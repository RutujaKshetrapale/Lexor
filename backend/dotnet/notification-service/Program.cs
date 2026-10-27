using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using NotificationService.Configuration;
using NotificationService.Data;
using NotificationService.Domain.Entities;
using NotificationService.Domain.Enums;
using NotificationService.Middleware;
using NotificationService.Providers;
using NotificationService.Services;

var builder = WebApplication.CreateBuilder(args);

// Configuration options
builder.Services.Configure<NotificationSettings>(builder.Configuration.GetSection("NotificationSettings"));
builder.Services.Configure<JwtSettings>(builder.Configuration.GetSection("JwtSettings"));

// Controllers & JSON Serializer
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
    });

// Database Setup
var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");
if (!builder.Environment.IsEnvironment("Testing") && !string.IsNullOrEmpty(connectionString))
{
    builder.Services.AddDbContext<NotificationDbContext>(options =>
        options.UseMySql(connectionString, ServerVersion.AutoDetect(connectionString)));
}
else
{
    builder.Services.AddDbContext<NotificationDbContext>(options =>
        options.UseInMemoryDatabase("NotificationTestDb"));
}

// JWT Authentication
var jwtSecret = builder.Configuration["JwtSettings:Secret"] ?? builder.Configuration["JWT_SECRET"];
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
var key = Encoding.UTF8.GetBytes(jwtSecret);

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.RequireHttpsMetadata = false;
        options.SaveToken = true;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(key),
            ValidateIssuer = false,
            ValidateAudience = false,
            ClockSkew = TimeSpan.Zero
        };
    });

builder.Services.AddAuthorization();

// Register Providers
builder.Services.AddSingleton<IPushNotificationProvider, FcmPushProvider>();
builder.Services.AddSingleton<Msg91SmsProvider>();
builder.Services.AddSingleton<TwilioSmsProvider>();
builder.Services.AddSingleton<ISmsNotificationProvider, SmsProviderRouter>();
builder.Services.AddSingleton<IEmailNotificationProvider, SmtpEmailProvider>();
builder.Services.AddSingleton<IRealtimeNotifier, RealtimeNotifier>();

// Startup Provider Validation in Production Mode
var notifSettings = builder.Configuration.GetSection("NotificationSettings").Get<NotificationSettings>() ?? new NotificationSettings();
var envName = builder.Environment.EnvironmentName;
if (string.Equals(envName, "Production", StringComparison.OrdinalIgnoreCase))
{
    if (notifSettings.Sms.Enabled && !notifSettings.Sms.Provider.Contains("SANDBOX", StringComparison.OrdinalIgnoreCase))
    {
        if (notifSettings.Sms.Provider.Contains("MSG91", StringComparison.OrdinalIgnoreCase))
        {
            if (string.IsNullOrWhiteSpace(notifSettings.Sms.Msg91?.AuthKey))
            {
                throw new InvalidOperationException("Production Startup Validation Failed: MSG91_AUTH_KEY is required when MSG91 SMS provider is enabled in Production.");
            }
        }
        else if (notifSettings.Sms.Provider.Contains("TWILIO", StringComparison.OrdinalIgnoreCase))
        {
            if (string.IsNullOrWhiteSpace(notifSettings.Sms.AccountSid) || string.IsNullOrWhiteSpace(notifSettings.Sms.AuthToken))
            {
                throw new InvalidOperationException("Production Startup Validation Failed: TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN are required in Production.");
            }
        }
    }
}


// Register Application Services
builder.Services.AddScoped<ITemplateService, TemplateService>();
builder.Services.AddScoped<IPreferenceService, PreferenceService>();
builder.Services.AddScoped<IDeviceService, DeviceService>();
builder.Services.AddScoped<IOtpDeliveryService, OtpDeliveryService>();
builder.Services.AddScoped<INotificationService, NotificationService.Services.NotificationService>();
builder.Services.AddScoped<IEventIngestionService, EventIngestionService>();

// Swagger Documentation
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "LEXOR Notification Service API", Version = "v1" });
    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Description = "JWT Authorization header using the Bearer scheme. Example: \"Bearer {token}\"",
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
                Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" }
            },
            Array.Empty<string>()
        }
    });
});

var app = builder.Build();

// Seed initial templates if empty in dev/in-memory
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<NotificationDbContext>();
    if (db.Database.IsInMemory() && !db.NotificationTemplates.Any())
    {
        db.NotificationTemplates.AddRange(
            new NotificationTemplate { TemplateCode = "DRIVER_ASSIGNED", Channel = NotificationChannel.PUSH, Locale = "en", TitleTemplate = "Driver Assigned!", BodyTemplate = "Your driver {driverName} is on the way." },
            new NotificationTemplate { TemplateCode = "DRIVER_ASSIGNED", Channel = NotificationChannel.IN_APP, Locale = "en", TitleTemplate = "Driver Assigned", BodyTemplate = "Your driver {driverName} has accepted your ride." },
            new NotificationTemplate { TemplateCode = "DRIVER_ASSIGNED", Channel = NotificationChannel.PUSH, Locale = "hi", TitleTemplate = "चालक नियुक्त!", BodyTemplate = "आपके चालक {driverName} रास्ते में हैं।" },
            new NotificationTemplate { TemplateCode = "RIDE_COMPLETED", Channel = NotificationChannel.IN_APP, Locale = "en", TitleTemplate = "Trip Completed", BodyTemplate = "Your ride has concluded. Fare: INR {fare}." }
        );
        db.SaveChanges();
    }
}

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
