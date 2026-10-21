using System.Net;
using System.Text.Json;
using PaymentService.DTOs.Responses;
using PaymentService.Exceptions;

namespace PaymentService.Middleware;

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
        // Propagate X-Correlation-Id
        if (!context.Request.Headers.TryGetValue("X-Correlation-Id", out var correlationId) || string.IsNullOrWhiteSpace(correlationId))
        {
            correlationId = Guid.NewGuid().ToString();
            context.Request.Headers["X-Correlation-Id"] = correlationId;
        }
        context.Response.Headers["X-Correlation-Id"] = correlationId;

        try
        {
            await _next(context);
        }
        catch (Exception ex)
        {
            await HandleExceptionAsync(context, ex);
        }
    }

    private async Task HandleExceptionAsync(HttpContext context, Exception exception)
    {
        var statusCode = HttpStatusCode.InternalServerError;
        var error = "INTERNAL_SERVER_ERROR";
        var message = exception.Message;

        switch (exception)
        {
            case BadRequestException:
                statusCode = HttpStatusCode.BadRequest;
                error = "BAD_REQUEST";
                _logger.LogWarning("Bad request: {Message}", message);
                break;
            case ResourceNotFoundException:
                statusCode = HttpStatusCode.NotFound;
                error = "NOT_FOUND";
                _logger.LogWarning("Resource not found: {Message}", message);
                break;
            case UnauthorizedException:
                statusCode = HttpStatusCode.Unauthorized;
                error = "UNAUTHORIZED";
                _logger.LogWarning("Unauthorized: {Message}", message);
                break;
            case ForbiddenException:
                statusCode = HttpStatusCode.Forbidden;
                error = "FORBIDDEN";
                _logger.LogWarning("Forbidden: {Message}", message);
                break;
            case ConflictException:
                statusCode = HttpStatusCode.Conflict;
                error = "CONFLICT";
                _logger.LogWarning("Conflict: {Message}", message);
                break;
            case PaymentGatewayException:
                statusCode = HttpStatusCode.BadGateway;
                error = "GATEWAY_ERROR";
                _logger.LogError(exception, "Payment gateway exception: {Message}", message);
                break;
            default:
                _logger.LogError(exception, "Unhandled exception occurred: {Message}", message);
                message = "An unexpected error occurred. Please try again later.";
                break;
        }

        context.Response.ContentType = "application/json";
        context.Response.StatusCode = (int)statusCode;

        var response = new ErrorResponse
        {
            Timestamp = DateTime.UtcNow,
            Status = (int)statusCode,
            Error = error,
            Message = message,
            Path = context.Request.Path
        };

        var json = JsonSerializer.Serialize(response, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });

        await context.Response.WriteAsync(json);
    }
}
