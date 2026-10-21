using System.Net;
using System.Text.Json;
using ApiGateway.DTOs;

namespace ApiGateway.Middleware;

public class GlobalExceptionMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<GlobalExceptionMiddleware> _logger;

    public GlobalExceptionMiddleware(RequestDelegate next, ILogger<GlobalExceptionMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await _next(context);
        }
        catch (Exception ex)
        {
            if (context.Response.HasStarted)
            {
                _logger.LogWarning(ex, "Response already started; unable to write custom error response.");
                throw;
            }

            await HandleExceptionAsync(context, ex);
        }
    }

    private async Task HandleExceptionAsync(HttpContext context, Exception ex)
    {
        int statusCode = StatusCodes.Status500InternalServerError;
        string error = "INTERNAL_SERVER_ERROR";
        string message = "An internal gateway error occurred.";

        if (ex is TimeoutException or TaskCanceledException)
        {
            statusCode = StatusCodes.Status504GatewayTimeout;
            error = "GATEWAY_TIMEOUT";
            message = "Upstream microservice did not respond within the configured timeout window.";
            _logger.LogWarning("Upstream timeout on {Path}: {Message}", context.Request.Path, ex.Message);
        }
        else if (ex is HttpRequestException)
        {
            statusCode = StatusCodes.Status503ServiceUnavailable;
            error = "SERVICE_UNAVAILABLE";
            message = "Target microservice is currently unreachable or starting up.";
            _logger.LogWarning("Connection failure forwarding to {Path}: {Message}", context.Request.Path, ex.Message);
        }
        else
        {
            _logger.LogError(ex, "Unhandled gateway exception on {Path}: {Message}", context.Request.Path, ex.Message);
        }

        context.Response.StatusCode = statusCode;
        context.Response.ContentType = "application/json";

        var correlationId = context.Request.Headers[CorrelationIdMiddleware.CorrelationIdHeader].FirstOrDefault();
        var errorResponse = new ErrorResponse
        {
            Timestamp = DateTime.UtcNow,
            Status = statusCode,
            Error = error,
            Message = message,
            Path = context.Request.Path.Value,
            CorrelationId = correlationId
        };

        await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse));
    }
}
