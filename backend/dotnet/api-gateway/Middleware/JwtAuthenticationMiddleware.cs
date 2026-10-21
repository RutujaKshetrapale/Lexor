using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using System.Text.Json;
using ApiGateway.Configuration;
using ApiGateway.DTOs;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

namespace ApiGateway.Middleware;

public class JwtAuthenticationMiddleware
{
    private readonly RequestDelegate _next;
    private readonly JwtOptions _jwtOptions;
    private readonly ILogger<JwtAuthenticationMiddleware> _logger;
    private readonly JwtSecurityTokenHandler _tokenHandler = new();

    public JwtAuthenticationMiddleware(
        RequestDelegate next,
        IOptions<JwtOptions> jwtOptions,
        ILogger<JwtAuthenticationMiddleware> logger)
    {
        _next = next;
        _jwtOptions = jwtOptions.Value;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var path = context.Request.Path.Value;
        var method = context.Request.Method;

        // 1. Always strip any incoming client-supplied spoofed identity headers
        context.Request.Headers.Remove("X-User-Uuid");
        context.Request.Headers.Remove("X-User-Role");
        context.Request.Headers.Remove("X-User-Email");
        context.Request.Headers.Remove("X-User-Id");

        // 2. Allow preflight OPTIONS requests without authentication
        if (HttpMethods.IsOptions(method))
        {
            await _next(context);
            return;
        }

        // 3. Bypass public endpoints
        if (IsPublicEndpoint(path, method))
        {
            await _next(context);
            return;
        }

        // 4. Extract Authorization Header
        if (!context.Request.Headers.TryGetValue("Authorization", out var authHeaders) ||
            string.IsNullOrWhiteSpace(authHeaders.FirstOrDefault()) ||
            !authHeaders.First()!.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
        {
            await SendErrorResponseAsync(context, StatusCodes.Status401Unauthorized, "UNAUTHORIZED",
                "Full authentication is required to access this resource");
            return;
        }

        var token = authHeaders.First()!["Bearer ".Length..].Trim();
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_jwtOptions.Secret));

        var validationParameters = new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = key,
            ValidateIssuer = !string.IsNullOrWhiteSpace(_jwtOptions.Issuer),
            ValidIssuer = _jwtOptions.Issuer,
            ValidateAudience = !string.IsNullOrWhiteSpace(_jwtOptions.Audience),
            ValidAudience = _jwtOptions.Audience,
            ValidateLifetime = true,
            ClockSkew = TimeSpan.Zero
        };

        ClaimsPrincipal principal;
        try
        {
            principal = _tokenHandler.ValidateToken(token, validationParameters, out _);
        }
        catch (SecurityTokenExpiredException ex)
        {
            _logger.LogWarning("Expired JWT token attempt on {Path}: {Message}", path, ex.Message);
            await SendErrorResponseAsync(context, StatusCodes.Status401Unauthorized, "UNAUTHORIZED", "JWT token has expired");
            return;
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Invalid JWT token attempt on {Path}: {Message}", path, ex.Message);
            await SendErrorResponseAsync(context, StatusCodes.Status401Unauthorized, "UNAUTHORIZED", "Invalid or malformed JWT token");
            return;
        }

        // 5. Extract claims
        var userUuid = principal.FindFirst(JwtRegisteredClaimNames.Sub)?.Value
                       ?? principal.FindFirst(ClaimTypes.NameIdentifier)?.Value
                       ?? principal.FindFirst("sub")?.Value;

        var roleClaim = principal.FindFirst("role")?.Value
                        ?? principal.FindFirst(ClaimTypes.Role)?.Value
                        ?? "ROLE_RIDER";

        var email = principal.FindFirst("email")?.Value
                    ?? principal.FindFirst(ClaimTypes.Email)?.Value;

        var userId = principal.FindFirst("userId")?.Value
                     ?? principal.FindFirst("user_id")?.Value;

        if (string.IsNullOrWhiteSpace(userUuid))
        {
            await SendErrorResponseAsync(context, StatusCodes.Status401Unauthorized, "UNAUTHORIZED", "Invalid token subject (missing UUID)");
            return;
        }

        var role = NormalizeRole(roleClaim);

        // 6. Role-Based Access Control (RBAC) Enforcement
        if (!IsAuthorizedForPath(path, role))
        {
            _logger.LogWarning("Access denied: User {UserUuid} with role {Role} attempted forbidden path {Path}", userUuid, role, path);
            await SendErrorResponseAsync(context, StatusCodes.Status403Forbidden, "FORBIDDEN",
                "You do not have permission to access this resource");
            return;
        }

        // 7. Inject verified identity headers downstream for target microservice
        context.Request.Headers["X-User-Uuid"] = userUuid;
        context.Request.Headers["X-User-Role"] = role;
        if (!string.IsNullOrWhiteSpace(email))
        {
            context.Request.Headers["X-User-Email"] = email;
        }
        if (!string.IsNullOrWhiteSpace(userId))
        {
            context.Request.Headers["X-User-Id"] = userId;
        }

        context.User = principal;

        await _next(context);
    }

    private static bool IsPublicEndpoint(string? path, string method)
    {
        if (string.IsNullOrWhiteSpace(path)) return false;

        if (path.Equals("/health", StringComparison.OrdinalIgnoreCase)) return true;
        if (path.StartsWith("/api/v1/auth/", StringComparison.OrdinalIgnoreCase)) return true;
        if (path.StartsWith("/api/v1/ride-types", StringComparison.OrdinalIgnoreCase) &&
            HttpMethods.IsGet(method) &&
            !path.StartsWith("/api/v1/admin/", StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return false;
    }

    private static string NormalizeRole(string roleClaim)
    {
        if (string.IsNullOrWhiteSpace(roleClaim)) return "ROLE_RIDER";
        var upper = roleClaim.Trim().ToUpperInvariant();
        return upper.StartsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    private static bool IsAuthorizedForPath(string? path, string role)
    {
        if (string.IsNullOrWhiteSpace(path)) return false;

        var isAdmin = "ROLE_ADMIN".Equals(role, StringComparison.OrdinalIgnoreCase);
        var isDriver = "ROLE_DRIVER".Equals(role, StringComparison.OrdinalIgnoreCase) || isAdmin;
        var isRider = "ROLE_RIDER".Equals(role, StringComparison.OrdinalIgnoreCase) || isAdmin;

        // Admin-only endpoints
        if (path.StartsWith("/api/v1/admin/", StringComparison.OrdinalIgnoreCase))
        {
            return isAdmin;
        }

        // Driver-specific endpoints
        if (path.StartsWith("/api/v1/drivers/me/bookings", StringComparison.OrdinalIgnoreCase))
        {
            return isDriver;
        }
        if (path.StartsWith("/api/v1/drivers/", StringComparison.OrdinalIgnoreCase))
        {
            return isDriver;
        }
        if (path.StartsWith("/api/v1/vehicles/", StringComparison.OrdinalIgnoreCase))
        {
            return isDriver;
        }

        // Rider endpoints
        if (path.StartsWith("/api/v1/riders/", StringComparison.OrdinalIgnoreCase))
        {
            return isRider;
        }
        if (path.StartsWith("/api/v1/ride-requests/", StringComparison.OrdinalIgnoreCase))
        {
            return isRider;
        }
        if (path.StartsWith("/api/v1/bookings/", StringComparison.OrdinalIgnoreCase))
        {
            return isRider;
        }

        return true;
    }

    private static async Task SendErrorResponseAsync(HttpContext context, int statusCode, string error, string message)
    {
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
