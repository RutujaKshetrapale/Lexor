using System.IdentityModel.Tokens.Jwt;
using System.Net;
using System.Net.Http.Headers;
using System.Security.Claims;
using System.Text;
using System.Text.Json;
using ApiGateway.DTOs;
using ApiGateway.Extensions;
using Microsoft.AspNetCore.TestHost;
using Microsoft.IdentityModel.Tokens;
using Xunit;

namespace ApiGateway.Tests;

public class TestServerFixture : IAsyncLifetime
{
    public HttpClient Client { get; private set; } = null!;
    private WebApplication _app = null!;

    public async Task InitializeAsync()
    {
        var builder = WebApplication.CreateBuilder(new WebApplicationOptions
        {
            ContentRootPath = AppContext.BaseDirectory,
            EnvironmentName = "Testing"
        });

        builder.Configuration.AddJsonFile("appsettings.json", optional: true);
        builder.Configuration.AddInMemoryCollection(new Dictionary<string, string?>
        {
            ["Jwt:Secret"] = ApiGatewayIntegrationTests.TestSecret,
            ["RateLimiting:PermitLimit"] = "3",
            ["RateLimiting:WindowSeconds"] = "60"
        });

        builder.WebHost.UseTestServer();
        builder.Services.AddGatewayServices(builder.Configuration);

        _app = builder.Build();
        _app.UseGatewayPipeline();
        await _app.StartAsync();

        Client = _app.GetTestClient();
    }

    public async Task DisposeAsync()
    {
        Client.Dispose();
        await _app.DisposeAsync();
    }
}

public class ApiGatewayIntegrationTests : IClassFixture<TestServerFixture>
{
    private readonly HttpClient _client;
    public const string TestSecret = "lexor-default-jwt-secret-key-32-chars-long!";

    public ApiGatewayIntegrationTests(TestServerFixture fixture)
    {
        _client = fixture.Client;
    }

    private static string GenerateTestJwt(string userUuid, string role, TimeSpan lifetime, string secret = TestSecret)
    {
        var tokenHandler = new JwtSecurityTokenHandler();
        var key = Encoding.UTF8.GetBytes(secret);

        var now = DateTime.UtcNow;
        var notBefore = lifetime < TimeSpan.Zero ? now.AddMinutes(-30) : now;
        var expires = now.Add(lifetime);
        var issuedAt = notBefore;

        var tokenDescriptor = new SecurityTokenDescriptor
        {
            Subject = new ClaimsIdentity(new[]
            {
                new Claim(JwtRegisteredClaimNames.Sub, userUuid),
                new Claim("role", role),
                new Claim("email", "testuser@lexor.com"),
                new Claim("userId", "101")
            }),
            Issuer = "LEXOR",
            Audience = "LEXOR",
            NotBefore = notBefore,
            IssuedAt = issuedAt,
            Expires = expires,
            SigningCredentials = new SigningCredentials(new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature)
        };

        var token = tokenHandler.CreateToken(tokenDescriptor);
        return tokenHandler.WriteToken(token);
    }

    [Fact]
    public async Task HealthCheck_ReturnsUp()
    {
        var response = await _client.GetAsync("/health");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var content = await response.Content.ReadAsStringAsync();
        Assert.Contains("\"status\":\"UP\"", content);
        Assert.Contains("LEXOR .NET YARP API Gateway", content);
    }

    [Fact]
    public async Task CorrelationId_GeneratedWhenMissing()
    {
        var response = await _client.GetAsync("/health");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.True(response.Headers.Contains("X-Correlation-Id"));
        var corrId = response.Headers.GetValues("X-Correlation-Id").FirstOrDefault();
        Assert.False(string.IsNullOrWhiteSpace(corrId));
    }

    [Fact]
    public async Task CorrelationId_PreservedWhenSupplied()
    {
        var testCorrId = "corr-test-" + Guid.NewGuid();
        using var request = new HttpRequestMessage(HttpMethod.Get, "/health");
        request.Headers.Add("X-Correlation-Id", testCorrId);

        var response = await _client.SendAsync(request);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.True(response.Headers.Contains("X-Correlation-Id"));
        Assert.Equal(testCorrId, response.Headers.GetValues("X-Correlation-Id").First());
    }

    [Fact]
    public async Task ProtectedEndpoint_WithoutToken_Returns401Unauthorized()
    {
        var response = await _client.GetAsync("/api/v1/bookings/me");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        var error = await JsonSerializer.DeserializeAsync<ErrorResponse>(await response.Content.ReadAsStreamAsync());
        Assert.NotNull(error);
        Assert.Equal(401, error.Status);
        Assert.Equal("UNAUTHORIZED", error.Error);
        Assert.Contains("Full authentication is required", error.Message);
    }

    [Fact]
    public async Task ProtectedEndpoint_WithExpiredToken_Returns401Unauthorized()
    {
        var expiredToken = GenerateTestJwt("rider-uuid-1", "ROLE_RIDER", TimeSpan.FromMinutes(-10));
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/v1/bookings/me");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", expiredToken);

        var response = await _client.SendAsync(request);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        var error = await JsonSerializer.DeserializeAsync<ErrorResponse>(await response.Content.ReadAsStreamAsync());
        Assert.NotNull(error);
        Assert.Equal(401, error.Status);
        Assert.Contains("expired", error.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task ProtectedEndpoint_WithInvalidSignature_Returns401Unauthorized()
    {
        var invalidToken = GenerateTestJwt("rider-uuid-1", "ROLE_RIDER", TimeSpan.FromHours(1), "different-wrong-secret-key-32-chars!");
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/v1/bookings/me");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", invalidToken);

        var response = await _client.SendAsync(request);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        var error = await JsonSerializer.DeserializeAsync<ErrorResponse>(await response.Content.ReadAsStreamAsync());
        Assert.NotNull(error);
        Assert.Equal(401, error.Status);
        Assert.Equal("UNAUTHORIZED", error.Error);
    }

    [Fact]
    public async Task RoleAuthorization_RiderAccessingAdminEndpoint_Returns403Forbidden()
    {
        var riderToken = GenerateTestJwt("rider-uuid-1", "ROLE_RIDER", TimeSpan.FromHours(1));
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/v1/admin/drivers");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", riderToken);

        var response = await _client.SendAsync(request);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        var error = await JsonSerializer.DeserializeAsync<ErrorResponse>(await response.Content.ReadAsStreamAsync());
        Assert.NotNull(error);
        Assert.Equal(403, error.Status);
        Assert.Equal("FORBIDDEN", error.Error);
    }

    [Fact]
    public async Task RoleAuthorization_RiderAccessingDriverEndpoint_Returns403Forbidden()
    {
        var riderToken = GenerateTestJwt("rider-uuid-1", "ROLE_RIDER", TimeSpan.FromHours(1));
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/v1/drivers/me");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", riderToken);

        var response = await _client.SendAsync(request);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        var error = await JsonSerializer.DeserializeAsync<ErrorResponse>(await response.Content.ReadAsStreamAsync());
        Assert.NotNull(error);
        Assert.Equal(403, error.Status);
        Assert.Equal("FORBIDDEN", error.Error);
    }

    [Fact]
    public async Task RoleAuthorization_DriverAccessingRiderBookingEndpoint_Returns403Forbidden()
    {
        var driverToken = GenerateTestJwt("driver-uuid-1", "ROLE_DRIVER", TimeSpan.FromHours(1));
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/v1/bookings/me");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", driverToken);

        var response = await _client.SendAsync(request);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        var error = await JsonSerializer.DeserializeAsync<ErrorResponse>(await response.Content.ReadAsStreamAsync());
        Assert.NotNull(error);
        Assert.Equal(403, error.Status);
        Assert.Equal("FORBIDDEN", error.Error);
    }

    [Fact]
    public async Task InternalEndpoint_DirectAccessBlocked_Returns403Forbidden()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/api/v1/internal/matching/ride-requests/req-123/attempts");
        request.Headers.Add("X-Internal-Service-Key", "malicious-internal-key-attempt");

        var response = await _client.SendAsync(request);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        var error = await JsonSerializer.DeserializeAsync<ErrorResponse>(await response.Content.ReadAsStreamAsync());
        Assert.NotNull(error);
        Assert.Equal(403, error.Status);
        Assert.Contains("internal microservice endpoints", error.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task CorsPreflight_ReturnsAllowHeaders()
    {
        using var request = new HttpRequestMessage(HttpMethod.Options, "/api/v1/bookings/me");
        request.Headers.Add("Origin", "http://localhost:3000");
        request.Headers.Add("Access-Control-Request-Method", "GET");
        request.Headers.Add("Access-Control-Request-Headers", "Authorization");

        var response = await _client.SendAsync(request);

        Assert.True(response.StatusCode is HttpStatusCode.OK or HttpStatusCode.NoContent);
        Assert.True(response.Headers.Contains("Access-Control-Allow-Origin"));
        Assert.Equal("http://localhost:3000", response.Headers.GetValues("Access-Control-Allow-Origin").First());
        Assert.True(response.Headers.Contains("Access-Control-Allow-Credentials"));
        Assert.Equal("true", response.Headers.GetValues("Access-Control-Allow-Credentials").First());
    }

    [Fact]
    public async Task RateLimiting_BurstExceeded_Returns429TooManyRequests()
    {
        var token = GenerateTestJwt("rate-limit-user-net", "ROLE_RIDER", TimeSpan.FromHours(1));
        var got429 = false;

        for (int i = 0; i < 10; i++)
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, "/api/v1/ride-types");
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

            var response = await _client.SendAsync(request);
            if (response.StatusCode == HttpStatusCode.TooManyRequests)
            {
                got429 = true;
                break;
            }
        }

        Assert.True(got429);
    }
}
