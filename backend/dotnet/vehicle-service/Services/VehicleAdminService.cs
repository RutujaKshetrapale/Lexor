using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using VehicleService.Data;
using VehicleService.DTOs.Request;
using VehicleService.DTOs.Response;
using VehicleService.Entities;
using VehicleService.Enums;
using VehicleService.Exceptions;
using VehicleService.Storage;

namespace VehicleService.Services;

public class VehicleAdminService : IVehicleAdminService
{
    private readonly LexorDbContext _dbContext;
    private readonly IDocumentStorageService _storageService;
    private readonly IVehicleVerificationPolicyService _policyService;
    private readonly ILogger<VehicleAdminService> _logger;

    public VehicleAdminService(
        LexorDbContext dbContext,
        IDocumentStorageService storageService,
        IVehicleVerificationPolicyService policyService,
        ILogger<VehicleAdminService> logger)
    {
        _dbContext = dbContext;
        _storageService = storageService;
        _policyService = policyService;
        _logger = logger;
    }

    public async Task<PagedResponse<VehicleResponse>> ListVehiclesAsync(
        VehicleVerificationStatus? verificationStatus,
        bool? isActive,
        string? rideTypeCategory,
        string? search,
        int page,
        int size,
        string sortBy,
        string sortDir)
    {
        var query = _dbContext.Vehicles
            .Include(v => v.Driver)
                .ThenInclude(d => d.User)
            .AsQueryable();

        if (verificationStatus.HasValue)
        {
            query = query.Where(v => v.VerificationStatus == verificationStatus.Value);
        }

        if (isActive.HasValue)
        {
            query = query.Where(v => v.IsActive == isActive.Value);
        }

        if (!string.IsNullOrWhiteSpace(rideTypeCategory))
        {
            var category = rideTypeCategory.Trim().ToUpperInvariant();
            query = query.Where(v => v.RideTypeCategory == category);
        }

        if (!string.IsNullOrWhiteSpace(search))
        {
            var s = search.Trim().ToLowerInvariant();
            query = query.Where(v =>
                v.Make.ToLower().Contains(s) ||
                v.Model.ToLower().Contains(s) ||
                v.LicensePlate.ToLower().Contains(s));
        }

        var totalElements = await query.LongCountAsync();

        // Apply Sorting
        var isAsc = string.Equals(sortDir, "asc", StringComparison.OrdinalIgnoreCase);
        query = (sortBy?.ToLowerInvariant(), isAsc) switch
        {
            ("make", true) => query.OrderBy(v => v.Make),
            ("make", false) => query.OrderByDescending(v => v.Make),
            ("model", true) => query.OrderBy(v => v.Model),
            ("model", false) => query.OrderByDescending(v => v.Model),
            ("year", true) => query.OrderBy(v => v.Year),
            ("year", false) => query.OrderByDescending(v => v.Year),
            ("licenseplate", true) => query.OrderBy(v => v.LicensePlate),
            ("licenseplate", false) => query.OrderByDescending(v => v.LicensePlate),
            ("verificationstatus", true) => query.OrderBy(v => v.VerificationStatus),
            ("verificationstatus", false) => query.OrderByDescending(v => v.VerificationStatus),
            (_, true) => query.OrderBy(v => v.CreatedAt),
            _ => query.OrderByDescending(v => v.CreatedAt)
        };

        var effectivePage = Math.Max(0, page);
        var effectiveSize = size > 0 ? size : 10;

        var items = await query
            .Skip(effectivePage * effectiveSize)
            .Take(effectiveSize)
            .ToListAsync();

        var totalPages = (int)Math.Ceiling(totalElements / (double)effectiveSize);
        var isLast = effectivePage >= totalPages - 1;

        return new PagedResponse<VehicleResponse>
        {
            Content = items.Select(MapToVehicleResponse).ToList(),
            Page = effectivePage,
            Size = effectiveSize,
            TotalElements = totalElements,
            TotalPages = totalPages,
            Last = isLast
        };
    }

    public async Task<PagedResponse<VehicleResponse>> ListPendingVehiclesAsync(
        int page,
        int size,
        string sortBy,
        string sortDir)
    {
        return await ListVehiclesAsync(
            VehicleVerificationStatus.PENDING_VERIFICATION,
            null,
            null,
            null,
            page,
            size,
            sortBy ?? "createdAt",
            sortDir ?? "asc"
        );
    }

    public async Task<VehicleResponse> GetVehicleDetailsAsync(string vehicleUuid)
    {
        var vehicle = await GetVehicleWithDriverAsync(vehicleUuid);
        return MapToVehicleResponse(vehicle);
    }

    public async Task<List<VehicleDocumentResponse>> GetVehicleDocumentsAsync(string vehicleUuid)
    {
        var vehicle = await GetVehicleAsync(vehicleUuid);

        var documents = await _dbContext.VehicleDocuments
            .Include(d => d.ReviewedByAdmin)
            .Where(d => d.VehicleId == vehicle.Id && d.IsCurrent)
            .OrderBy(d => d.DocumentType)
            .ToListAsync();

        return documents.Select(d => MapToDocumentResponse(d, vehicle.Uuid)).ToList();
    }

    public async Task<(Stream Stream, string ContentType, string FileName)> DownloadDocumentAsync(string vehicleUuid, long documentId)
    {
        var vehicle = await GetVehicleAsync(vehicleUuid);

        var document = await _dbContext.VehicleDocuments
            .FirstOrDefaultAsync(d => d.Id == documentId && d.VehicleId == vehicle.Id);

        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        var stream = _storageService.LoadAsStream(document.FilePath);
        return (stream, document.ContentType, document.FileName);
    }

    public async Task<VehicleDocumentResponse> ReviewDocumentAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        long documentId,
        ReviewVehicleDocumentRequest request)
    {
        var vehicle = await GetVehicleAsync(vehicleUuid);

        var document = await _dbContext.VehicleDocuments
            .FirstOrDefaultAsync(d => d.Id == documentId && d.VehicleId == vehicle.Id);

        if (document == null)
        {
            throw new ResourceNotFoundException($"Document not found with ID: {documentId}");
        }

        var admin = await GetAdminUserAsync(adminUser);

        var oldStatus = document.VerificationStatus;
        document.VerificationStatus = request.Status!.Value;
        document.RejectionReason = request.RejectionReason;
        document.ReviewNotes = request.ReviewNotes;
        document.ReviewedByAdminId = admin.Id;
        document.ReviewedByAdmin = admin;
        document.ReviewedAt = DateTime.UtcNow;
        document.UpdatedAt = DateTime.UtcNow;

        // Record verification audit history
        var history = new VehicleVerificationHistory
        {
            VehicleId = vehicle.Id,
            Vehicle = vehicle,
            DocumentId = document.Id,
            Document = document,
            ActionType = "DOCUMENT_REVIEW",
            PreviousStatus = oldStatus.ToString(),
            NewStatus = request.Status.Value.ToString(),
            ReviewedByAdminId = admin.Id,
            ReviewedByAdmin = admin,
            Reason = !string.IsNullOrWhiteSpace(request.RejectionReason) ? request.RejectionReason : request.ReviewNotes,
            CreatedAt = DateTime.UtcNow
        };

        _dbContext.VehicleVerificationHistories.Add(history);
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Admin {AdminEmail} reviewed document ID {DocId} on vehicle {VehicleUuid}: {OldStatus} -> {NewStatus}",
            admin.Email, documentId, vehicleUuid, oldStatus, request.Status);

        return MapToDocumentResponse(document, vehicle.Uuid);
    }

    public async Task<VehicleResponse> ReviewVehicleAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        ReviewVehicleRequest request)
    {
        var vehicle = await GetVehicleWithDriverAsync(vehicleUuid);
        var admin = await GetAdminUserAsync(adminUser);

        var oldStatus = vehicle.VerificationStatus;

        if (request.Status == VehicleVerificationStatus.VERIFIED)
        {
            // Verify mandatory compliance documents
            var currentDocs = await _dbContext.VehicleDocuments
                .Where(d => d.VehicleId == vehicle.Id && d.IsCurrent)
                .ToListAsync();

            var allVerified = _policyService.HasAllRequiredDocumentsVerified(currentDocs);
            if (!allVerified)
            {
                var missing = _policyService.GetMissingDocuments(currentDocs);
                var missingDetail = missing.Any()
                    ? string.Join(", ", missing)
                    : "one or more documents still pending review";
                throw new InvalidStatusTransitionException(
                    $"Cannot approve vehicle. Mandatory compliance documents are not fully verified. Missing or unverified: {missingDetail}");
            }

            vehicle.VerificationStatus = VehicleVerificationStatus.VERIFIED;
            vehicle.IsInspected = true;
            vehicle.VerifiedAt = DateTime.UtcNow;
            vehicle.ReviewedByAdminId = admin.Id;
            vehicle.ReviewedByAdmin = admin;
            vehicle.RejectionReason = null;
        }
        else if (request.Status == VehicleVerificationStatus.REJECTED)
        {
            if (string.IsNullOrWhiteSpace(request.RejectionReason))
            {
                throw new BadRequestException("Rejection reason is mandatory when rejecting a vehicle");
            }
            vehicle.VerificationStatus = VehicleVerificationStatus.REJECTED;
            vehicle.IsInspected = false;
            vehicle.RejectionReason = request.RejectionReason.Trim();
            vehicle.ReviewedByAdminId = admin.Id;
            vehicle.ReviewedByAdmin = admin;
        }
        else
        {
            throw new BadRequestException("Review decision status must be VERIFIED or REJECTED");
        }

        vehicle.UpdatedAt = DateTime.UtcNow;

        // Record verification audit history
        var history = new VehicleVerificationHistory
        {
            VehicleId = vehicle.Id,
            Vehicle = vehicle,
            ActionType = "VEHICLE_VERIFICATION",
            PreviousStatus = oldStatus.ToString(),
            NewStatus = request.Status.Value.ToString(),
            ReviewedByAdminId = admin.Id,
            ReviewedByAdmin = admin,
            Reason = !string.IsNullOrWhiteSpace(request.RejectionReason) ? request.RejectionReason : request.Notes,
            CreatedAt = DateTime.UtcNow
        };

        _dbContext.VehicleVerificationHistories.Add(history);
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Admin {AdminEmail} changed vehicle {VehicleUuid} status from {OldStatus} to {NewStatus}",
            admin.Email, vehicleUuid, oldStatus, request.Status);

        return MapToVehicleResponse(vehicle);
    }

    public async Task<VehicleResponse> SuspendOrReinstateVehicleAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        SuspendVehicleRequest request)
    {
        var vehicle = await GetVehicleWithDriverAsync(vehicleUuid);
        var admin = await GetAdminUserAsync(adminUser);

        var oldStatus = vehicle.VerificationStatus;
        var action = request.Action.Trim().ToUpperInvariant();

        if ("SUSPEND" == action)
        {
            vehicle.VerificationStatus = VehicleVerificationStatus.SUSPENDED;
            vehicle.IsActive = false;
            vehicle.RejectionReason = request.Reason;
            vehicle.ReviewedByAdminId = admin.Id;
            vehicle.ReviewedByAdmin = admin;

            // Deactivate active assignments
            var activeAssignment = await _dbContext.DriverVehicleAssignments
                .FirstOrDefaultAsync(a => a.VehicleId == vehicle.Id && a.Status == AssignmentStatus.ACTIVE);

            if (activeAssignment != null)
            {
                activeAssignment.Status = AssignmentStatus.INACTIVE;
                activeAssignment.UnassignedAt = DateTime.UtcNow;
                activeAssignment.Reason = $"Vehicle suspended by admin: {request.Reason}";
                activeAssignment.UpdatedAt = DateTime.UtcNow;
            }

            var history = new VehicleVerificationHistory
            {
                VehicleId = vehicle.Id,
                Vehicle = vehicle,
                ActionType = "VEHICLE_SUSPENSION",
                PreviousStatus = oldStatus.ToString(),
                NewStatus = VehicleVerificationStatus.SUSPENDED.ToString(),
                ReviewedByAdminId = admin.Id,
                ReviewedByAdmin = admin,
                Reason = request.Reason,
                CreatedAt = DateTime.UtcNow
            };

            _dbContext.VehicleVerificationHistories.Add(history);
        }
        else if ("REINSTATE" == action)
        {
            if (oldStatus != VehicleVerificationStatus.SUSPENDED)
            {
                throw new InvalidStatusTransitionException("Only a suspended vehicle can be reinstated");
            }

            vehicle.VerificationStatus = VehicleVerificationStatus.VERIFIED;
            vehicle.IsActive = true;
            vehicle.RejectionReason = null;
            vehicle.ReviewedByAdminId = admin.Id;
            vehicle.ReviewedByAdmin = admin;

            var history = new VehicleVerificationHistory
            {
                VehicleId = vehicle.Id,
                Vehicle = vehicle,
                ActionType = "VEHICLE_REINSTATEMENT",
                PreviousStatus = oldStatus.ToString(),
                NewStatus = VehicleVerificationStatus.VERIFIED.ToString(),
                ReviewedByAdminId = admin.Id,
                ReviewedByAdmin = admin,
                Reason = request.Reason,
                CreatedAt = DateTime.UtcNow
            };

            _dbContext.VehicleVerificationHistories.Add(history);
        }
        else
        {
            throw new BadRequestException("Action must be either 'SUSPEND' or 'REINSTATE'");
        }

        vehicle.UpdatedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Admin {AdminEmail} performed {Action} on vehicle {VehicleUuid}",
            admin.Email, action, vehicleUuid);

        return MapToVehicleResponse(vehicle);
    }

    public async Task<List<VehicleVerificationHistoryResponse>> GetVerificationHistoryAsync(string vehicleUuid)
    {
        var vehicle = await GetVehicleAsync(vehicleUuid);

        var history = await _dbContext.VehicleVerificationHistories
            .Include(h => h.ReviewedByAdmin)
            .Include(h => h.Document)
            .Where(h => h.VehicleId == vehicle.Id)
            .OrderByDescending(h => h.CreatedAt)
            .ToListAsync();

        return history.Select(h => MapToHistoryResponse(h, vehicle.Uuid)).ToList();
    }

    public async Task<DriverVehicleAssignmentResponse> AssignDriverAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        AssignDriverRequest request)
    {
        var vehicle = await GetVehicleWithDriverAsync(vehicleUuid);
        var admin = await GetAdminUserAsync(adminUser);

        // Business rule 6 & 7: Only verified and active vehicles can be assigned
        if (vehicle.VerificationStatus != VehicleVerificationStatus.VERIFIED || !vehicle.IsActive)
        {
            throw new InvalidStatusTransitionException(
                $"Vehicle {vehicle.LicensePlate} is not verified or is inactive. Only verified active vehicles can be assigned.");
        }

        // Look up driver profile by User UUID
        var driver = await _dbContext.DriverProfiles
            .Include(d => d.User)
            .FirstOrDefaultAsync(d => d.User.Uuid == request.DriverUuid);

        if (driver == null)
        {
            throw new ResourceNotFoundException($"Driver profile not found with user UUID: {request.DriverUuid}");
        }

        // Business rule 7: Enforce driver eligibility
        if (!driver.IsVerified || driver.OnboardingStatus != OnboardingStatus.VERIFIED)
        {
            throw new InvalidStatusTransitionException($"Driver {driver.User.FullName} is not verified for vehicle assignment");
        }
        if (driver.Status == DriverStatus.SUSPENDED)
        {
            throw new InvalidStatusTransitionException("Cannot assign vehicle to a suspended driver");
        }

        // Terminate existing active assignment for this vehicle if present
        var currentAssignment = await _dbContext.DriverVehicleAssignments
            .FirstOrDefaultAsync(a => a.VehicleId == vehicle.Id && a.Status == AssignmentStatus.ACTIVE);

        if (currentAssignment != null)
        {
            currentAssignment.Status = AssignmentStatus.TERMINATED;
            currentAssignment.UnassignedAt = DateTime.UtcNow;
            currentAssignment.Reason = $"Replaced by administrative assignment to driver {driver.User.FullName}";
            currentAssignment.UpdatedAt = DateTime.UtcNow;
        }

        // Assign to new driver
        var newAssignment = new DriverVehicleAssignment
        {
            VehicleId = vehicle.Id,
            Vehicle = vehicle,
            DriverId = driver.Id,
            Driver = driver,
            AssignedByAdminId = admin.Id,
            AssignedByAdmin = admin,
            Status = AssignmentStatus.ACTIVE,
            AssignedAt = DateTime.UtcNow,
            Reason = request.Reason,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.DriverVehicleAssignments.Add(newAssignment);

        // Update assigned driver on vehicle
        vehicle.DriverId = driver.Id;
        vehicle.Driver = driver;
        vehicle.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Admin {AdminEmail} assigned vehicle {VehicleUuid} to driver {DriverEmail}",
            admin.Email, vehicleUuid, driver.User.Email);

        return MapToAssignmentResponse(newAssignment, vehicle.Uuid, vehicle.LicensePlate);
    }

    public async Task<DriverVehicleAssignmentResponse> UnassignDriverAsync(
        ClaimsPrincipal adminUser,
        string vehicleUuid,
        long assignmentId,
        string? reason)
    {
        var vehicle = await GetVehicleAsync(vehicleUuid);

        var assignment = await _dbContext.DriverVehicleAssignments
            .Include(a => a.Driver)
                .ThenInclude(d => d.User)
            .Include(a => a.AssignedByAdmin)
            .FirstOrDefaultAsync(a => a.Id == assignmentId);

        if (assignment == null)
        {
            throw new ResourceNotFoundException($"Assignment not found with ID: {assignmentId}");
        }

        if (assignment.VehicleId != vehicle.Id)
        {
            throw new BadRequestException("Assignment does not belong to specified vehicle");
        }

        assignment.Status = AssignmentStatus.TERMINATED;
        assignment.UnassignedAt = DateTime.UtcNow;
        assignment.Reason = reason ?? "Unassigned by administrator";
        assignment.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();

        _logger.LogInformation("Unassigned driver assignment ID {AssignmentId} for vehicle {VehicleUuid}",
            assignmentId, vehicleUuid);

        return MapToAssignmentResponse(assignment, vehicle.Uuid, vehicle.LicensePlate);
    }

    public async Task<List<DriverVehicleAssignmentResponse>> GetAssignmentHistoryAsync(string vehicleUuid)
    {
        var vehicle = await GetVehicleAsync(vehicleUuid);

        var assignments = await _dbContext.DriverVehicleAssignments
            .Include(a => a.Driver)
                .ThenInclude(d => d.User)
            .Include(a => a.AssignedByAdmin)
            .Where(a => a.VehicleId == vehicle.Id)
            .OrderByDescending(a => a.AssignedAt)
            .ToListAsync();

        return assignments.Select(a => MapToAssignmentResponse(a, vehicle.Uuid, vehicle.LicensePlate)).ToList();
    }

    private async Task<Vehicle> GetVehicleAsync(string vehicleUuid)
    {
        var vehicle = await _dbContext.Vehicles
            .FirstOrDefaultAsync(v => v.Uuid == vehicleUuid);

        if (vehicle == null)
        {
            throw new ResourceNotFoundException($"Vehicle not found with UUID: {vehicleUuid}");
        }

        return vehicle;
    }

    private async Task<Vehicle> GetVehicleWithDriverAsync(string vehicleUuid)
    {
        var vehicle = await _dbContext.Vehicles
            .Include(v => v.Driver)
                .ThenInclude(d => d.User)
            .FirstOrDefaultAsync(v => v.Uuid == vehicleUuid);

        if (vehicle == null)
        {
            throw new ResourceNotFoundException($"Vehicle not found with UUID: {vehicleUuid}");
        }

        return vehicle;
    }

    private async Task<User> GetAdminUserAsync(ClaimsPrincipal adminUser)
    {
        var userIdStr = adminUser.FindFirst("userId")?.Value;
        var userUuid = adminUser.FindFirst("sub")?.Value 
                       ?? adminUser.FindFirst(ClaimTypes.NameIdentifier)?.Value
                       ?? adminUser.FindFirst("uuid")?.Value;

        User? user = null;
        if (long.TryParse(userIdStr, out var userId))
        {
            user = await _dbContext.Users.FindAsync(userId);
        }
        if (user == null && !string.IsNullOrEmpty(userUuid))
        {
            user = await _dbContext.Users.FirstOrDefaultAsync(u => u.Uuid == userUuid);
        }

        if (user == null)
        {
            throw new ResourceNotFoundException("Admin user not found");
        }

        return user;
    }

    private static VehicleResponse MapToVehicleResponse(Vehicle vehicle)
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

    private static VehicleDocumentResponse MapToDocumentResponse(VehicleDocument doc, string vehicleUuid)
    {
        return new VehicleDocumentResponse
        {
            Id = doc.Id,
            VehicleUuid = vehicleUuid,
            DocumentType = doc.DocumentType,
            DocumentNumber = doc.DocumentNumber,
            FileName = doc.FileName,
            ContentType = doc.ContentType,
            FileSize = doc.FileSize,
            IssueDate = doc.IssueDate,
            ExpiryDate = doc.ExpiryDate,
            IssuingAuthority = doc.IssuingAuthority,
            VerificationStatus = doc.VerificationStatus,
            RejectionReason = doc.RejectionReason,
            ReviewNotes = doc.ReviewNotes,
            ReviewedByAdminName = doc.ReviewedByAdmin?.FullName,
            ReviewedAt = doc.ReviewedAt,
            Version = doc.Version,
            IsCurrent = doc.IsCurrent,
            CreatedAt = doc.CreatedAt,
            UpdatedAt = doc.UpdatedAt
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
