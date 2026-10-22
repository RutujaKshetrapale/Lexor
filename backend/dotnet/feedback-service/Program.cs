using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using FeedbackService.Clients;
using FeedbackService.Configuration;
using FeedbackService.Data;
using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;
using FeedbackService.Middleware;
using FeedbackService.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);

// Configuration options
builder.Services.Configure<FeedbackSettings>(builder.Configuration.GetSection("FeedbackSettings"));
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
    builder.Services.AddDbContext<FeedbackDbContext>(options =>
        options.UseMySql(connectionString, ServerVersion.AutoDetect(connectionString)));
}
else
{
    builder.Services.AddDbContext<FeedbackDbContext>(options =>
        options.UseInMemoryDatabase("FeedbackTestDb"));
}

// JWT Authentication
var jwtSecret = builder.Configuration["JwtSettings:Secret"] ?? "lexor-default-jwt-secret-key-32-chars-long!";
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
            ClockSkew = TimeSpan.Zero,
            RoleClaimType = "role"
        };
    });

builder.Services.AddAuthorization();

// Register HttpClients
builder.Services.AddHttpClient<IBookingVerificationClient, BookingVerificationClient>();
builder.Services.AddHttpClient<INotificationClient, NotificationClient>();

// Register Application Services
builder.Services.AddScoped<IRatingCalculationService, RatingCalculationService>();
builder.Services.AddScoped<IModerationService, ModerationService>();
builder.Services.AddScoped<IFeedbackService, FeedbackService.Services.FeedbackService>();

// Swagger Documentation
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "LEXOR Feedback & Ratings Service API", Version = "v1" });
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

// Seed initial feedback tags if empty in memory/dev
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<FeedbackDbContext>();
    if (db.Database.IsInMemory() && !db.FeedbackTags.Any())
    {
        db.FeedbackTags.AddRange(
            new FeedbackTag { TagCode = "FRIENDLY_DRIVER", Label = "Friendly driver", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.POSITIVE, DisplayOrder = 1 },
            new FeedbackTag { TagCode = "PROFESSIONAL", Label = "Professional", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.POSITIVE, DisplayOrder = 2 },
            new FeedbackTag { TagCode = "SAFE_DRIVING", Label = "Safe driving", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.POSITIVE, DisplayOrder = 3 },
            new FeedbackTag { TagCode = "CLEAN_VEHICLE", Label = "Clean vehicle", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.POSITIVE, DisplayOrder = 4 },
            new FeedbackTag { TagCode = "SMOOTH_RIDE", Label = "Smooth ride", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.POSITIVE, DisplayOrder = 5 },
            new FeedbackTag { TagCode = "GOOD_COMMUNICATION", Label = "Good communication", TargetRole = TagTargetRole.BOTH, Category = TagCategory.POSITIVE, DisplayOrder = 6 },
            new FeedbackTag { TagCode = "PUNCTUAL", Label = "Punctual", TargetRole = TagTargetRole.BOTH, Category = TagCategory.POSITIVE, DisplayOrder = 7 },
            new FeedbackTag { TagCode = "ROUTE_CONCERN", Label = "Route concern", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.CONCERN, DisplayOrder = 8 },
            new FeedbackTag { TagCode = "DRIVING_CONCERN", Label = "Driving concern", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.CONCERN, DisplayOrder = 9 },
            new FeedbackTag { TagCode = "VEHICLE_CLEANLINESS_CONCERN", Label = "Vehicle cleanliness concern", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.CONCERN, DisplayOrder = 10 },
            new FeedbackTag { TagCode = "UNPROFESSIONAL_BEHAVIOR", Label = "Unprofessional behavior", TargetRole = TagTargetRole.BOTH, Category = TagCategory.CONCERN, DisplayOrder = 11 },
            new FeedbackTag { TagCode = "OTHER", Label = "Other", TargetRole = TagTargetRole.BOTH, Category = TagCategory.GENERAL, DisplayOrder = 12 },
            new FeedbackTag { TagCode = "RESPECTFUL", Label = "Respectful", TargetRole = TagTargetRole.RIDER, Category = TagCategory.POSITIVE, DisplayOrder = 13 },
            new FeedbackTag { TagCode = "PUNCTUAL_PICKUP", Label = "Punctual at pickup", TargetRole = TagTargetRole.RIDER, Category = TagCategory.POSITIVE, DisplayOrder = 14 },
            new FeedbackTag { TagCode = "CLEAR_PICKUP_INSTRUCTIONS", Label = "Clear pickup instructions", TargetRole = TagTargetRole.RIDER, Category = TagCategory.POSITIVE, DisplayOrder = 15 },
            new FeedbackTag { TagCode = "COMMUNICATION_ISSUE", Label = "Communication issue", TargetRole = TagTargetRole.RIDER, Category = TagCategory.CONCERN, DisplayOrder = 16 },
            new FeedbackTag { TagCode = "PICKUP_DELAY", Label = "Pickup delay", TargetRole = TagTargetRole.RIDER, Category = TagCategory.CONCERN, DisplayOrder = 17 },
            new FeedbackTag { TagCode = "INAPPROPRIATE_BEHAVIOR", Label = "Inappropriate behavior", TargetRole = TagTargetRole.RIDER, Category = TagCategory.CONCERN, DisplayOrder = 18 },
            new FeedbackTag { TagCode = "SAFETY_CONCERN", Label = "Safety concern", TargetRole = TagTargetRole.BOTH, Category = TagCategory.CONCERN, DisplayOrder = 19 }
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
