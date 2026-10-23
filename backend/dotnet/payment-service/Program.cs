using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using PaymentService.Clients;
using PaymentService.Configuration;
using PaymentService.Data;
using PaymentService.Middleware;
using PaymentService.Providers;
using PaymentService.Services;

var builder = WebApplication.CreateBuilder(args);

// Determine port from environment or default to 8098
var port = Environment.GetEnvironmentVariable("PORT");
var listenPort = int.TryParse(port, out var parsedPort) ? parsedPort : 8098;

builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenLocalhost(listenPort);
});

// Add Configuration Options
builder.Services.Configure<PaymentSettings>(builder.Configuration.GetSection(PaymentSettings.SectionName));
builder.Services.Configure<JwtSettings>(builder.Configuration.GetSection(JwtSettings.SectionName));
builder.Services.Configure<ServiceUrls>(builder.Configuration.GetSection(ServiceUrls.SectionName));

// Configure Database Connection (MySQL 8 with Pomelo)
var connectionString = Environment.GetEnvironmentVariable("DB_CONNECTION_STRING")
    ?? builder.Configuration.GetConnectionString("DefaultConnection")
    ?? "Server=localhost;Port=3306;Database=lexor_db;Uid=root;Pwd=;AllowPublicKeyRetrieval=True;SSLMode=None;";

builder.Services.AddDbContext<PaymentDbContext>(options =>
{
    try
    {
        var serverVersion = ServerVersion.AutoDetect(connectionString);
        options.UseMySql(connectionString, serverVersion);
    }
    catch
    {
        // Fallback for offline testing / startup initialization
        options.UseMySql(connectionString, new MySqlServerVersion(new Version(8, 0, 36)));
    }
});

// Configure Downstream HTTP Clients
var downstreamTimeout = builder.Configuration.GetValue<int>("Services:TimeoutSeconds", 5);
builder.Services.AddHttpClient<IBookingClient, BookingClient>(client =>
{
    client.Timeout = TimeSpan.FromSeconds(downstreamTimeout > 0 ? downstreamTimeout : 5);
});

// Register Domain Services and Payment Provider
builder.Services.AddScoped<IPaymentProvider, RazorpayPaymentProvider>();
builder.Services.AddScoped<ILedgerService, LedgerService>();
builder.Services.AddScoped<IIdempotencyService, IdempotencyService>();
builder.Services.AddScoped<IPaymentService, PaymentService.Services.PaymentService>();

// Configure Controllers
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
        options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
        options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    });

// Configure JWT Authentication
var jwtSecret = Environment.GetEnvironmentVariable("JWT_SECRET")
    ?? Environment.GetEnvironmentVariable("LEXOR_JWT_SECRET")
    ?? builder.Configuration.GetValue<string>("Jwt:Secret");

if (string.IsNullOrWhiteSpace(jwtSecret) || jwtSecret.Length < 32)
{
    if (builder.Environment.IsEnvironment("Testing"))
    {
        jwtSecret = "lexor-testing-jwt-secret-key-must-be-at-least-32-chars!";
    }
    else
    {
        throw new InvalidOperationException("Missing or insufficiently secure JWT secret. 'Jwt:Secret' or 'JWT_SECRET' must be at least 32 characters.");
    }
}
var jwtIssuer = builder.Configuration.GetValue<string>("Jwt:Issuer") ?? "LEXOR";
var jwtAudience = builder.Configuration.GetValue<string>("Jwt:Audience") ?? "LEXOR";

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
        ValidateIssuer = true,
        ValidIssuer = jwtIssuer,
        ValidateAudience = true,
        ValidAudience = jwtAudience,
        ValidateIssuerSigningKey = true,
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtSecret)),
        ValidateLifetime = true,
        ClockSkew = TimeSpan.FromSeconds(30),
        RoleClaimType = "role"
    };
});

// Configure Swagger
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "LEXOR Payment Microservice (.NET)",
        Version = "v1",
        Description = "Production-grade ride-hailing payment service supporting UPI, Cards, Net Banking, and Cash reconciliation."
    });

    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Description = "JWT Authorization header using the Bearer scheme. Format: 'Bearer {token}'",
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

app.UseMiddleware<GlobalExceptionMiddleware>();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();

public partial class Program { }
