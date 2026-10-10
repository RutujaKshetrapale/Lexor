using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using UserService.Data;
using UserService.DTOs.Response;
using UserService.Entities;

namespace UserService.Middleware;

public class UserStatusMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<UserStatusMiddleware> _logger;

    public UserStatusMiddleware(RequestDelegate next, ILogger<UserStatusMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context, LexorDbContext dbContext)
    {
        if (context.User.Identity?.IsAuthenticated == true)
        {
            var uuid = context.User.FindFirstValue(ClaimTypes.NameIdentifier)
                       ?? context.User.FindFirstValue(JwtRegisteredClaimNames.Sub)
                       ?? context.User.FindFirstValue("sub")
                       ?? context.User.FindFirstValue("uuid");

            if (!string.IsNullOrEmpty(uuid))
            {
                var user = await dbContext.Users
                    .AsNoTracking()
                    .FirstOrDefaultAsync(u => u.Uuid == uuid);

                if (user == null || user.Status == UserStatus.DEACTIVATED || user.Status == UserStatus.SUSPENDED)
                {
                    _logger.LogWarning("Rejecting request for user [{Uuid}] due to status [{Status}]", uuid, user?.Status);

                    context.Response.StatusCode = StatusCodes.Status401Unauthorized;
                    context.Response.ContentType = "application/json";

                    var errorResponse = new ErrorResponse
                    {
                        Timestamp = DateTime.UtcNow,
                        Status = StatusCodes.Status401Unauthorized,
                        Error = "UNAUTHORIZED",
                        Message = "User account is inactive, suspended, or deactivated",
                        Path = context.Request.Path
                    };

                    var jsonOptions = new JsonSerializerOptions
                    {
                        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
                        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
                    };

                    await context.Response.WriteAsync(JsonSerializer.Serialize(errorResponse, jsonOptions));
                    return;
                }
            }
        }

        await _next(context);
    }
}
