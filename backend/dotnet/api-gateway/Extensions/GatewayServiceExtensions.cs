using System.Net;
using System.Text.Json;
using System.Threading.RateLimiting;
using ApiGateway.Configuration;
using ApiGateway.DTOs;
using ApiGateway.Middleware;
using Microsoft.AspNetCore.RateLimiting;

namespace ApiGateway.Extensions;

public static class GatewayServiceExtensions
{
    public static IServiceCollection AddGatewayServices(this IServiceCollection services, IConfiguration configuration)
    {
        services.Configure<JwtOptions>(configuration.GetSection(JwtOptions.SectionName));
        services.Configure<CorsOptions>(configuration.GetSection(CorsOptions.SectionName));
        services.Configure<RateLimitingOptions>(configuration.GetSection(RateLimitingOptions.SectionName));

        var corsOrigins = (configuration.GetSection($"{CorsOptions.SectionName}:AllowedOrigins").Value ?? "http://localhost:3000,http://localhost:5173")
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

        services.AddCors(options =>
        {
            options.AddPolicy("GatewayCorsPolicy", policy =>
            {
                policy.WithOrigins(corsOrigins)
                      .WithMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                      .WithHeaders("Authorization", "Content-Type", "X-Correlation-Id", "X-Requested-With", "Accept")
                      .AllowCredentials();
            });
        });

        services.AddRateLimiter(options =>
        {
            options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
            options.OnRejected = async (context, token) =>
            {
                context.HttpContext.Response.StatusCode = StatusCodes.Status429TooManyRequests;
                context.HttpContext.Response.ContentType = "application/json";
                context.HttpContext.Response.Headers.Append("Retry-After", "1");

                var correlationId = context.HttpContext.Request.Headers[CorrelationIdMiddleware.CorrelationIdHeader].FirstOrDefault();
                var errorResponse = new ErrorResponse
                {
                    Timestamp = DateTime.UtcNow,
                    Status = StatusCodes.Status429TooManyRequests,
                    Error = "TOO_MANY_REQUESTS",
                    Message = "Rate limit exceeded. Please wait before retrying your request.",
                    Path = context.HttpContext.Request.Path.Value,
                    CorrelationId = correlationId
                };

                await context.HttpContext.Response.WriteAsync(JsonSerializer.Serialize(errorResponse), cancellationToken: token);
            };

            var permitLimit = int.TryParse(configuration.GetSection($"{RateLimitingOptions.SectionName}:PermitLimit").Value, out var pl) ? pl : 50;
            var windowSeconds = int.TryParse(configuration.GetSection($"{RateLimitingOptions.SectionName}:WindowSeconds").Value, out var ws) ? ws : 1;

            options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(httpContext =>
            {
                var path = httpContext.Request.Path.Value;
                if (!string.IsNullOrEmpty(path) && (path.Equals("/health", StringComparison.OrdinalIgnoreCase) || HttpMethods.IsOptions(httpContext.Request.Method)))
                {
                    return RateLimitPartition.GetNoLimiter("unlimited");
                }

                var key = httpContext.Request.Headers["X-User-Uuid"].FirstOrDefault()
                          ?? httpContext.Connection.RemoteIpAddress?.ToString()
                          ?? "anonymous";

                return RateLimitPartition.GetFixedWindowLimiter(
                    partitionKey: key,
                    factory: _ => new FixedWindowRateLimiterOptions
                    {
                        PermitLimit = permitLimit,
                        QueueLimit = 0,
                        Window = TimeSpan.FromSeconds(windowSeconds)
                    });
            });
        });

        services.AddReverseProxy()
            .LoadFromConfig(configuration.GetSection("ReverseProxy"));

        return services;
    }

    public static WebApplication UseGatewayPipeline(this WebApplication app)
    {
        app.UseMiddleware<GlobalExceptionMiddleware>();
        app.UseMiddleware<CorrelationIdMiddleware>();
        app.UseMiddleware<InternalEndpointProtectionMiddleware>();
        app.UseMiddleware<RedactedLoggingMiddleware>();

        app.UseCors("GatewayCorsPolicy");
        app.UseMiddleware<JwtAuthenticationMiddleware>();
        app.UseRateLimiter();

        app.MapGet("/health", () => Results.Ok(new
        {
            status = "UP",
            service = "LEXOR .NET YARP API Gateway",
            timestamp = DateTime.UtcNow,
            version = "1.0.0"
        }));

        app.MapReverseProxy();

        return app;
    }
}
