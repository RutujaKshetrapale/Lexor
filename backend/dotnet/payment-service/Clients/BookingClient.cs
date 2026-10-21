using System.Net.Http.Headers;
using System.Text.Json;
using Microsoft.Extensions.Options;
using PaymentService.Clients.DTOs;
using PaymentService.Configuration;

namespace PaymentService.Clients;

public interface IBookingClient
{
    Task<BookingDetailDto?> GetBookingDetailsAsync(string bookingUuid, string? authorizationHeader = null);
}

public class BookingClient : IBookingClient
{
    private readonly HttpClient _httpClient;
    private readonly ServiceUrls _urls;
    private readonly ILogger<BookingClient> _logger;

    public BookingClient(HttpClient httpClient, IOptions<ServiceUrls> urls, ILogger<BookingClient> logger)
    {
        _httpClient = httpClient;
        _urls = urls.Value;
        _logger = logger;
    }

    public async Task<BookingDetailDto?> GetBookingDetailsAsync(string bookingUuid, string? authorizationHeader = null)
    {
        try
        {
            var request = new HttpRequestMessage(HttpMethod.Get, $"{_urls.BookingUrl}/api/v1/admin/bookings/{bookingUuid}");
            if (!string.IsNullOrWhiteSpace(authorizationHeader))
            {
                request.Headers.Add("Authorization", authorizationHeader);
            }

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var content = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(content);
                var root = doc.RootElement;

                var dto = new BookingDetailDto
                {
                    BookingUuid = root.TryGetProperty("bookingUuid", out var bId) ? bId.GetString() ?? bookingUuid : bookingUuid,
                    Status = root.TryGetProperty("status", out var st) ? st.GetString() ?? "CONFIRMED" : "CONFIRMED",
                    TotalFare = root.TryGetProperty("totalFare", out var fare) ? fare.GetDecimal() : 250.00m,
                    Currency = root.TryGetProperty("currency", out var curr) ? curr.GetString() ?? "INR" : "INR",
                    PickupAddress = root.TryGetProperty("pickupAddress", out var pu) ? pu.GetString() ?? "" : "",
                    DropAddress = root.TryGetProperty("dropAddress", out var dr) ? dr.GetString() ?? "" : ""
                };

                if (root.TryGetProperty("rider", out var rider))
                {
                    dto.RiderUuid = rider.TryGetProperty("riderUuid", out var rUuid) ? rUuid.GetString() ?? "" : "";
                    var fn = rider.TryGetProperty("firstName", out var rFn) ? rFn.GetString() ?? "" : "";
                    var ln = rider.TryGetProperty("lastName", out var rLn) ? rLn.GetString() ?? "" : "";
                    dto.RiderName = $"{fn} {ln}".Trim();
                }

                if (root.TryGetProperty("driver", out var driver))
                {
                    dto.DriverUuid = driver.TryGetProperty("driverUuid", out var dUuid) ? dUuid.GetString() : null;
                    var fn = driver.TryGetProperty("firstName", out var dFn) ? dFn.GetString() ?? "" : "";
                    var ln = driver.TryGetProperty("lastName", out var dLn) ? dLn.GetString() ?? "" : "";
                    dto.DriverName = $"{fn} {ln}".Trim();
                }

                if (root.TryGetProperty("vehicle", out var veh))
                {
                    var make = veh.TryGetProperty("make", out var mk) ? mk.GetString() ?? "" : "";
                    var model = veh.TryGetProperty("model", out var md) ? md.GetString() ?? "" : "";
                    dto.VehicleModel = $"{make} {model}".Trim();
                    dto.LicensePlate = veh.TryGetProperty("licensePlate", out var lp) ? lp.GetString() ?? "" : "";
                }

                return dto;
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to fetch booking details from {Url}: {Message}", _urls.BookingUrl, ex.Message);
        }
        return null;
    }
}
