using AuthService.Entities;

namespace AuthService.Security;

public interface IJwtTokenService
{
    string GenerateToken(User user);
    long GetExpirationSeconds();
}
