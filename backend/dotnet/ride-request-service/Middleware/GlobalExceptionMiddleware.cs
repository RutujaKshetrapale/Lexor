using System.Net;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.EntityFrameworkCore;
using RideRequestService.DTOs.Response;
using RideRequestService.Exceptions;

namespace RideRequestService.Middleware;

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
            _logger.LogError(ex, "Unhandled exception occurred during request to {Path}: {Message}", context.Request.Path, ex.Message);
            await HandleExceptionAsync(context, ex);
        }
    }

    private static async Task HandleExceptionAsync(HttpContext context, Exception exception)
    {
        context.Response.ContentType = "application/json";

        var (statusCode, error, message) = exception switch
        {
            ResourceNotFoundException => (HttpStatusCode.NotFound, "RESOURCE_NOT_FOUND", exception.Message),
            ConflictException => (HttpStatusCode.Conflict, "CONFLICT", exception.Message),
            BadRequestException => (HttpStatusCode.BadRequest, "BAD_REQUEST", exception.Message),
            ForbiddenException => (HttpStatusCode.Forbidden, "FORBIDDEN", exception.Message),
            ServiceUnavailableException => (HttpStatusCode.ServiceUnavailable, "SERVICE_UNAVAILABLE", exception.Message),
            DbUpdateException => (HttpStatusCode.Conflict, "DATA_INTEGRITY_VIOLATION", "Operation failed due to database integrity constraints."),
            UnauthorizedAccessException => (HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Full authentication is required to access this resource"),
            ArgumentException => (HttpStatusCode.BadRequest, "BAD_REQUEST", exception.Message),
            _ => (HttpStatusCode.InternalServerError, "INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please contact system support.")
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
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
        };

        await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse, jsonOptions));
    }
}
