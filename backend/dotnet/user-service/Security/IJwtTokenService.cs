using UserService.Entities;

namespace UserService.Security;

public interface IJwtTokenService
{
    string GenerateToken(User user);
}
