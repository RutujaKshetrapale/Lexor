using System.Net;
using System.Text.Json;
using DriverMatchingService.DTOs.Response;
using DriverMatchingService.Exceptions;

namespace DriverMatchingService.Middleware;

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
        var statusCode = HttpStatusCode.InternalServerError;
        var error = "INTERNAL_SERVER_ERROR";
        var message = "An unexpected error occurred. Please try again later.";
        IDictionary<string, string>? details = null;

        switch (exception)
        {
            case ResourceNotFoundException:
                statusCode = HttpStatusCode.NotFound;
                error = "NOT_FOUND";
                message = exception.Message;
                break;

            case BadRequestException:
                statusCode = HttpStatusCode.BadRequest;
                error = "BAD_REQUEST";
                message = exception.Message;
                break;

            case ConflictException:
                statusCode = HttpStatusCode.Conflict;
                error = "CONFLICT";
                message = exception.Message;
                break;

            case ForbiddenException:
                statusCode = HttpStatusCode.Forbidden;
                error = "FORBIDDEN";
                message = exception.Message;
                break;

            case UnauthorizedException:
                statusCode = HttpStatusCode.Unauthorized;
                error = "UNAUTHORIZED";
                message = exception.Message;
                break;

            default:
                _logger.LogError(exception, "Unhandled exception occurred at path: {Path}", context.Request.Path);
                break;
        }

        var response = new ErrorResponse
        {
            Timestamp = DateTime.UtcNow,
            Status = (int)statusCode,
            Error = error,
            Message = message,
            Path = context.Request.Path,
            Details = details
        };

        context.Response.ContentType = "application/json";
        context.Response.StatusCode = (int)statusCode;

        var jsonOptions = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
        };

        await context.Response.WriteAsync(JsonSerializer.Serialize(response, jsonOptions));
    }
}
