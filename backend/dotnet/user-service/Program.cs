using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using UserService.Configuration;
using UserService.Data;
using UserService.Middleware;
using UserService.Security;
using UserService.Services;

var builder = WebApplication.CreateBuilder(args);

// Determine port from environment or fallback to 8084
var port = Environment.GetEnvironmentVariable("PORT");
var listenPort = int.TryParse(port, out var parsedPort) ? parsedPort : 8084;

builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenLocalhost(listenPort);
});

// Add Configuration Options
builder.Services.Configure<JwtSettings>(builder.Configuration.GetSection(JwtSettings.SectionName));

// Configure Database Connection (MySQL 8 with EF Core)
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
        // Fallback server version for initialization/offline testing
        options.UseMySql(connectionString, new MySqlServerVersion(new Version(8, 0, 36)));
    }
});

// Register Application Services
builder.Services.AddSingleton<IPasswordHasher, PasswordHasher>();
builder.Services.AddScoped<IJwtTokenService, JwtTokenService>();
builder.Services.AddScoped<IUserService, UserService.Services.UserService>();

// Configure Controllers with String Enums and CamelCase
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
        options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
        options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    });

// Configure JWT Bearer Authentication
var jwtSecret = Environment.GetEnvironmentVariable("JWT_SECRET")
    ?? Environment.GetEnvironmentVariable("LEXOR_JWT_SECRET")
    ?? builder.Configuration.GetSection($"{JwtSettings.SectionName}:Secret").Value;

if (string.IsNullOrWhiteSpace(jwtSecret))
{
    throw new InvalidOperationException("JWT Secret is not configured. Set the JWT_SECRET or LEXOR_JWT_SECRET environment variable.");
}

builder.Services.PostConfigure<JwtSettings>(settings =>
{
    if (string.IsNullOrWhiteSpace(settings.Secret))
    {
        settings.Secret = jwtSecret;
    }
});

var jwtIssuer = builder.Configuration.GetSection($"{JwtSettings.SectionName}:Issuer").Value ?? "LEXOR";
var jwtAudience = builder.Configuration.GetSection($"{JwtSettings.SectionName}:Audience").Value ?? "LEXOR";

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
        RoleClaimType = "role"
    };
});

builder.Services.AddAuthorization();

// Configure Swagger/OpenAPI with Bearer Security Scheme
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "LEXOR User Service (.NET API)",
        Version = "v1",
        Description = "ASP.NET Core User Profile & Administrative Management Microservice for LEXOR Platform"
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

// Enable Global Exception Handler Middleware
app.UseMiddleware<GlobalExceptionMiddleware>();

// Enable Swagger UI
app.UseSwagger();
app.UseSwaggerUI(c =>
{
    c.SwaggerEndpoint("/swagger/v1/swagger.json", "LEXOR User Service v1");
    c.RoutePrefix = "swagger";
});

app.UseAuthentication();
app.UseMiddleware<UserStatusMiddleware>();
app.UseAuthorization();

app.MapControllers();

app.Run();

public partial class Program { }
