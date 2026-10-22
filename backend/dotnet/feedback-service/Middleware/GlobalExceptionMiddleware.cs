using System.Net;
using System.Text.Json;
using FeedbackService.Exceptions;
using Microsoft.EntityFrameworkCore;

namespace FeedbackService.Middleware;

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

    private Task HandleExceptionAsync(HttpContext context, Exception exception)
    {
        var statusCode = HttpStatusCode.InternalServerError;
        var error = "Internal Server Error";
        var message = exception.Message;

        switch (exception)
        {
            case ResourceNotFoundException:
                statusCode = HttpStatusCode.NotFound;
                error = "Not Found";
                break;
            case ConflictException:
                statusCode = HttpStatusCode.Conflict;
                error = "Conflict";
                break;
            case DbUpdateException:
                statusCode = HttpStatusCode.Conflict;
                error = "Conflict";
                message = "A duplicate feedback record or report was detected or a constraint was violated.";
                break;
            case ForbiddenException:
                statusCode = HttpStatusCode.Forbidden;
                error = "Forbidden";
                break;
            case IneligibleFeedbackException:
                statusCode = HttpStatusCode.BadRequest;
                error = "Ineligible Feedback";
                break;
            case BadRequestException:
                statusCode = HttpStatusCode.BadRequest;
                error = "Bad Request";
                break;
            default:
                _logger.LogError(exception, "Unhandled exception occurred: {Message}", exception.Message);
                message = "An unexpected error occurred. Please try again later.";
                break;
        }

        context.Response.ContentType = "application/json";
        context.Response.StatusCode = (int)statusCode;

        var response = new
        {
            status = (int)statusCode,
            error,
            message,
            path = context.Request.Path.Value,
            timestamp = DateTime.UtcNow
        };

        var json = JsonSerializer.Serialize(response, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });

        return context.Response.WriteAsync(json);
    }
}
