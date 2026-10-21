using System.Net;
using System.Text.Json;
using BookingService.Clients;
using BookingService.Clients.DTOs;
using BookingService.Configuration;
using BookingService.Exceptions;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace BookingService.Tests;

public class RideRequestClientTests
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

    private class TimeoutHttpMessageHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            throw new TaskCanceledException("Operation timed out");
        }
    }

    private readonly Mock<ILogger<RideRequestClient>> _loggerMock = new();
    private readonly IOptions<ServiceUrls> _serviceUrls = Options.Create(new ServiceUrls
    {
        RideRequestServiceUrl = "http://localhost:8091"
    });

    [Fact]
    public async Task GetRideRequestByUuidAsync_Success_ReturnsRideRequestDto()
    {
        var expectedDto = new RideRequestClientDto
        {
            RequestUuid = "req-test-uuid-1",
            RiderUuid = "rider-user-uuid-1",
            RiderName = "Rajesh Kumar",
            PickupAddress = "Indiranagar 100ft Rd",
            PickupLatitude = 12.9784m,
            PickupLongitude = 77.6408m,
            DropAddress = "Prestige Tech Park",
            DropLatitude = 12.9372m,
            DropLongitude = 77.6949m,
            RideTypeCode = "GO",
            RideTypeName = "LEXOR Go",
            EstimatedDistanceKm = 11.5m,
            EstimatedDurationMinutes = 32,
            EstimatedFare = 250m,
            Currency = "INR",
            Status = "MATCHED",
            RequestedAt = DateTime.UtcNow
        };

        var handler = new MockHttpMessageHandler(req =>
        {
            Assert.Equal(HttpMethod.Get, req.Method);
            Assert.Contains("/api/v1/ride-requests/me/req-test-uuid-1", req.RequestUri!.ToString());
            Assert.Equal("Bearer", req.Headers.Authorization?.Scheme);
            Assert.Equal("mock-jwt-token", req.Headers.Authorization?.Parameter);

            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(JsonSerializer.Serialize(expectedDto))
            };
        });

        var client = new RideRequestClient(new HttpClient(handler), _serviceUrls, _loggerMock.Object);

        var result = await client.GetRideRequestByUuidAsync("req-test-uuid-1", "Bearer mock-jwt-token");

        Assert.NotNull(result);
        Assert.Equal("req-test-uuid-1", result.RequestUuid);
        Assert.Equal("rider-user-uuid-1", result.RiderUuid);
        Assert.Equal("Indiranagar 100ft Rd", result.PickupAddress);
        Assert.Equal(250m, result.EstimatedFare);
        Assert.Equal("MATCHED", result.Status);
    }

    [Fact]
    public async Task GetRideRequestByUuidAsync_TokenWithoutBearerPrefix_PassesProperHeader()
    {
        var expectedDto = new RideRequestClientDto
        {
            RequestUuid = "req-test-uuid-2",
            RiderUuid = "rider-user-uuid-1",
            Status = "MATCHED"
        };

        var handler = new MockHttpMessageHandler(req =>
        {
            Assert.Equal("Bearer", req.Headers.Authorization?.Scheme);
            Assert.Equal("raw-token-without-prefix", req.Headers.Authorization?.Parameter);

            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(JsonSerializer.Serialize(expectedDto))
            };
        });

        var client = new RideRequestClient(new HttpClient(handler), _serviceUrls, _loggerMock.Object);

        var result = await client.GetRideRequestByUuidAsync("req-test-uuid-2", "raw-token-without-prefix");

        Assert.NotNull(result);
        Assert.Equal("req-test-uuid-2", result.RequestUuid);
    }

    [Fact]
    public async Task GetRideRequestByUuidAsync_EmptyUuid_ReturnsNull()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.OK));
        var client = new RideRequestClient(new HttpClient(handler), _serviceUrls, _loggerMock.Object);

        var result = await client.GetRideRequestByUuidAsync("   ");

        Assert.Null(result);
    }

    [Fact]
    public async Task GetRideRequestByUuidAsync_NotFound_ThrowsResourceNotFoundException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.NotFound));
        var client = new RideRequestClient(new HttpClient(handler), _serviceUrls, _loggerMock.Object);

        await Assert.ThrowsAsync<ResourceNotFoundException>(() =>
            client.GetRideRequestByUuidAsync("req-nonexistent"));
    }

    [Fact]
    public async Task GetRideRequestByUuidAsync_Forbidden_ThrowsForbiddenException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.Forbidden));
        var client = new RideRequestClient(new HttpClient(handler), _serviceUrls, _loggerMock.Object);

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            client.GetRideRequestByUuidAsync("req-other-rider"));
    }

    [Fact]
    public async Task GetRideRequestByUuidAsync_BadRequest_ThrowsBadRequestException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.BadRequest));
        var client = new RideRequestClient(new HttpClient(handler), _serviceUrls, _loggerMock.Object);

        await Assert.ThrowsAsync<BadRequestException>(() =>
            client.GetRideRequestByUuidAsync("req-bad"));
    }

    [Fact]
    public async Task GetRideRequestByUuidAsync_ServerError_ThrowsServiceUnavailableException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.InternalServerError));
        var client = new RideRequestClient(new HttpClient(handler), _serviceUrls, _loggerMock.Object);

        await Assert.ThrowsAsync<ServiceUnavailableException>(() =>
            client.GetRideRequestByUuidAsync("req-server-err"));
    }

    [Fact]
    public async Task GetRideRequestByUuidAsync_Timeout_ThrowsServiceUnavailableException()
    {
        var handler = new TimeoutHttpMessageHandler();
        var client = new RideRequestClient(new HttpClient(handler), _serviceUrls, _loggerMock.Object);

        await Assert.ThrowsAsync<ServiceUnavailableException>(() =>
            client.GetRideRequestByUuidAsync("req-timeout"));
    }
}
