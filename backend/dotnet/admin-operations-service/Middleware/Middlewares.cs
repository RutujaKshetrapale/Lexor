using System.Net;
using System.Security.Claims;
using System.Text.Json;
using AdminOperationsService.DTOs;

namespace AdminOperationsService.Middleware;

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
        catch (ArgumentException ex)
        {
            _logger.LogWarning("Bad request on {Path}: {Message}", context.Request.Path, ex.Message);
            await HandleExceptionAsync(context, HttpStatusCode.BadRequest, "Bad Request", ex.Message);
        }
        catch (KeyNotFoundException ex)
        {
            _logger.LogWarning("Resource not found on {Path}: {Message}", context.Request.Path, ex.Message);
            await HandleExceptionAsync(context, HttpStatusCode.NotFound, "Not Found", ex.Message);
        }
        catch (UnauthorizedAccessException ex)
        {
            _logger.LogWarning("Forbidden access on {Path}: {Message}", context.Request.Path, ex.Message);
            await HandleExceptionAsync(context, HttpStatusCode.Forbidden, "Forbidden", ex.Message);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unhandled operational exception on {Path}", context.Request.Path);
            await HandleExceptionAsync(context, HttpStatusCode.InternalServerError, "Internal Server Error", "An unexpected operational error occurred");
        }
    }

    private static async Task HandleExceptionAsync(HttpContext context, HttpStatusCode statusCode, string error, string message)
    {
        context.Response.ContentType = "application/json";
        context.Response.StatusCode = (int)statusCode;

        var response = new ErrorResponse
        {
            Status = (int)statusCode,
            Error = error,
            Message = message,
            Path = context.Request.Path,
            Timestamp = DateTime.UtcNow
        };

        var json = JsonSerializer.Serialize(response, new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
        await context.Response.WriteAsync(json);
    }
}

public class InternalServiceMiddleware
{
    private readonly RequestDelegate _next;
    private readonly string? _expectedApiKey;

    public InternalServiceMiddleware(RequestDelegate next, IConfiguration config)
    {
        _next = next;
        _expectedApiKey = config["InternalService:ApiKey"] ?? config["INTERNAL_SERVICE_API_KEY"];
    }

    public async Task InvokeAsync(HttpContext context)
    {
        if (!string.IsNullOrWhiteSpace(_expectedApiKey) &&
            context.Request.Headers.TryGetValue("X-Internal-Service-Key", out var headerKey) &&
            !string.IsNullOrWhiteSpace(headerKey) &&
            headerKey == _expectedApiKey)
        {
            var identity = new ClaimsIdentity(new[]
            {
                new Claim(ClaimTypes.NameIdentifier, "internal-service"),
                new Claim(ClaimTypes.Role, "ROLE_ADMIN"),
                new Claim(ClaimTypes.Role, "ADMIN")
            }, "InternalService");

            context.User = new ClaimsPrincipal(identity);
        }

        await _next(context);
    }
}
