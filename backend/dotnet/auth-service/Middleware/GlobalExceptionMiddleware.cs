using System.Net;
using System.Text.Json;
using AuthService.DTOs;
using AuthService.Exceptions;

namespace AuthService.Middleware;

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
            _logger.LogError(ex, "An unhandled exception occurred during request processing");
            await HandleExceptionAsync(context, ex);
        }
    }

    private static async Task HandleExceptionAsync(HttpContext context, Exception exception)
    {
        context.Response.ContentType = "application/json";

        var (statusCode, error, message) = exception switch
        {
            InvalidCredentialsException => (HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", exception.Message),
            DuplicateResourceException => (HttpStatusCode.Conflict, "DUPLICATE_RESOURCE", exception.Message),
            ResourceNotFoundException => (HttpStatusCode.NotFound, "RESOURCE_NOT_FOUND", exception.Message),
            ForbiddenException => (HttpStatusCode.Forbidden, "FORBIDDEN", exception.Message),
            UnauthorizedAccessException => (HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Full authentication is required to access this resource"),
            _ => (HttpStatusCode.InternalServerError, "INTERNAL_SERVER_ERROR", "An unexpected error occurred: " + exception.Message)
        };

        context.Response.StatusCode = (int)statusCode;

        var errorResponse = new ErrorResponse
        {
            Timestamp = DateTime.UtcNow,
            Status = (int)statusCode,
            Error = error,
            Message = message,
            Path = context.Request.Path
        };

        var jsonOptions = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
        };

        await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse, jsonOptions));
    }
}
