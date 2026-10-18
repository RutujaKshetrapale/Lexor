using System.Security.Claims;

namespace BookingService.Extensions;

public static class ClaimsPrincipalExtensions
{
    public static string? GetUserUuid(this ClaimsPrincipal principal) =>
        principal.FindFirstValue("uuid") ??
        principal.FindFirstValue(ClaimTypes.NameIdentifier) ??
        principal.FindFirstValue("sub");
}
