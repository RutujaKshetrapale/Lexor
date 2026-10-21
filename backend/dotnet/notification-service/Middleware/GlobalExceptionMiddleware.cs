using System.Net;
using System.Text.Json;
using NotificationService.DTOs;
using NotificationService.Exceptions;

namespace NotificationService.Middleware;

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
            await HandleExceptionAsync(context, ex);
        }
    }

    private async Task HandleExceptionAsync(HttpContext context, Exception exception)
    {
        var correlationId = context.Request.Headers["X-Correlation-Id"].FirstOrDefault()
            ?? context.TraceIdentifier;

        context.Response.ContentType = "application/json";

        var (status, errorCode, message) = exception switch
        {
            ResourceNotFoundException ex => (HttpStatusCode.NotFound, "RESOURCE_NOT_FOUND", ex.Message),
            ForbiddenException ex => (HttpStatusCode.Forbidden, "FORBIDDEN", ex.Message),
            BadRequestException ex => (HttpStatusCode.BadRequest, "BAD_REQUEST", ex.Message),
            DuplicateEventException ex => (HttpStatusCode.Conflict, "DUPLICATE_EVENT", ex.Message),
            RateLimitExceededException ex => (HttpStatusCode.TooManyRequests, "RATE_LIMIT_EXCEEDED", ex.Message),
            _ => (HttpStatusCode.InternalServerError, "INTERNAL_SERVER_ERROR", "An unexpected error occurred")
        };

        if (status == HttpStatusCode.InternalServerError)
        {
            _logger.LogError(exception, "Unhandled exception: {Message}", exception.Message);
        }
        else
        {
            _logger.LogWarning("Handled domain exception ({ErrorCode}): {Message}", errorCode, message);
        }

        context.Response.StatusCode = (int)status;

        var errorResponse = new ErrorResponse(
            DateTime.UtcNow,
            (int)status,
            status.ToString(),
            errorCode,
            message,
            context.Request.Path,
            correlationId
        );

        var json = JsonSerializer.Serialize(errorResponse, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });

        await context.Response.WriteAsync(json);
    }
}
