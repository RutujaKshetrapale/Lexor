using ApiGateway.Extensions;

var builder = WebApplication.CreateBuilder(args);

// Determine port from environment or fallback to proposed 8081
var portEnv = Environment.GetEnvironmentVariable("PORT");
var listenPort = int.TryParse(portEnv, out var parsedPort) ? parsedPort : 8081;

builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenLocalhost(listenPort);
});

// Register services
builder.Services.AddGatewayServices(builder.Configuration);

var app = builder.Build();

// Configure pipeline
app.UseGatewayPipeline();

app.Run();

// Required for tests
public partial class Program { }
