using System.Net;
using System.Text.Json;
using UserService.DTOs.Response;
using UserService.Exceptions;

namespace UserService.Middleware;

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
            ResourceNotFoundException => (HttpStatusCode.NotFound, "RESOURCE_NOT_FOUND", exception.Message),
            DuplicateResourceException => (HttpStatusCode.Conflict, "DUPLICATE_RESOURCE", exception.Message),
            BadRequestException => (HttpStatusCode.BadRequest, "BAD_REQUEST", exception.Message),
            InvalidStatusTransitionException => (HttpStatusCode.BadRequest, "BAD_REQUEST", exception.Message),
            ForbiddenException => (HttpStatusCode.Forbidden, "FORBIDDEN", exception.Message),
            UnauthorizedAccessException => (HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Full authentication is required to access this resource"),
            ArgumentException => (HttpStatusCode.BadRequest, "BAD_REQUEST", exception.Message),
            _ => (HttpStatusCode.InternalServerError, "INTERNAL_SERVER_ERROR", "An unexpected internal server error occurred")
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
