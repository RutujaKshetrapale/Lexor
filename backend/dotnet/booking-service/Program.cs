using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using BookingService.Clients;
using BookingService.Configuration;
using BookingService.Data;
using BookingService.DTOs.Response;
using BookingService.Events;
using BookingService.Middleware;
using BookingService.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);

// Determine port from environment or default to 8096
var port = Environment.GetEnvironmentVariable("PORT");
var listenPort = int.TryParse(port, out var parsedPort) ? parsedPort : 8096;

builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenLocalhost(listenPort);
});

// Add Configuration Options
builder.Services.Configure<JwtSettings>(builder.Configuration.GetSection(JwtSettings.SectionName));
builder.Services.Configure<ServiceUrls>(builder.Configuration.GetSection(ServiceUrls.SectionName));
builder.Services.Configure<BookingSettings>(builder.Configuration.GetSection(BookingSettings.SectionName));

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

// Configure Downstream HTTP Clients
var downstreamTimeout = builder.Configuration.GetValue<int>("Booking:TimeoutSeconds", 3);
builder.Services.AddHttpClient<IRideRequestClient, RideRequestClient>(client =>
{
    client.Timeout = TimeSpan.FromSeconds(downstreamTimeout > 0 ? downstreamTimeout : 3);
});

builder.Services.AddHttpClient<IDriverMatchingClient, DriverMatchingClient>(client =>
{
    client.Timeout = TimeSpan.FromSeconds(downstreamTimeout > 0 ? downstreamTimeout : 3);
});

// Register Event Publisher & Application Services
builder.Services.AddScoped<IEventPublisher, LoggingEventPublisher>();
builder.Services.AddScoped<IBookingService, BookingService.Services.BookingService>();

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
        ValidateIssuer = !string.IsNullOrEmpty(jwtIssuer),
        ValidIssuer = jwtIssuer,
        ValidateAudience = !string.IsNullOrEmpty(jwtAudience),
        ValidAudience = jwtAudience,
        ValidateLifetime = true,
        ClockSkew = TimeSpan.FromMinutes(1),
        RoleClaimType = "role",
        NameClaimType = "sub"
    };

    options.Events = new JwtBearerEvents
    {
        OnTokenValidated = context =>
        {
            if (context.Principal?.Identity is System.Security.Claims.ClaimsIdentity identity)
            {
                var roleClaims = identity.FindAll(c => c.Type == "role" || c.Type == "roles" || c.Type == System.Security.Claims.ClaimTypes.Role).ToList();
                foreach (var rc in roleClaims)
                {
                    var val = rc.Value;
                    if (!identity.HasClaim(identity.RoleClaimType, val))
                    {
                        identity.AddClaim(new System.Security.Claims.Claim(identity.RoleClaimType, val));
                    }
                    if (!identity.HasClaim(System.Security.Claims.ClaimTypes.Role, val))
                    {
                        identity.AddClaim(new System.Security.Claims.Claim(System.Security.Claims.ClaimTypes.Role, val));
                    }

                    if (val.StartsWith("ROLE_", StringComparison.OrdinalIgnoreCase))
                    {
                        var shortVal = val[5..];
                        if (!identity.HasClaim(identity.RoleClaimType, shortVal)) identity.AddClaim(new System.Security.Claims.Claim(identity.RoleClaimType, shortVal));
                        if (!identity.HasClaim(System.Security.Claims.ClaimTypes.Role, shortVal)) identity.AddClaim(new System.Security.Claims.Claim(System.Security.Claims.ClaimTypes.Role, shortVal));
                    }
                    else
                    {
                        var longVal = "ROLE_" + val;
                        if (!identity.HasClaim(identity.RoleClaimType, longVal)) identity.AddClaim(new System.Security.Claims.Claim(identity.RoleClaimType, longVal));
                        if (!identity.HasClaim(System.Security.Claims.ClaimTypes.Role, longVal)) identity.AddClaim(new System.Security.Claims.Claim(System.Security.Claims.ClaimTypes.Role, longVal));
                    }
                }
            }
            return Task.CompletedTask;
        },
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

            var jsonOptions = new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
                DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
            };

            await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse, jsonOptions));
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
                Message = "You do not have permission to access this resource",
                Path = context.Request.Path
            };

            var jsonOptions = new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
                DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
            };

            await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse, jsonOptions));
        }
    };
});

builder.Services.AddAuthorization();

// Configure Swagger/OpenAPI with Bearer Security Scheme
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "LEXOR Booking Service (.NET API)",
        Version = "v1",
        Description = "ASP.NET Core Booking Lifecycle, Trip Dispatch, and Journey State Machine Microservice for LEXOR Smart Mobility Platform"
    });

    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Description = "JWT Authorization header using the Bearer scheme. Input: 'Bearer <your_token>'",
        Name = "Authorization",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.ApiKey,
        Scheme = "Bearer"
    });

    c.AddSecurityRequirement(new OpenApiSecurityRequirement
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

// Enable Global Exception Middleware
app.UseMiddleware<GlobalExceptionMiddleware>();

// Enable Swagger UI
app.UseSwagger();
app.UseSwaggerUI(c =>
{
    c.SwaggerEndpoint("/swagger/v1/swagger.json", "LEXOR Booking Service API v1");
    c.RoutePrefix = "swagger";
});

app.UseRouting();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();

// Required for WebApplicationFactory in integration tests
public partial class Program { }
