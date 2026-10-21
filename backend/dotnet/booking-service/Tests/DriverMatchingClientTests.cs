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

public class DriverMatchingClientTests
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

    private readonly Mock<ILogger<DriverMatchingClient>> _loggerMock = new();
    private readonly IOptions<ServiceUrls> _serviceUrls = Options.Create(new ServiceUrls
    {
        MatchingServiceUrl = "http://localhost:8093"
    });
    private readonly IOptions<BookingSettings> _bookingSettings = Options.Create(new BookingSettings
    {
        InternalApiKey = "lexor-internal-secret-key-phase8"
    });

    [Fact]
    public async Task GetMatchingStatusAsync_Success_ReturnsMatchingAttempt()
    {
        var expectedDto = new MatchingAttemptClientDto
        {
            AttemptUuid = "att-test-123",
            RideRequestUuid = "req-test-123",
            Status = "MATCHED",
            AttemptNumber = 1,
            CandidatesEvaluated = 3,
            Driver = new DriverCandidateClientDto
            {
                DriverUuid = "drv-uuid-1",
                FirstName = "Vikram",
                LastName = "Singh",
                PhoneNumber = "+919876543212",
                Rating = 4.95m,
                TotalTrips = 80
            },
            Vehicle = new VehicleClientDto
            {
                VehicleUuid = "veh-uuid-1",
                Make = "Toyota",
                Model = "Hyryder",
                Color = "Black",
                LicensePlate = "KA-01-EQ-4829"
            }
        };

        var handler = new MockHttpMessageHandler(req =>
        {
            Assert.Equal(HttpMethod.Get, req.Method);
            Assert.Contains("/api/v1/internal/matching/ride-requests/req-test-123", req.RequestUri!.ToString());
            Assert.True(req.Headers.Contains("X-Internal-Service-Key"));
            Assert.Equal("lexor-internal-secret-key-phase8", req.Headers.GetValues("X-Internal-Service-Key").First());

            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(JsonSerializer.Serialize(expectedDto))
            };
        });

        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        var result = await client.GetMatchingStatusAsync("req-test-123");

        Assert.NotNull(result);
        Assert.Equal("att-test-123", result.AttemptUuid);
        Assert.Equal("MATCHED", result.Status);
        Assert.NotNull(result.Driver);
        Assert.Equal("Vikram", result.Driver.FirstName);
        Assert.NotNull(result.Vehicle);
        Assert.Equal("KA-01-EQ-4829", result.Vehicle.LicensePlate);
    }

    [Fact]
    public async Task GetMatchingStatusAsync_EmptyRequestUuid_ReturnsNull()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.OK));
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        var result = await client.GetMatchingStatusAsync("");

        Assert.Null(result);
    }

    [Fact]
    public async Task GetMatchingStatusAsync_NotFound_ThrowsResourceNotFoundException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.NotFound));
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        await Assert.ThrowsAsync<ResourceNotFoundException>(() =>
            client.GetMatchingStatusAsync("req-unknown"));
    }

    [Fact]
    public async Task GetMatchingStatusAsync_Conflict_ThrowsConflictException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.Conflict));
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        await Assert.ThrowsAsync<ConflictException>(() =>
            client.GetMatchingStatusAsync("req-conflict"));
    }

    [Fact]
    public async Task GetMatchingStatusAsync_BadRequest_ThrowsBadRequestException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.BadRequest));
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        await Assert.ThrowsAsync<BadRequestException>(() =>
            client.GetMatchingStatusAsync("req-bad"));
    }

    [Fact]
    public async Task GetMatchingStatusAsync_ServerError_ThrowsServiceUnavailableException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.InternalServerError));
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        await Assert.ThrowsAsync<ServiceUnavailableException>(() =>
            client.GetMatchingStatusAsync("req-server-err"));
    }

    [Fact]
    public async Task GetMatchingStatusAsync_Timeout_ThrowsServiceUnavailableException()
    {
        var handler = new TimeoutHttpMessageHandler();
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        await Assert.ThrowsAsync<ServiceUnavailableException>(() =>
            client.GetMatchingStatusAsync("req-timeout"));
    }

    [Fact]
    public async Task InitiateMatchingAsync_Success_ReturnsMatchingAttempt()
    {
        var expectedDto = new MatchingAttemptClientDto
        {
            AttemptUuid = "att-init-123",
            RideRequestUuid = "req-init-123",
            Status = "MATCHED",
            AttemptNumber = 1
        };

        var handler = new MockHttpMessageHandler(req =>
        {
            Assert.Equal(HttpMethod.Post, req.Method);
            Assert.Contains("/api/v1/internal/matching/ride-requests/req-init-123/attempts", req.RequestUri!.ToString());
            Assert.True(req.Headers.Contains("X-Internal-Service-Key"));
            Assert.Equal("lexor-internal-secret-key-phase8", req.Headers.GetValues("X-Internal-Service-Key").First());
            Assert.True(req.Headers.Contains("X-Correlation-Id"));
            Assert.Equal("corr-xyz-789", req.Headers.GetValues("X-Correlation-Id").First());

            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(JsonSerializer.Serialize(expectedDto))
            };
        });

        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        var result = await client.InitiateMatchingAsync("req-init-123", "corr-xyz-789");

        Assert.NotNull(result);
        Assert.Equal("att-init-123", result.AttemptUuid);
        Assert.Equal("MATCHED", result.Status);
    }

    [Fact]
    public async Task InitiateMatchingAsync_NotFound_ThrowsResourceNotFoundException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.NotFound));
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        await Assert.ThrowsAsync<ResourceNotFoundException>(() =>
            client.InitiateMatchingAsync("req-missing"));
    }

    [Fact]
    public async Task InitiateMatchingAsync_Conflict_ThrowsConflictException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.Conflict));
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        await Assert.ThrowsAsync<ConflictException>(() =>
            client.InitiateMatchingAsync("req-conflict"));
    }

    [Fact]
    public async Task InitiateMatchingAsync_ServerError_ThrowsServiceUnavailableException()
    {
        var handler = new MockHttpMessageHandler(_ => new HttpResponseMessage(HttpStatusCode.InternalServerError));
        var client = new DriverMatchingClient(new HttpClient(handler), _serviceUrls, _bookingSettings, _loggerMock.Object);

        await Assert.ThrowsAsync<ServiceUnavailableException>(() =>
            client.InitiateMatchingAsync("req-500"));
    }
}
