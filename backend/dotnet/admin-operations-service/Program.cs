using System.Text;
using AdminOperationsService.Clients;
using AdminOperationsService.Data;
using AdminOperationsService.Middleware;
using AdminOperationsService.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

// Configure port
var port = Environment.GetEnvironmentVariable("PORT") ?? "8104";
builder.WebHost.UseUrls($"http://*:{port}");

// Add Database Context
var connectionString = builder.Configuration.GetConnectionString("DefaultConnection") 
    ?? "Server=localhost;Port=3306;Database=lexor_db;User=root;Password=root;CharSet=utf8mb4;";

builder.Services.AddDbContext<AdminOperationsDbContext>(options =>
{
    if (builder.Environment.IsEnvironment("Testing"))
    {
        options.UseInMemoryDatabase("AdminOperationsTestDb");
    }
    else
    {
        options.UseMySql(connectionString, ServerVersion.AutoDetect(connectionString));
    }
});

// Add HttpClients & Domain Clients
builder.Services.AddHttpClient();
builder.Services.AddScoped<BookingAdminClient>();
builder.Services.AddScoped<PaymentAdminClient>();
builder.Services.AddScoped<DriverAdminClient>();
builder.Services.AddScoped<VehicleAdminClient>();
builder.Services.AddScoped<UserAdminClient>();
builder.Services.AddScoped<RideRequestAdminClient>();
builder.Services.AddScoped<FeedbackAdminClient>();
builder.Services.AddScoped<ServiceHealthProbeClient>();

// Add Services
builder.Services.AddScoped<IOperationsDashboardService, OperationsDashboardService>();
builder.Services.AddScoped<IPlatformAnalyticsService, PlatformAnalyticsService>();
builder.Services.AddScoped<IServiceHealthMonitorService, ServiceHealthMonitorService>();
builder.Services.AddScoped<IAdminAuditService, AdminAuditService>();

// Add JWT Authentication
var jwtSecret = builder.Configuration["Jwt:Secret"] ?? "lexor-default-jwt-secret-key-32-chars-long!";
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
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

app.UseMiddleware<GlobalExceptionMiddleware>();
app.UseMiddleware<InternalServiceMiddleware>();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseAuthentication();
app.UseAuthorization();

app.MapGet("/health", () => Results.Ok(new { status = "UP", service = "admin-operations-service" }));
app.MapControllers();

app.Run();

public partial class Program { }
