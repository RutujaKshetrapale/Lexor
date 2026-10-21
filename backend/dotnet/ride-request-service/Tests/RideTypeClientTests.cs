using System.Net;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using RideRequestService.Configuration;
using RideRequestService.DTOs.Response;
using RideRequestService.Exceptions;
using RideRequestService.Services;
using Xunit;

namespace RideRequestService.Tests;

public class RideTypeClientTests
{
    private class MockHttpMessageHandler : HttpMessageHandler
    {
        private readonly Func<HttpRequestMessage, HttpResponseMessage> _handler;

        public MockHttpMessageHandler(Func<HttpRequestMessage, HttpResponseMessage> handler)
        {
            _handler = handler;
        }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            return Task.FromResult(_handler(request));
        }
    }

    private readonly Mock<ILogger<RideTypeClient>> _loggerMock = new();
    private readonly IOptions<ServiceUrls> _options = Options.Create(new ServiceUrls
    {
        RideTypeServiceUrl = "http://localhost:8089"
    });

    [Fact]
    public async Task GetRideTypeByUuidAsync_Success_ReturnsDto()
    {
        var expectedDto = new RideTypeDto
        {
            Uuid = "rt-uuid-1",
            Code = "GO",
            Name = "LEXOR Go",
            BaseFare = 50m,
            PerKmRate = 15m,
            PerMinuteRate = 2m,
            IsActive = true
        };

        var handler = new MockHttpMessageHandler(req =>
        {
            Assert.Equal(HttpMethod.Get, req.Method);
            Assert.Contains("/api/v1/ride-types/rt-uuid-1", req.RequestUri!.ToString());
            Assert.Equal("Bearer", req.Headers.Authorization?.Scheme);
            Assert.Equal("test-token", req.Headers.Authorization?.Parameter);

            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(JsonSerializer.Serialize(expectedDto))
            };
        });

        var client = new RideTypeClient(new HttpClient(handler), _options, _loggerMock.Object);
        var result = await client.GetRideTypeByUuidAsync("rt-uuid-1", "Bearer test-token");

        Assert.NotNull(result);
        Assert.Equal("GO", result.Code);
        Assert.Equal("LEXOR Go", result.Name);
        Assert.Equal(50m, result.BaseFare);
        Assert.True(result.IsActive);
    }

    [Fact]
    public async Task GetRideTypeByUuidAsync_EmptyUuid_ReturnsNull()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.OK));
        var client = new RideTypeClient(new HttpClient(handler), _options, _loggerMock.Object);

        var result = await client.GetRideTypeByUuidAsync("  ", "token");
        Assert.Null(result);
    }

    [Fact]
    public async Task GetRideTypeByUuidAsync_DisabledCategory_ThrowsBadRequestException()
    {
        var disabledDto = new RideTypeDto
        {
            Uuid = "rt-uuid-disabled",
            Code = "VINTAGE",
            Name = "LEXOR Vintage",
            IsActive = false
        };

        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(JsonSerializer.Serialize(disabledDto))
        });

        var client = new RideTypeClient(new HttpClient(handler), _options, _loggerMock.Object);

        await Assert.ThrowsAsync<BadRequestException>(() =>
            client.GetRideTypeByUuidAsync("rt-uuid-disabled"));
    }

    [Fact]
    public async Task GetRideTypeByUuidAsync_NotFound_ThrowsResourceNotFoundException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.NotFound));
        var client = new RideTypeClient(new HttpClient(handler), _options, _loggerMock.Object);

        await Assert.ThrowsAsync<ResourceNotFoundException>(() =>
            client.GetRideTypeByUuidAsync("rt-uuid-missing"));
    }

    [Fact]
    public async Task GetRideTypeByUuidAsync_ServerError_ThrowsServiceUnavailableException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.InternalServerError));
        var client = new RideTypeClient(new HttpClient(handler), _options, _loggerMock.Object);

        await Assert.ThrowsAsync<ServiceUnavailableException>(() =>
            client.GetRideTypeByUuidAsync("rt-uuid-error"));
    }
}
