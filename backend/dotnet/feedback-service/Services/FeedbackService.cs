using System.Security.Claims;
using FeedbackService.Clients;
using FeedbackService.Configuration;
using FeedbackService.Data;
using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;
using FeedbackService.DTOs;
using FeedbackService.Exceptions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace FeedbackService.Services;

public class FeedbackService : IFeedbackService
{
    private readonly FeedbackDbContext _dbContext;
    private readonly IBookingVerificationClient _bookingClient;
    private readonly INotificationClient _notificationClient;
    private readonly IRatingCalculationService _ratingCalculationService;
    private readonly FeedbackSettings _settings;
    private readonly ILogger<FeedbackService> _logger;

    public FeedbackService(
        FeedbackDbContext dbContext,
        IBookingVerificationClient bookingClient,
        INotificationClient notificationClient,
        IRatingCalculationService ratingCalculationService,
        IOptions<FeedbackSettings> settings,
        ILogger<FeedbackService> logger)
    {
        _dbContext = dbContext;
        _bookingClient = bookingClient;
        _notificationClient = notificationClient;
        _ratingCalculationService = ratingCalculationService;
        _settings = settings.Value;
        _logger = logger;
    }

    private static string GetUserId(ClaimsPrincipal user)
    {
        return user.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? user.FindFirstValue("sub")
            ?? user.FindFirstValue("uuid")
            ?? string.Empty;
    }

    public async Task<FeedbackResponse> SubmitRiderFeedbackAsync(
        ClaimsPrincipal rider, string rideUuid, SubmitRatingRequest request, string? authHeader = null)
    {
        var riderUuid = GetUserId(rider);
        _logger.LogInformation("Rider {Rider} submitting feedback for ride {Ride}", riderUuid, rideUuid);

        // 1. Authoritative ride verification
        var ride = await _bookingClient.VerifyRideEligibilityAsync(rideUuid, authHeader);
        if (ride == null)
            throw new ResourceNotFoundException($"Ride not found with UUID: {rideUuid}");

        // 2. Validate authoritative completed state
        if (!ride.IsAuthoritativeCompleted())
            throw new IneligibleFeedbackException($"Cannot review ride in status: {ride.Status}. Ride must be completed.");

        // 3. Validate reviewer participation
        if (!ride.RiderUuid.Equals(riderUuid, StringComparison.OrdinalIgnoreCase))
            throw new ForbiddenException("Access denied: You are not the rider on this ride");

        // 4. Validate driver existence on ride
        if (string.IsNullOrWhiteSpace(ride.DriverUuid))
            throw new IneligibleFeedbackException("Ride does not have an assigned driver to evaluate");

        // 5. Check submission deadline
        var completedAt = ride.CompletedAt;
        if (completedAt.AddDays(_settings.SubmissionWindowDays) < DateTime.UtcNow)
            throw new IneligibleFeedbackException($"Feedback submission deadline has expired ({_settings.SubmissionWindowDays} days)");

        // 6. Check duplicate submission
        var exists = await _dbContext.Feedbacks.AnyAsync(f =>
            f.RideUuid == ride.RideUuid && f.ReviewerUuid == riderUuid && f.ReviewerRole == FeedbackRole.RIDER);
        if (exists)
            throw new ConflictException("You have already submitted a review for this ride");

        // 7. Resolve tags
        var tags = await ResolveTagsAsync(request.TagCodes, TagTargetRole.DRIVER);

        // 8. Create feedback record
        var now = DateTime.UtcNow;
        var editableUntil = _settings.EditEnabled ? now.AddMinutes(_settings.EditWindowMinutes) : (DateTime?)null;

        var feedback = new RideFeedback
        {
            FeedbackUuid = Guid.NewGuid().ToString(),
            RideUuid = ride.RideUuid,
            BookingUuid = ride.BookingUuid,
            ReviewerUuid = riderUuid,
            TargetUuid = ride.DriverUuid,
            ReviewerRole = FeedbackRole.RIDER,
            TargetRole = FeedbackRole.DRIVER,
            Rating = request.Rating,
            ReviewText = request.ReviewText,
            ModerationStatus = ModerationStatus.VISIBLE,
            IsEdited = false,
            EditableUntil = editableUntil,
            CreatedAt = now,
            UpdatedAt = now
        };

        _dbContext.Feedbacks.Add(feedback);
        await _dbContext.SaveChangesAsync();

        if (tags.Count > 0)
        {
            foreach (var tag in tags)
            {
                _dbContext.TagAssignments.Add(new FeedbackTagAssignment
                {
                    FeedbackId = feedback.Id,
                    TagId = tag.Id,
                    CreatedAt = DateTime.UtcNow
                });
            }
            await _dbContext.SaveChangesAsync();
        }

        // Reload assignments for response
        await _dbContext.Entry(feedback)
            .Collection(f => f.TagAssignments)
            .Query()
            .Include(a => a.Tag)
            .LoadAsync();

        // 9. Update driver rating summary
        await _ratingCalculationService.UpdateRatingSummaryOnNewFeedbackAsync(feedback);

        // 10. Asynchronous notification dispatch
        await _notificationClient.SendNotificationAsync(new DirectNotificationRequest
        {
            RecipientUuid = ride.DriverUuid,
            RecipientRole = "DRIVER",
            NotificationType = "NEW_RATING_RECEIVED",
            Category = "RIDE",
            Title = "New Rider Rating",
            Body = $"You received a {request.Rating}-star rating for your recent trip.",
            RelatedEntityType = "RIDE",
            RelatedEntityUuid = ride.RideUuid,
            IdempotencyKey = $"NOTIF-RATING-{feedback.FeedbackUuid}",
            Channels = new List<string> { "PUSH", "IN_APP" }
        });

        return FeedbackResponse.FromEntity(feedback);
    }

    public async Task<FeedbackResponse> SubmitDriverFeedbackAsync(
        ClaimsPrincipal driver, string rideUuid, SubmitRatingRequest request, string? authHeader = null)
    {
        var driverUuid = GetUserId(driver);
        _logger.LogInformation("Driver {Driver} submitting feedback for ride {Ride}", driverUuid, rideUuid);

        // 1. Authoritative ride verification
        var ride = await _bookingClient.VerifyRideEligibilityAsync(rideUuid, authHeader);
        if (ride == null)
            throw new ResourceNotFoundException($"Ride not found with UUID: {rideUuid}");

        // 2. Validate authoritative completed state
        if (!ride.IsAuthoritativeCompleted())
            throw new IneligibleFeedbackException($"Cannot review ride in status: {ride.Status}. Ride must be completed.");

        // 3. Validate driver participation
        if (!ride.DriverUuid.Equals(driverUuid, StringComparison.OrdinalIgnoreCase))
            throw new ForbiddenException("Access denied: You are not the assigned driver for this ride");

        // 4. Validate rider existence
        if (string.IsNullOrWhiteSpace(ride.RiderUuid))
            throw new IneligibleFeedbackException("Ride does not have a valid rider reference");

        // 5. Check submission deadline
        var completedAt = ride.CompletedAt;
        if (completedAt.AddDays(_settings.SubmissionWindowDays) < DateTime.UtcNow)
            throw new IneligibleFeedbackException($"Feedback submission deadline has expired ({_settings.SubmissionWindowDays} days)");

        // 6. Check duplicate submission
        var exists = await _dbContext.Feedbacks.AnyAsync(f =>
            f.RideUuid == ride.RideUuid && f.ReviewerUuid == driverUuid && f.ReviewerRole == FeedbackRole.DRIVER);
        if (exists)
            throw new ConflictException("You have already submitted a review for this ride");

        // 7. Resolve tags
        var tags = await ResolveTagsAsync(request.TagCodes, TagTargetRole.RIDER);

        // 8. Create feedback record
        var now = DateTime.UtcNow;
        var editableUntil = _settings.EditEnabled ? now.AddMinutes(_settings.EditWindowMinutes) : (DateTime?)null;

        var feedback = new RideFeedback
        {
            FeedbackUuid = Guid.NewGuid().ToString(),
            RideUuid = ride.RideUuid,
            BookingUuid = ride.BookingUuid,
            ReviewerUuid = driverUuid,
            TargetUuid = ride.RiderUuid,
            ReviewerRole = FeedbackRole.DRIVER,
            TargetRole = FeedbackRole.RIDER,
            Rating = request.Rating,
            ReviewText = request.ReviewText,
            ModerationStatus = ModerationStatus.VISIBLE,
            IsEdited = false,
            EditableUntil = editableUntil,
            CreatedAt = now,
            UpdatedAt = now
        };

        _dbContext.Feedbacks.Add(feedback);
        await _dbContext.SaveChangesAsync();

        if (tags.Count > 0)
        {
            foreach (var tag in tags)
            {
                _dbContext.TagAssignments.Add(new FeedbackTagAssignment
                {
                    FeedbackId = feedback.Id,
                    TagId = tag.Id,
                    CreatedAt = DateTime.UtcNow
                });
            }
            await _dbContext.SaveChangesAsync();
        }

        await _dbContext.Entry(feedback)
            .Collection(f => f.TagAssignments)
            .Query()
            .Include(a => a.Tag)
            .LoadAsync();

        // 9. Update rider rating summary
        await _ratingCalculationService.UpdateRatingSummaryOnNewFeedbackAsync(feedback);

        return FeedbackResponse.FromEntity(feedback);
    }

    public async Task<PageResponse<FeedbackResponse>> GetMySubmittedFeedbackAsync(ClaimsPrincipal user, int page, int size)
    {
        var userId = GetUserId(user);
        var query = _dbContext.Feedbacks
            .Include(f => f.TagAssignments)
            .ThenInclude(a => a.Tag)
            .Where(f => f.ReviewerUuid == userId);

        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(f => f.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var dtos = items.Select(FeedbackResponse.FromEntity).ToList();
        return PageResponse<FeedbackResponse>.Create(dtos, page, size, total);
    }

    public Task<List<PendingFeedbackItemResponse>> GetMyPendingFeedbackAsync(ClaimsPrincipal user, string? authHeader = null)
    {
        return Task.FromResult(new List<PendingFeedbackItemResponse>());
    }

    public async Task<FeedbackResponse> GetMyReviewByUuidAsync(ClaimsPrincipal user, string reviewUuid)
    {
        var userId = GetUserId(user);
        var feedback = await _dbContext.Feedbacks
            .Include(f => f.TagAssignments)
            .ThenInclude(a => a.Tag)
            .FirstOrDefaultAsync(f => f.FeedbackUuid == reviewUuid);

        if (feedback == null)
            throw new ResourceNotFoundException($"Review not found with UUID: {reviewUuid}");

        if (!feedback.ReviewerUuid.Equals(userId, StringComparison.OrdinalIgnoreCase))
            throw new ForbiddenException("Access denied: You do not own this review");

        return FeedbackResponse.FromEntity(feedback);
    }

    public async Task<FeedbackResponse> UpdateMyReviewAsync(ClaimsPrincipal user, string reviewUuid, UpdateRatingRequest request)
    {
        var userId = GetUserId(user);
        var feedback = await _dbContext.Feedbacks
            .Include(f => f.TagAssignments)
            .ThenInclude(a => a.Tag)
            .FirstOrDefaultAsync(f => f.FeedbackUuid == reviewUuid);

        if (feedback == null)
            throw new ResourceNotFoundException($"Review not found with UUID: {reviewUuid}");

        if (!feedback.ReviewerUuid.Equals(userId, StringComparison.OrdinalIgnoreCase))
            throw new ForbiddenException("Access denied: You do not own this review");

        if (!_settings.EditEnabled)
            throw new BadRequestException("Review editing is currently disabled");

        if (feedback.EditableUntil.HasValue && DateTime.UtcNow > feedback.EditableUntil.Value)
            throw new BadRequestException("The edit window for this review has expired");

        int oldRating = feedback.Rating;
        bool ratingChanged = false;

        if (request.Rating.HasValue && request.Rating.Value != oldRating)
        {
            feedback.Rating = request.Rating.Value;
            ratingChanged = true;
        }

        if (request.ReviewText != null)
        {
            feedback.ReviewText = request.ReviewText;
        }

        if (request.TagCodes != null)
        {
            // Remove old assignments
            _dbContext.TagAssignments.RemoveRange(feedback.TagAssignments);
            var allowedRole = feedback.ReviewerRole == FeedbackRole.RIDER ? TagTargetRole.DRIVER : TagTargetRole.RIDER;
            var newTags = await ResolveTagsAsync(request.TagCodes, allowedRole);
            foreach (var t in newTags)
            {
                _dbContext.TagAssignments.Add(new FeedbackTagAssignment
                {
                    FeedbackId = feedback.Id,
                    TagId = t.Id,
                    CreatedAt = DateTime.UtcNow
                });
            }
        }

        feedback.IsEdited = true;
        feedback.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();

        if (ratingChanged)
        {
            await _ratingCalculationService.UpdateRatingSummaryOnRatingEditAsync(feedback, oldRating);
        }

        await _dbContext.Entry(feedback)
            .Collection(f => f.TagAssignments)
            .Query()
            .Include(a => a.Tag)
            .LoadAsync();

        return FeedbackResponse.FromEntity(feedback);
    }

    public async Task<PageResponse<FeedbackResponse>> GetDriverReceivedFeedbackAsync(ClaimsPrincipal driver, int page, int size)
    {
        var driverUuid = GetUserId(driver);
        var query = _dbContext.Feedbacks
            .Include(f => f.TagAssignments)
            .ThenInclude(a => a.Tag)
            .Where(f => f.TargetUuid == driverUuid && f.ModerationStatus == ModerationStatus.VISIBLE);

        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(f => f.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var dtos = items.Select(FeedbackResponse.FromEntity).ToList();
        return PageResponse<FeedbackResponse>.Create(dtos, page, size, total);
    }

    public async Task<PageResponse<FeedbackResponse>> GetAdminFeedbackAsync(
        string? reviewerUuid, string? targetUuid, ModerationStatus? status, int page, int size)
    {
        var query = _dbContext.Feedbacks
            .Include(f => f.TagAssignments)
            .ThenInclude(a => a.Tag)
            .AsQueryable();

        if (!string.IsNullOrWhiteSpace(reviewerUuid))
            query = query.Where(f => f.ReviewerUuid == reviewerUuid);

        if (!string.IsNullOrWhiteSpace(targetUuid))
            query = query.Where(f => f.TargetUuid == targetUuid);

        if (status.HasValue)
            query = query.Where(f => f.ModerationStatus == status.Value);

        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(f => f.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        var dtos = items.Select(FeedbackResponse.FromEntity).ToList();
        return PageResponse<FeedbackResponse>.Create(dtos, page, size, total);
    }

    public async Task<FeedbackResponse> GetAdminFeedbackByUuidAsync(string reviewUuid)
    {
        var feedback = await _dbContext.Feedbacks
            .Include(f => f.TagAssignments)
            .ThenInclude(a => a.Tag)
            .FirstOrDefaultAsync(f => f.FeedbackUuid == reviewUuid);

        if (feedback == null)
            throw new ResourceNotFoundException($"Review not found with UUID: {reviewUuid}");

        return FeedbackResponse.FromEntity(feedback);
    }

    public async Task<List<FeedbackTagDto>> GetFeedbackTagsAsync(string? targetRole = null)
    {
        var query = _dbContext.FeedbackTags.Where(t => t.IsActive);

        if (!string.IsNullOrWhiteSpace(targetRole))
        {
            if (Enum.TryParse<TagTargetRole>(targetRole, true, out var role))
            {
                query = query.Where(t => t.TargetRole == role || t.TargetRole == TagTargetRole.BOTH);
            }
        }

        var list = await query.OrderBy(t => t.DisplayOrder).ToListAsync();
        return list.Select(FeedbackTagDto.FromEntity).ToList();
    }

    private async Task<List<FeedbackTag>> ResolveTagsAsync(List<string>? tagCodes, TagTargetRole targetRole)
    {
        if (tagCodes == null || tagCodes.Count == 0) return new List<FeedbackTag>();

        var matched = await _dbContext.FeedbackTags
            .Where(t => tagCodes.Contains(t.TagCode) && t.IsActive)
            .ToListAsync();

        return matched
            .Where(t => t.TargetRole == targetRole || t.TargetRole == TagTargetRole.BOTH)
            .ToList();
    }
}
