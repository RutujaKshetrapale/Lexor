using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using DriverMatchingService.Configuration;
using DriverMatchingService.Data;
using DriverMatchingService.DTOs.Response;
using DriverMatchingService.Events;
using DriverMatchingService.Middleware;
using DriverMatchingService.Services;

var builder = WebApplication.CreateBuilder(args);

// Determine port from environment or fallback to 8094
var port = Environment.GetEnvironmentVariable("PORT");
var listenPort = int.TryParse(port, out var parsedPort) ? parsedPort : 8094;

builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenLocalhost(listenPort);
});

// Add Configuration Options
builder.Services.Configure<JwtSettings>(builder.Configuration.GetSection(JwtSettings.SectionName));
builder.Services.Configure<MatchingSettings>(builder.Configuration.GetSection(MatchingSettings.SectionName));
builder.Services.Configure<ServiceUrls>(builder.Configuration.GetSection(ServiceUrls.SectionName));

// Configure Database Connection (MySQL 8 with EF Core Pomelo)
var connectionString = Environment.GetEnvironmentVariable("DB_CONNECTION_STRING")
    ?? builder.Configuration.GetConnectionString("DefaultConnection")
    ?? "Server=localhost;Port=3306;Database=lexor_db;Uid=root;Pwd=;AllowPublicKeyRetrieval=True;SSLMode=None;";

builder.Services.AddDbContext<LexorDbContext>(options =>
{
    try
    {
        var serverVersion = ServerVersion.AutoDetect(connectionString);
        options.UseMySql(connectionString, serverVersion);
    }
    catch
    {
        // Fallback server version for offline initialization / testing
        options.UseMySql(connectionString, new MySqlServerVersion(new Version(8, 0, 36)));
    }
});

// Register Application Services
builder.Services.AddScoped<IScoringPolicyService, ScoringPolicyService>();
builder.Services.AddScoped<IDriverLocationService, DriverLocationService>();
builder.Services.AddScoped<IDriverMatchingService, DriverMatchingService.Services.DriverMatchingService>();
builder.Services.AddSingleton<IEventPublisher, LoggingEventPublisher>();

// Configure Controllers with String Enums and CamelCase
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
        options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
        options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    })
    .ConfigureApiBehaviorOptions(options =>
    {
        options.InvalidModelStateResponseFactory = context =>
        {
            var details = new Dictionary<string, string>();
            foreach (var (key, modelStateEntry) in context.ModelState)
            {
                var error = modelStateEntry.Errors.FirstOrDefault();
                if (error != null)
                {
                    var fieldName = string.IsNullOrEmpty(key) ? "error" :
                        char.ToLowerInvariant(key[0]) + key[1..];
                    details[fieldName] = error.ErrorMessage;
                }
            }

            var errorResponse = new ErrorResponse
            {
                Timestamp = DateTime.UtcNow,
                Status = StatusCodes.Status400BadRequest,
                Error = "VALIDATION_ERROR",
                Message = "Input validation failed",
                Path = context.HttpContext.Request.Path,
                Details = details
            };

            return new BadRequestObjectResult(errorResponse);
        };
    });

// Configure JWT Bearer Authentication
var jwtSecret = Environment.GetEnvironmentVariable("JWT_SECRET")
    ?? Environment.GetEnvironmentVariable("LEXOR_JWT_SECRET")
    ?? builder.Configuration.GetSection($"{JwtSettings.SectionName}:Secret").Value;

if (string.IsNullOrWhiteSpace(jwtSecret) || jwtSecret.Length < 32)
{
    if (builder.Environment.IsEnvironment("Testing"))
    {
        jwtSecret = "lexor-testing-jwt-secret-key-must-be-at-least-32-chars!";
    }
    else
    {
        throw new InvalidOperationException("Missing or insufficiently secure JWT secret. 'JwtSettings:Secret' or 'JWT_SECRET' must be at least 32 characters.");
    }
}

builder.Services.PostConfigure<JwtSettings>(settings =>
{
    if (string.IsNullOrWhiteSpace(settings.Secret))
    {
        settings.Secret = jwtSecret;
    }
});

var jwtIssuer = Environment.GetEnvironmentVariable("JWT_ISSUER")
    ?? builder.Configuration.GetSection($"{JwtSettings.SectionName}:Issuer").Value
    ?? "LEXOR";

var jwtAudience = Environment.GetEnvironmentVariable("JWT_AUDIENCE")
    ?? builder.Configuration.GetSection($"{JwtSettings.SectionName}:Audience").Value
    ?? "LEXOR";

var key = Encoding.UTF8.GetBytes(jwtSecret);

builder.Services.AddAuthentication(options =>
{
    options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
    options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
})
.AddJwtBearer(options =>
{
    options.RequireHttpsMetadata = false;
    options.SaveToken = true;
    options.MapInboundClaims = false;
    options.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuerSigningKey = true,
        IssuerSigningKey = new SymmetricSecurityKey(key),
        ValidateIssuer = true,
        ValidIssuer = jwtIssuer,
        ValidateAudience = true,
        ValidAudience = jwtAudience,
        ValidateLifetime = true,
        ClockSkew = TimeSpan.FromMinutes(1),
        RoleClaimType = "role",
        NameClaimType = "sub"
    };

    options.Events = new JwtBearerEvents
    {
        OnChallenge = async context =>
        {
            context.HandleResponse();
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            context.Response.ContentType = "application/json";

            var errorResponse = new ErrorResponse
            {
                Timestamp = DateTime.UtcNow,
                Status = StatusCodes.Status401Unauthorized,
                Error = "UNAUTHORIZED",
                Message = "Full authentication is required to access this resource",
                Path = context.Request.Path
            };

            await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse));
        },
        OnForbidden = async context =>
        {
            context.Response.StatusCode = StatusCodes.Status403Forbidden;
            context.Response.ContentType = "application/json";

            var errorResponse = new ErrorResponse
            {
                Timestamp = DateTime.UtcNow,
                Status = StatusCodes.Status403Forbidden,
                Error = "FORBIDDEN",
                Message = "Access denied: You do not have permission to access this resource",
                Path = context.Request.Path
            };

            await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse));
        }
    };
});

// Configure Swagger/OpenAPI
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "LEXOR Driver Matching Service API (.NET)",
        Version = "v1",
        Description = "ASP.NET Core microservice responsible for real-time driver proximity matching, scoring, atomic reservation, and location telemetry.",
        Contact = new OpenApiContact
        {
            Name = "LEXOR Engineering Team",
            Email = "dev@lexor.mobility"
        }
    });

    options.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Name = "Authorization",
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        In = ParameterLocation.Header,
        Description = "Enter your JWT Bearer token: Bearer {token}"
    });

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference
                {
                    Type = ReferenceType.SecurityScheme,
                    Id = "Bearer"
                }
            },
            Array.Empty<string>()
        }
    });
});

var app = builder.Build();

app.UseMiddleware<GlobalExceptionMiddleware>();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI(c =>
    {
        c.SwaggerEndpoint("/swagger/v1/swagger.json", "Driver Matching API v1");
        c.RoutePrefix = "swagger";
    });
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();

public partial class Program { }
