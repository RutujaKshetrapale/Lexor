using BookingService.DTOs.Request;
using BookingService.DTOs.Response;
using BookingService.Enums;

namespace BookingService.Services;

public interface IBookingService
{
    // Rider
    Task<BookingResponse> CreateBookingAsync(string riderUserUuid, CreateBookingDto request, string? bearerToken = null, string? correlationId = null);
    Task<PageResponse<BookingResponse>> GetMyBookingsAsync(string riderUserUuid, BookingStatus? status, int page, int size);
    Task<BookingResponse> GetMyBookingByUuidAsync(string riderUserUuid, string bookingUuid);
    Task<BookingResponse> CancelBookingByRiderAsync(string riderUserUuid, string bookingUuid, CancelBookingDto? request);

    // Driver
    Task<PageResponse<BookingResponse>> GetDriverBookingsAsync(string driverUserUuid, BookingStatus? status, int page, int size);
    Task<BookingResponse> GetDriverBookingByUuidAsync(string driverUserUuid, string bookingUuid);
    Task<BookingResponse> AcceptBookingByDriverAsync(string driverUserUuid, string bookingUuid);
    Task<BookingResponse> DeclineBookingByDriverAsync(string driverUserUuid, string bookingUuid, DeclineBookingDto? request);
    Task<BookingResponse> ArriveBookingByDriverAsync(string driverUserUuid, string bookingUuid);
    Task<BookingResponse> StartTripByDriverAsync(string driverUserUuid, string bookingUuid, StartTripDto? request);
    Task<BookingResponse> CompleteTripByDriverAsync(string driverUserUuid, string bookingUuid, CompleteTripDto? request);

    // Admin
    Task<PageResponse<BookingResponse>> GetAdminBookingsAsync(long? riderId, long? driverId, BookingStatus? status, int page, int size);
    Task<BookingResponse> GetAdminBookingByUuidAsync(string bookingUuid);
}
