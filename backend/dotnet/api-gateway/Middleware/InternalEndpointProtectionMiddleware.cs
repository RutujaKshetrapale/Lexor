using System.Text.Json;
using ApiGateway.DTOs;

namespace ApiGateway.Middleware;

public class InternalEndpointProtectionMiddleware
{
    private readonly RequestDelegate _next;

    public InternalEndpointProtectionMiddleware(RequestDelegate next)
    {
        _next = next;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var path = context.Request.Path.Value;

        // 1. Block direct external calls to /api/v1/internal/**
        if (!string.IsNullOrEmpty(path) && path.StartsWith("/api/v1/internal", StringComparison.OrdinalIgnoreCase))
        {
            context.Response.StatusCode = StatusCodes.Status403Forbidden;
            context.Response.ContentType = "application/json";

            var correlationId = context.Request.Headers[CorrelationIdMiddleware.CorrelationIdHeader].FirstOrDefault();
            var errorResponse = new ErrorResponse
            {
                Timestamp = DateTime.UtcNow,
                Status = StatusCodes.Status403Forbidden,
                Error = "FORBIDDEN",
                Message = "Access to internal microservice endpoints is forbidden.",
                Path = path,
                CorrelationId = correlationId
            };

            await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse));
            return;
        }

        // 2. Strip any spoofed internal service key
        context.Request.Headers.Remove("X-Internal-Service-Key");

        await _next(context);
    }
}
