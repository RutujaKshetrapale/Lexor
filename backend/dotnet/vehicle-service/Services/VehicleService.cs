using System.Security.Claims;
using System.Text.RegularExpressions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using VehicleService.Data;
using VehicleService.DTOs.Request;
using VehicleService.DTOs.Response;
using VehicleService.Entities;
using VehicleService.Enums;
using VehicleService.Exceptions;

namespace VehicleService.Services;

public class VehicleService : IVehicleService
{
    private readonly LexorDbContext _dbContext;
    private readonly IVehicleVerificationPolicyService _policyService;
    private readonly ILogger<VehicleService> _logger;

    public VehicleService(
        LexorDbContext dbContext,
        IVehicleVerificationPolicyService policyService,
        ILogger<VehicleService> logger)
    {
        _dbContext = dbContext;
        _policyService = policyService;
        _logger = logger;
    }

    public async Task<VehicleResponse> RegisterVehicleAsync(ClaimsPrincipal user, RegisterVehicleRequest request)
    {
        var driver = await GetAuthenticatedDriverAsync(user);

        var normalizedPlate = NormalizeLicensePlate(request.LicensePlate);
        if (await _dbContext.Vehicles.AnyAsync(v => v.LicensePlate == normalizedPlate))
        {
            throw new DuplicateResourceException($"A vehicle with license plate {normalizedPlate} is already registered");
        }

        string? trimmedVin = null;
        if (!string.IsNullOrWhiteSpace(request.Vin))
        {
            trimmedVin = request.Vin.Trim().ToUpperInvariant();
            if (await _dbContext.Vehicles.AnyAsync(v => v.Vin == trimmedVin))
            {
                throw new DuplicateResourceException($"A vehicle with VIN {trimmedVin} is already registered");
            }
        }

        var vehicle = new Vehicle
        {
            Uuid = Guid.NewGuid().ToString(),
            DriverId = driver.Id,
            Driver = driver,
            Make = request.Make.Trim(),
            Model = request.Model.Trim(),
            Year = request.Year!.Value,
            Color = request.Color.Trim(),
            LicensePlate = normalizedPlate,
            Vin = trimmedVin,
            RideTypeCategory = request.RideTypeCategory.Trim().ToUpperInvariant(),
            SeatCapacity = request.SeatCapacity ?? 4,
            FuelType = request.FuelType ?? FuelType.PETROL,
            IsActive = true,
            IsInspected = false,
            VerificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.Vehicles.Add(vehicle);
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Driver {DriverId} registered new vehicle {VehicleUuid} ({Plate})",
            driver.Id, vehicle.Uuid, vehicle.LicensePlate);

        // Record initial assignment
        var assignment = new DriverVehicleAssignment
        {
            VehicleId = vehicle.Id,
            Vehicle = vehicle,
            DriverId = driver.Id,
            Driver = driver,
            Status = AssignmentStatus.ACTIVE,
            AssignedAt = DateTime.UtcNow,
            Reason = "Initial vehicle registration by owning driver",
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.DriverVehicleAssignments.Add(assignment);
        await _dbContext.SaveChangesAsync();

        return MapToResponse(vehicle);
    }

    public async Task<List<VehicleResponse>> GetMyVehiclesAsync(ClaimsPrincipal user)
    {
        var driver = await GetAuthenticatedDriverAsync(user);

        var vehicles = await _dbContext.Vehicles
            .Include(v => v.Driver)
                .ThenInclude(d => d.User)
            .Where(v => v.DriverId == driver.Id)
            .OrderByDescending(v => v.CreatedAt)
            .ToListAsync();

        return vehicles.Select(MapToResponse).ToList();
    }

    public async Task<VehicleResponse> GetVehicleByUuidAsync(ClaimsPrincipal user, string vehicleUuid)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);
        return MapToResponse(vehicle);
    }

    public async Task<VehicleResponse> UpdateVehicleAsync(ClaimsPrincipal user, string vehicleUuid, UpdateVehicleRequest request)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        if (vehicle.VerificationStatus == VehicleVerificationStatus.SUSPENDED)
        {
            throw new InvalidStatusTransitionException("Cannot update details of a suspended vehicle. Please contact administration.");
        }

        if (!string.IsNullOrWhiteSpace(request.Make))
        {
            vehicle.Make = request.Make.Trim();
        }
        if (!string.IsNullOrWhiteSpace(request.Model))
        {
            vehicle.Model = request.Model.Trim();
        }
        if (request.Year.HasValue)
        {
            vehicle.Year = request.Year.Value;
        }
        if (!string.IsNullOrWhiteSpace(request.Color))
        {
            vehicle.Color = request.Color.Trim();
        }
        if (!string.IsNullOrWhiteSpace(request.Vin))
        {
            var trimmedVin = request.Vin.Trim().ToUpperInvariant();
            if (await _dbContext.Vehicles.AnyAsync(v => v.Vin == trimmedVin && v.Id != vehicle.Id))
            {
                throw new DuplicateResourceException($"VIN {trimmedVin} is already in use by another vehicle");
            }
            vehicle.Vin = trimmedVin;
        }
        if (!string.IsNullOrWhiteSpace(request.RideTypeCategory))
        {
            vehicle.RideTypeCategory = request.RideTypeCategory.Trim().ToUpperInvariant();
        }
        if (request.SeatCapacity.HasValue)
        {
            vehicle.SeatCapacity = request.SeatCapacity.Value;
        }
        if (request.FuelType.HasValue)
        {
            vehicle.FuelType = request.FuelType.Value;
        }

        vehicle.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Updated vehicle details for {VehicleUuid}", vehicle.Uuid);
        return MapToResponse(vehicle);
    }

    public async Task<VehicleResponse> UpdateVehicleStatusAsync(ClaimsPrincipal user, string vehicleUuid, bool isActive)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        if (isActive && vehicle.VerificationStatus != VehicleVerificationStatus.VERIFIED)
        {
            throw new InvalidStatusTransitionException($"Cannot activate an unverified or suspended vehicle. Status is: {vehicle.VerificationStatus}");
        }

        vehicle.IsActive = isActive;
        vehicle.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Toggled active status for vehicle {VehicleUuid} to {IsActive}", vehicle.Uuid, isActive);
        return MapToResponse(vehicle);
    }

    public async Task<VehicleResponse> SubmitForVerificationAsync(ClaimsPrincipal user, string vehicleUuid)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        if (vehicle.VerificationStatus == VehicleVerificationStatus.SUSPENDED)
        {
            throw new InvalidStatusTransitionException("Cannot submit a suspended vehicle for verification. Contact support for reinstatement.");
        }
        if (vehicle.VerificationStatus == VehicleVerificationStatus.VERIFIED)
        {
            throw new InvalidStatusTransitionException("Vehicle is already verified.");
        }

        var documents = await _dbContext.VehicleDocuments
            .Where(d => d.VehicleId == vehicle.Id && d.IsCurrent)
            .ToListAsync();

        var missing = _policyService.GetMissingDocuments(documents);
        if (missing.Any())
        {
            throw new BadRequestException($"Cannot submit for verification. The following mandatory compliance documents are missing: {string.Join(", ", missing)}");
        }

        var oldStatus = vehicle.VerificationStatus;
        vehicle.VerificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION;
        vehicle.RejectionReason = null;
        vehicle.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Vehicle {VehicleUuid} submitted for administrative verification (previous status: {OldStatus})",
            vehicle.Uuid, oldStatus);

        return MapToResponse(vehicle);
    }

    public async Task<VehicleVerificationStatusResponse> GetVerificationStatusAsync(ClaimsPrincipal user, string vehicleUuid)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        var documents = await _dbContext.VehicleDocuments
            .Where(d => d.VehicleId == vehicle.Id && d.IsCurrent)
            .ToListAsync();

        var required = _policyService.GetRequiredDocumentTypes();
        var uploaded = documents.Select(d => d.DocumentType).ToList();
        var missing = _policyService.GetMissingDocuments(documents);
        var pendingReview = documents
            .Where(d => d.VerificationStatus == DocumentVerificationStatus.PENDING_REVIEW || d.VerificationStatus == DocumentVerificationStatus.UNDER_REVIEW)
            .Select(d => d.DocumentType)
            .ToList();
        var rejected = documents
            .Where(d => d.VerificationStatus == DocumentVerificationStatus.REJECTED || d.VerificationStatus == DocumentVerificationStatus.RESUBMISSION_REQUIRED)
            .Select(d => d.DocumentType)
            .ToList();

        var isEligible = vehicle.VerificationStatus == VehicleVerificationStatus.VERIFIED && vehicle.IsActive;

        return new VehicleVerificationStatusResponse
        {
            VehicleUuid = vehicle.Uuid,
            Status = vehicle.VerificationStatus,
            RejectionReason = vehicle.RejectionReason,
            IsEligibleForAssignment = isEligible,
            RequiredDocuments = required,
            UploadedDocuments = uploaded,
            MissingDocuments = missing,
            PendingReviewDocuments = pendingReview,
            RejectedDocuments = rejected
        };
    }

    public async Task<List<VehicleVerificationHistoryResponse>> GetVehicleVerificationHistoryAsync(ClaimsPrincipal user, string vehicleUuid)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        var history = await _dbContext.VehicleVerificationHistories
            .Include(h => h.ReviewedByAdmin)
            .Include(h => h.Document)
            .Where(h => h.VehicleId == vehicle.Id)
            .OrderByDescending(h => h.CreatedAt)
            .ToListAsync();

        return history.Select(h => MapToHistoryResponse(h, vehicle.Uuid)).ToList();
    }

    public async Task<List<DriverVehicleAssignmentResponse>> GetVehicleAssignmentsAsync(ClaimsPrincipal user, string vehicleUuid)
    {
        var vehicle = await GetVehicleAndVerifyOwnershipAsync(user, vehicleUuid);

        var assignments = await _dbContext.DriverVehicleAssignments
            .Include(a => a.Driver)
                .ThenInclude(d => d.User)
            .Include(a => a.AssignedByAdmin)
            .Where(a => a.VehicleId == vehicle.Id)
            .OrderByDescending(a => a.AssignedAt)
            .ToListAsync();

        return assignments.Select(a => MapToAssignmentResponse(a, vehicle.Uuid, vehicle.LicensePlate)).ToList();
    }

    private async Task<DriverProfile> GetAuthenticatedDriverAsync(ClaimsPrincipal user)
    {
        var userIdStr = user.FindFirst("userId")?.Value;
        var userUuid = user.FindFirst("sub")?.Value 
                       ?? user.FindFirst(ClaimTypes.NameIdentifier)?.Value
                       ?? user.FindFirst("uuid")?.Value;

        DriverProfile? driver = null;

        if (long.TryParse(userIdStr, out var userId))
        {
            driver = await _dbContext.DriverProfiles
                .Include(d => d.User)
                .FirstOrDefaultAsync(d => d.UserId == userId);
        }

        if (driver == null && !string.IsNullOrEmpty(userUuid))
        {
            driver = await _dbContext.DriverProfiles
                .Include(d => d.User)
                .FirstOrDefaultAsync(d => d.User.Uuid == userUuid);
        }

        if (driver == null)
        {
            throw new ForbiddenException("No driver profile found for authenticated user");
        }

        return driver;
    }

    private async Task<Vehicle> GetVehicleAndVerifyOwnershipAsync(ClaimsPrincipal user, string vehicleUuid)
    {
        var vehicle = await _dbContext.Vehicles
            .Include(v => v.Driver)
                .ThenInclude(d => d.User)
            .FirstOrDefaultAsync(v => v.Uuid == vehicleUuid);

        if (vehicle == null)
        {
            throw new ResourceNotFoundException($"Vehicle not found with UUID: {vehicleUuid}");
        }

        var isAdmin = user.IsInRole("ROLE_ADMIN") || user.IsInRole("ADMIN");

        if (!isAdmin)
        {
            var driver = await GetAuthenticatedDriverAsync(user);
            if (vehicle.DriverId != driver.Id)
            {
                throw new ForbiddenException("You do not have permission to access or manage this vehicle");
            }
        }

        return vehicle;
    }

    private static string NormalizeLicensePlate(string plate)
    {
        if (string.IsNullOrWhiteSpace(plate))
        {
            throw new BadRequestException("License plate cannot be blank");
        }
        var cleaned = Regex.Replace(plate.Trim().ToUpperInvariant(), @"\s+", "-");
        return Regex.Replace(cleaned, @"-+", "-");
    }

    private static VehicleResponse MapToResponse(Vehicle vehicle)
    {
        return new VehicleResponse
        {
            Uuid = vehicle.Uuid,
            DriverUuid = vehicle.Driver?.User?.Uuid,
            DriverName = vehicle.Driver?.User?.FullName,
            Make = vehicle.Make,
            Model = vehicle.Model,
            Year = vehicle.Year,
            Color = vehicle.Color,
            LicensePlate = vehicle.LicensePlate,
            Vin = vehicle.Vin,
            RideTypeCategory = vehicle.RideTypeCategory,
            SeatCapacity = vehicle.SeatCapacity,
            FuelType = vehicle.FuelType,
            IsActive = vehicle.IsActive,
            IsInspected = vehicle.IsInspected,
            InspectionExpiresAt = vehicle.InspectionExpiresAt,
            VerificationStatus = vehicle.VerificationStatus,
            RejectionReason = vehicle.RejectionReason,
            VerifiedAt = vehicle.VerifiedAt,
            CreatedAt = vehicle.CreatedAt,
            UpdatedAt = vehicle.UpdatedAt
        };
    }

    private static VehicleVerificationHistoryResponse MapToHistoryResponse(VehicleVerificationHistory history, string vehicleUuid)
    {
        return new VehicleVerificationHistoryResponse
        {
            Id = history.Id,
            VehicleUuid = vehicleUuid,
            DocumentId = history.DocumentId,
            DocumentType = history.Document?.DocumentType,
            ActionType = history.ActionType,
            PreviousStatus = history.PreviousStatus,
            NewStatus = history.NewStatus,
            ReviewedByAdminName = history.ReviewedByAdmin?.FullName,
            Reason = history.Reason,
            CreatedAt = history.CreatedAt
        };
    }

    private static DriverVehicleAssignmentResponse MapToAssignmentResponse(
        DriverVehicleAssignment assignment,
        string vehicleUuid,
        string licensePlate)
    {
        return new DriverVehicleAssignmentResponse
        {
            Id = assignment.Id,
            VehicleUuid = vehicleUuid,
            VehiclePlate = licensePlate,
            DriverUuid = assignment.Driver?.User?.Uuid,
            DriverName = assignment.Driver?.User?.FullName,
            AssignedByAdminName = assignment.AssignedByAdmin?.FullName,
            Status = assignment.Status,
            AssignedAt = assignment.AssignedAt,
            UnassignedAt = assignment.UnassignedAt,
            Reason = assignment.Reason
        };
    }
}
