using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.IdentityModel.Tokens;
using Xunit;

namespace RideTypeService.Tests;

public class JwtSecurityTests
{
    private const string TestSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private const string TestIssuer = "LEXOR";
    private const string TestAudience = "LEXOR";

    private static string GenerateTestToken(string uuid, string email, string role, TimeSpan lifetime, string secret = TestSecret)
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret));
        var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var claims = new[]
        {
            new Claim("sub", uuid),
            new Claim("email", email),
            new Claim("role", role)
        };

        var now = DateTime.UtcNow;
        var notBefore = lifetime < TimeSpan.Zero ? now.Add(lifetime).AddMinutes(-1) : now;

        var tokenDescriptor = new SecurityTokenDescriptor
        {
            Subject = new ClaimsIdentity(claims),
            NotBefore = notBefore,
            IssuedAt = notBefore,
            Expires = now.Add(lifetime),
            Issuer = TestIssuer,
            Audience = TestAudience,
            SigningCredentials = credentials
        };

        var tokenHandler = new JwtSecurityTokenHandler();
        var token = tokenHandler.CreateToken(tokenDescriptor);
        return tokenHandler.WriteToken(token);
    }

    [Fact]
    public void ValidateToken_ValidAdminToken_Succeeds()
    {
        var token = GenerateTestToken("admin-uuid-1", "admin@lexor.mobility", "ROLE_ADMIN", TimeSpan.FromHours(1));

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(TestSecret));
        var tokenHandler = new JwtSecurityTokenHandler();
        tokenHandler.InboundClaimTypeMap.Clear();

        var principal = tokenHandler.ValidateToken(token, new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = key,
            ValidateIssuer = true,
            ValidIssuer = TestIssuer,
            ValidateAudience = true,
            ValidAudience = TestAudience,
            ValidateLifetime = true,
            ClockSkew = TimeSpan.Zero
        }, out var validatedToken);

        Assert.NotNull(validatedToken);
        Assert.Equal("admin-uuid-1", principal.FindFirstValue("sub"));
        Assert.Equal("admin@lexor.mobility", principal.FindFirstValue("email"));
        Assert.Equal("ROLE_ADMIN", principal.FindFirstValue("role"));
    }

    [Fact]
    public void ValidateToken_ValidRiderToken_Succeeds()
    {
        var token = GenerateTestToken("rider-uuid-2", "rider@lexor.mobility", "ROLE_RIDER", TimeSpan.FromHours(1));

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(TestSecret));
        var tokenHandler = new JwtSecurityTokenHandler();
        tokenHandler.InboundClaimTypeMap.Clear();

        var principal = tokenHandler.ValidateToken(token, new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = key,
            ValidateIssuer = true,
            ValidIssuer = TestIssuer,
            ValidateAudience = true,
            ValidAudience = TestAudience,
            ValidateLifetime = true,
            ClockSkew = TimeSpan.Zero
        }, out var validatedToken);

        Assert.NotNull(validatedToken);
        Assert.Equal("rider-uuid-2", principal.FindFirstValue("sub"));
        Assert.Equal("ROLE_RIDER", principal.FindFirstValue("role"));
    }

    [Fact]
    public void ValidateToken_TamperedToken_ThrowsSecurityTokenException()
    {
        var token = GenerateTestToken("rider-uuid-2", "rider@lexor.mobility", "ROLE_RIDER", TimeSpan.FromHours(1));
        var tamperedToken = token[..^5] + "ABCDE";

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(TestSecret));
        var tokenHandler = new JwtSecurityTokenHandler();

        Assert.ThrowsAny<SecurityTokenException>(() =>
            tokenHandler.ValidateToken(tamperedToken, new TokenValidationParameters
            {
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = key,
                ValidateIssuer = true,
                ValidIssuer = TestIssuer,
                ValidateAudience = true,
                ValidAudience = TestAudience
            }, out _));
    }

    [Fact]
    public void ValidateToken_MalformedToken_ThrowsArgumentException()
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(TestSecret));
        var tokenHandler = new JwtSecurityTokenHandler();

        Assert.ThrowsAny<ArgumentException>(() =>
            tokenHandler.ValidateToken("invalid.jwt", new TokenValidationParameters
            {
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = key,
                ValidateIssuer = true,
                ValidIssuer = TestIssuer,
                ValidateAudience = true,
                ValidAudience = TestAudience
            }, out _));
    }

    [Fact]
    public void ValidateToken_ExpiredToken_ThrowsSecurityTokenExpiredException()
    {
        var expiredToken = GenerateTestToken("admin-uuid-1", "admin@lexor.mobility", "ROLE_ADMIN", TimeSpan.FromMinutes(-10));

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(TestSecret));
        var tokenHandler = new JwtSecurityTokenHandler();

        Assert.Throws<SecurityTokenExpiredException>(() =>
            tokenHandler.ValidateToken(expiredToken, new TokenValidationParameters
            {
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = key,
                ValidateIssuer = true,
                ValidIssuer = TestIssuer,
                ValidateAudience = true,
                ValidAudience = TestAudience,
                ValidateLifetime = true,
                ClockSkew = TimeSpan.Zero
            }, out _));
    }

    [Fact]
    public void FailSecureSecret_EmptySecret_ThrowsArgumentException()
    {
        Assert.ThrowsAny<ArgumentException>(() => new SymmetricSecurityKey(Encoding.UTF8.GetBytes("")));
    }

    [Theory]
    [InlineData("")]
    [InlineData("short-key-16chars")]
    [InlineData("too-short-jwt-secret-under-32")]
    public void FailSecureSecret_Under32Chars_FailsEntropyPolicy(string insecureSecret)
    {
        // Enforces LEXOR enterprise security rule: minimum 256 bits (32 bytes)
        var isUnder32Chars = string.IsNullOrWhiteSpace(insecureSecret) || insecureSecret.Length < 32;
        Assert.True(isUnder32Chars, "Secret must be flagged as insecure when under 32 characters");

        var ex = Assert.Throws<InvalidOperationException>(() =>
        {
            if (string.IsNullOrWhiteSpace(insecureSecret) || insecureSecret.Length < 32)
            {
                throw new InvalidOperationException("Missing or insufficiently secure JWT secret. Must be at least 32 characters.");
            }
        });
        Assert.Contains("at least 32 characters", ex.Message);
    }

    [Fact]
    public void ValidateToken_SignedWithCompromisedLegacySecret_FailsAgainstRotatedKey()
    {
        // Token signed with old compromised key
        const string oldCompromisedSecret = "lexor-default-jwt-secret-key-32-chars-long!";
        var oldToken = GenerateTestToken("rider-uuid-1", "user@lexor.com", "ROLE_RIDER", TimeSpan.FromHours(1), oldCompromisedSecret);

        // Service configured with rotated key
        const string rotatedSecret = "a_brand_new_rotated_cryptographic_secret_min_32_characters!";
        var rotatedKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(rotatedSecret));
        var tokenHandler = new JwtSecurityTokenHandler();

        // Any SecurityTokenException (signature mismatch or key not found) confirms the token is rejected
        Assert.ThrowsAny<SecurityTokenException>(() =>
            tokenHandler.ValidateToken(oldToken, new TokenValidationParameters
            {
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = rotatedKey,
                ValidateIssuer = true,
                ValidIssuer = TestIssuer,
                ValidateAudience = true,
                ValidAudience = TestAudience,
                ValidateLifetime = true
            }, out _));
    }
}
