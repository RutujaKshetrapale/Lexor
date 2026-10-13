using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using VehicleService.Configuration;
using VehicleService.Data;
using VehicleService.Middleware;
using VehicleService.Services;
using VehicleService.Storage;

var builder = WebApplication.CreateBuilder(args);

// Determine port from environment or default to 8088
var port = Environment.GetEnvironmentVariable("PORT");
var listenPort = int.TryParse(port, out var parsedPort) ? parsedPort : 8088;

builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenLocalhost(listenPort);
});

// Add Configuration Options
builder.Services.Configure<JwtSettings>(builder.Configuration.GetSection(JwtSettings.SectionName));
builder.Services.Configure<StorageSettings>(builder.Configuration.GetSection(StorageSettings.SectionName));
builder.Services.Configure<VehicleVerificationPolicySettings>(builder.Configuration.GetSection(VehicleVerificationPolicySettings.SectionName));

// Configure Database Connection (MySQL 8 with EF Core Pomelo)
var connectionString = builder.Configuration.GetConnectionString("DefaultConnection")
    ?? "Server=localhost;Port=3306;Database=lexor_db;Uid=root;Pwd=21011017;AllowPublicKeyRetrieval=True;SSLMode=None;";

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
builder.Services.AddSingleton<IDocumentStorageService, FileSystemDocumentStorageService>();
builder.Services.AddScoped<IVehicleVerificationPolicyService, VehicleVerificationPolicyService>();
builder.Services.AddScoped<IVehicleService, VehicleService.Services.VehicleService>();
builder.Services.AddScoped<IVehicleDocumentService, VehicleDocumentService>();
builder.Services.AddScoped<IVehicleAdminService, VehicleAdminService>();

// Configure Controllers with String Enums and CamelCase
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
        options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
        options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    });

// Configure JWT Bearer Authentication
var jwtSecret = Environment.GetEnvironmentVariable("LEXOR_JWT_SECRET")
    ?? builder.Configuration.GetSection($"{JwtSettings.SectionName}:Secret").Value;

if (string.IsNullOrWhiteSpace(jwtSecret))
{
    // For test and initial local startup safety, require secret or use standard local test key
    jwtSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
}

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

                    if (val.StartsWith("ROLE_"))
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
        Title = "LEXOR Vehicle Service (.NET API)",
        Version = "v1",
        Description = "ASP.NET Core Vehicle Fleet, Document Management & Administrative Verification Microservice for LEXOR Platform"
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
    c.SwaggerEndpoint("/swagger/v1/swagger.json", "LEXOR Vehicle Service v1");
    c.RoutePrefix = "swagger";
});

app.UseAuthentication();
app.UseMiddleware<UserStatusMiddleware>();
app.UseAuthorization();

app.MapControllers();

app.Run();

public partial class Program { }
