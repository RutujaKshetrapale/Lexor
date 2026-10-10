using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.Extensions.Options;
using UserService.Configuration;
using UserService.Entities;
using UserService.Security;
using Xunit;

namespace UserService.Tests;

public class JwtTokenServiceTests
{
    private readonly JwtTokenService _jwtTokenService;

    public JwtTokenServiceTests()
    {
        var settings = Options.Create(new JwtSettings
        {
            Secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
            Issuer = "LEXOR",
            Audience = "LEXOR",
            ExpirationMinutes = 60
        });

        _jwtTokenService = new JwtTokenService(settings);
    }

    [Fact]
    public void GenerateToken_ShouldCreateValidJwtTokenWithExpectedClaims()
    {
        var user = new User
        {
            Id = 42,
            Uuid = "test-user-uuid-999",
            FirstName = "Jane",
            LastName = "Doe",
            Email = "jane.doe@example.com",
            PhoneNumber = "+919876543211",
            Role = UserRole.RIDER,
            Status = UserStatus.ACTIVE
        };

        var tokenString = _jwtTokenService.GenerateToken(user);

        Assert.NotNull(tokenString);

        var handler = new JwtSecurityTokenHandler();
        var token = handler.ReadJwtToken(tokenString);

        Assert.Equal("test-user-uuid-999", token.Subject);
        Assert.Equal("jane.doe@example.com", token.Claims.FirstOrDefault(c => c.Type == "email")?.Value);
        Assert.Equal("ROLE_RIDER", token.Claims.FirstOrDefault(c => c.Type == "role")?.Value);
        Assert.Equal("42", token.Claims.FirstOrDefault(c => c.Type == "userId")?.Value);
    }
}
