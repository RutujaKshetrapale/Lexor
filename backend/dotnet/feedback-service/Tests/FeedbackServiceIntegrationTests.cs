using System.Security.Claims;
using FeedbackService.Clients;
using FeedbackService.Configuration;
using FeedbackService.Data;
using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;
using FeedbackService.DTOs;
using FeedbackService.Exceptions;
using FeedbackService.Services;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace FeedbackService.Tests;

public class FeedbackServiceIntegrationTests
{
    private readonly string _riderUuid = "11111111-1111-4111-8111-111111111111";
    private readonly string _driverUuid = "22222222-2222-4222-8222-222222222222";
    private readonly string _otherRiderUuid = "33333333-3333-4333-8333-333333333333";
    private readonly string _adminUuid = "99999999-9999-4999-8999-999999999999";

    private ClaimsPrincipal CreatePrincipal(string uuid, string role)
    {
        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, uuid),
            new("sub", uuid),
            new("uuid", uuid),
            new("role", role),
            new(ClaimTypes.Role, role)
        };
        var identity = new ClaimsIdentity(claims, "TestAuth");
        return new ClaimsPrincipal(identity);
    }

    private (FeedbackService.Services.FeedbackService feedbackService,
            RatingCalculationService calcService,
            ModerationService modService,
            BookingVerificationClient bookingClient,
            FeedbackDbContext dbContext) CreateTestServices(string dbName)
    {
        var db = TestDbContextFactory.Create(dbName);

        // Seed tags if empty
        if (!db.FeedbackTags.Any())
        {
            db.FeedbackTags.AddRange(
                new FeedbackTag { TagCode = "FRIENDLY_DRIVER", Label = "Friendly driver", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.POSITIVE, DisplayOrder = 1 },
                new FeedbackTag { TagCode = "SAFE_DRIVING", Label = "Safe driving", TargetRole = TagTargetRole.DRIVER, Category = TagCategory.POSITIVE, DisplayOrder = 2 },
                new FeedbackTag { TagCode = "RESPECTFUL", Label = "Respectful", TargetRole = TagTargetRole.RIDER, Category = TagCategory.POSITIVE, DisplayOrder = 3 },
                new FeedbackTag { TagCode = "PUNCTUAL_PICKUP", Label = "Punctual at pickup", TargetRole = TagTargetRole.RIDER, Category = TagCategory.POSITIVE, DisplayOrder = 4 }
            );
            db.SaveChanges();
        }

        var settings = Options.Create(new FeedbackSettings
        {
            EditWindowMinutes = 120,
            SubmissionWindowDays = 7,
            EditEnabled = true,
            ReportAutoReviewThreshold = 3
        });

        var bookingClient = new BookingVerificationClient(new HttpClient(), settings, NullLogger<BookingVerificationClient>.Instance);
        var notifMock = new Mock<INotificationClient>();
        notifMock.Setup(n => n.SendNotificationAsync(It.IsAny<DirectNotificationRequest>())).Returns(Task.CompletedTask);

        var calcService = new RatingCalculationService(db, NullLogger<RatingCalculationService>.Instance);
        var modService = new ModerationService(db, calcService, settings, NullLogger<ModerationService>.Instance);
        var feedbackService = new FeedbackService.Services.FeedbackService(
            db, bookingClient, notifMock.Object, calcService, settings, NullLogger<FeedbackService.Services.FeedbackService>.Instance);

        return (feedbackService, calcService, modService, bookingClient, db);
    }

    // =========================================================================
    // 1. ELIGIBILITY TESTS
    // =========================================================================

    [Fact]
    public async Task SubmitRiderFeedback_EligibleCompletedRide_ReturnsCreated()
    {
        var (feedbackService, _, _, bookingClient, _) = CreateTestServices(nameof(SubmitRiderFeedback_EligibleCompletedRide_ReturnsCreated));
        var rideUuid = Guid.NewGuid().ToString();

        bookingClient.RegisterMockRide(new RideEligibilityDto
        {
            RideUuid = rideUuid,
            BookingUuid = rideUuid,
            Status = "COMPLETED",
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            CompletedAt = DateTime.UtcNow.AddHours(-1)
        });

        var principal = CreatePrincipal(_riderUuid, "RIDER");
        var req = new SubmitRatingRequest
        {
            Rating = 5,
            ReviewText = "Smooth ride and polite driver",
            TagCodes = new List<string> { "FRIENDLY_DRIVER", "SAFE_DRIVING" }
        };

        var response = await feedbackService.SubmitRiderFeedbackAsync(principal, rideUuid, req);

        Assert.NotNull(response);
        Assert.Equal(5, response.Rating);
        Assert.Equal(FeedbackRole.RIDER, response.ReviewerRole);
        Assert.Equal(FeedbackRole.DRIVER, response.TargetRole);
        Assert.Equal(ModerationStatus.VISIBLE, response.ModerationStatus);
        Assert.Equal(2, response.Tags.Count);
    }

    [Fact]
    public async Task SubmitDriverFeedback_EligibleCompletedRide_ReturnsCreated()
    {
        var (feedbackService, _, _, bookingClient, _) = CreateTestServices(nameof(SubmitDriverFeedback_EligibleCompletedRide_ReturnsCreated));
        var rideUuid = Guid.NewGuid().ToString();

        bookingClient.RegisterMockRide(new RideEligibilityDto
        {
            RideUuid = rideUuid,
            BookingUuid = rideUuid,
            Status = "COMPLETED",
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            CompletedAt = DateTime.UtcNow.AddHours(-1)
        });

        var principal = CreatePrincipal(_driverUuid, "DRIVER");
        var req = new SubmitRatingRequest
        {
            Rating = 5,
            ReviewText = "Respectful passenger, prompt at pickup.",
            TagCodes = new List<string> { "RESPECTFUL", "PUNCTUAL_PICKUP" }
        };

        var response = await feedbackService.SubmitDriverFeedbackAsync(principal, rideUuid, req);

        Assert.NotNull(response);
        Assert.Equal(5, response.Rating);
        Assert.Equal(FeedbackRole.DRIVER, response.ReviewerRole);
        Assert.Equal(FeedbackRole.RIDER, response.TargetRole);
    }

    [Fact]
    public async Task SubmitRiderFeedback_NonParticipant_ThrowsForbiddenException()
    {
        var (feedbackService, _, _, bookingClient, _) = CreateTestServices(nameof(SubmitRiderFeedback_NonParticipant_ThrowsForbiddenException));
        var rideUuid = Guid.NewGuid().ToString();

        bookingClient.RegisterMockRide(new RideEligibilityDto
        {
            RideUuid = rideUuid,
            Status = "COMPLETED",
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid
        });

        var otherPrincipal = CreatePrincipal(_otherRiderUuid, "RIDER");
        var req = new SubmitRatingRequest { Rating = 4 };

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            feedbackService.SubmitRiderFeedbackAsync(otherPrincipal, rideUuid, req));
    }

    [Fact]
    public async Task SubmitRiderFeedback_CancelledRide_ThrowsIneligibleFeedbackException()
    {
        var (feedbackService, _, _, bookingClient, _) = CreateTestServices(nameof(SubmitRiderFeedback_CancelledRide_ThrowsIneligibleFeedbackException));
        var rideUuid = Guid.NewGuid().ToString();

        bookingClient.RegisterMockRide(new RideEligibilityDto
        {
            RideUuid = rideUuid,
            Status = "CANCELLED",
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid
        });

        var principal = CreatePrincipal(_riderUuid, "RIDER");
        var req = new SubmitRatingRequest { Rating = 5 };

        await Assert.ThrowsAsync<IneligibleFeedbackException>(() =>
            feedbackService.SubmitRiderFeedbackAsync(principal, rideUuid, req));
    }

    [Fact]
    public async Task SubmitRiderFeedback_InProgressRide_ThrowsIneligibleFeedbackException()
    {
        var (feedbackService, _, _, bookingClient, _) = CreateTestServices(nameof(SubmitRiderFeedback_InProgressRide_ThrowsIneligibleFeedbackException));
        var rideUuid = Guid.NewGuid().ToString();

        bookingClient.RegisterMockRide(new RideEligibilityDto
        {
            RideUuid = rideUuid,
            Status = "IN_PROGRESS",
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid
        });

        var principal = CreatePrincipal(_riderUuid, "RIDER");
        var req = new SubmitRatingRequest { Rating = 5 };

        await Assert.ThrowsAsync<IneligibleFeedbackException>(() =>
            feedbackService.SubmitRiderFeedbackAsync(principal, rideUuid, req));
    }

    [Fact]
    public async Task SubmitRiderFeedback_NonExistentRide_ThrowsResourceNotFoundException()
    {
        var (feedbackService, _, _, _, _) = CreateTestServices(nameof(SubmitRiderFeedback_NonExistentRide_ThrowsResourceNotFoundException));
        var principal = CreatePrincipal(_riderUuid, "RIDER");
        var req = new SubmitRatingRequest { Rating = 5 };

        await Assert.ThrowsAsync<ResourceNotFoundException>(() =>
            feedbackService.SubmitRiderFeedbackAsync(principal, Guid.NewGuid().ToString(), req));
    }

    [Fact]
    public async Task SubmitRiderFeedback_PastDeadline_ThrowsIneligibleFeedbackException()
    {
        var (feedbackService, _, _, bookingClient, _) = CreateTestServices(nameof(SubmitRiderFeedback_PastDeadline_ThrowsIneligibleFeedbackException));
        var rideUuid = Guid.NewGuid().ToString();

        bookingClient.RegisterMockRide(new RideEligibilityDto
        {
            RideUuid = rideUuid,
            Status = "COMPLETED",
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            CompletedAt = DateTime.UtcNow.AddDays(-10) // past 7 days deadline
        });

        var principal = CreatePrincipal(_riderUuid, "RIDER");
        var req = new SubmitRatingRequest { Rating = 5 };

        await Assert.ThrowsAsync<IneligibleFeedbackException>(() =>
            feedbackService.SubmitRiderFeedbackAsync(principal, rideUuid, req));
    }

    // =========================================================================
    // 2. DUPLICATE SUBMISSION TESTS
    // =========================================================================

    [Fact]
    public async Task SubmitRiderFeedback_DuplicateSubmission_ThrowsConflictException()
    {
        var (feedbackService, _, _, bookingClient, _) = CreateTestServices(nameof(SubmitRiderFeedback_DuplicateSubmission_ThrowsConflictException));
        var rideUuid = Guid.NewGuid().ToString();

        bookingClient.RegisterMockRide(new RideEligibilityDto
        {
            RideUuid = rideUuid,
            Status = "COMPLETED",
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            CompletedAt = DateTime.UtcNow.AddHours(-1)
        });

        var principal = CreatePrincipal(_riderUuid, "RIDER");
        var req = new SubmitRatingRequest { Rating = 5 };

        // 1st submission
        await feedbackService.SubmitRiderFeedbackAsync(principal, rideUuid, req);

        // 2nd submission throws ConflictException
        await Assert.ThrowsAsync<ConflictException>(() =>
            feedbackService.SubmitRiderFeedbackAsync(principal, rideUuid, req));
    }

    // =========================================================================
    // 3. RATING CALCULATION TESTS
    // =========================================================================

    [Fact]
    public async Task DriverRatingCalculation_ThreeRatings_CalculatesCorrectAverageAndBreakdown()
    {
        var (_, calcService, _, _, db) = CreateTestServices(nameof(DriverRatingCalculation_ThreeRatings_CalculatesCorrectAverageAndBreakdown));

        db.Feedbacks.AddRange(
            new RideFeedback { FeedbackUuid = Guid.NewGuid().ToString(), RideUuid = Guid.NewGuid().ToString(), ReviewerUuid = Guid.NewGuid().ToString(), TargetUuid = _driverUuid, ReviewerRole = FeedbackRole.RIDER, TargetRole = FeedbackRole.DRIVER, Rating = 5, ModerationStatus = ModerationStatus.VISIBLE },
            new RideFeedback { FeedbackUuid = Guid.NewGuid().ToString(), RideUuid = Guid.NewGuid().ToString(), ReviewerUuid = Guid.NewGuid().ToString(), TargetUuid = _driverUuid, ReviewerRole = FeedbackRole.RIDER, TargetRole = FeedbackRole.DRIVER, Rating = 4, ModerationStatus = ModerationStatus.VISIBLE },
            new RideFeedback { FeedbackUuid = Guid.NewGuid().ToString(), RideUuid = Guid.NewGuid().ToString(), ReviewerUuid = Guid.NewGuid().ToString(), TargetUuid = _driverUuid, ReviewerRole = FeedbackRole.RIDER, TargetRole = FeedbackRole.DRIVER, Rating = 4, ModerationStatus = ModerationStatus.VISIBLE }
        );
        await db.SaveChangesAsync();

        await calcService.RecalculateSummaryForTargetAsync(_driverUuid, FeedbackRole.DRIVER);

        var summary = await calcService.GetDriverSummaryAsync(_driverUuid);
        Assert.Equal(3, summary.TotalRatings);
        Assert.Equal(4.33m, summary.AverageRating);
        Assert.Equal(1, summary.RatingDistribution[5]);
        Assert.Equal(2, summary.RatingDistribution[4]);
        Assert.Equal(0, summary.RatingDistribution[3]);
    }

    [Fact]
    public async Task RecalculateOnModerationChange_HiddenRating_AdjustsAverageAndExcludedCount()
    {
        var (_, calcService, _, _, db) = CreateTestServices(nameof(RecalculateOnModerationChange_HiddenRating_AdjustsAverageAndExcludedCount));

        var f1 = new RideFeedback { FeedbackUuid = Guid.NewGuid().ToString(), RideUuid = Guid.NewGuid().ToString(), ReviewerUuid = Guid.NewGuid().ToString(), TargetUuid = _driverUuid, ReviewerRole = FeedbackRole.RIDER, TargetRole = FeedbackRole.DRIVER, Rating = 1, ModerationStatus = ModerationStatus.VISIBLE };
        var f2 = new RideFeedback { FeedbackUuid = Guid.NewGuid().ToString(), RideUuid = Guid.NewGuid().ToString(), ReviewerUuid = Guid.NewGuid().ToString(), TargetUuid = _driverUuid, ReviewerRole = FeedbackRole.RIDER, TargetRole = FeedbackRole.DRIVER, Rating = 5, ModerationStatus = ModerationStatus.VISIBLE };
        db.Feedbacks.AddRange(f1, f2);
        await db.SaveChangesAsync();

        await calcService.RecalculateSummaryForTargetAsync(_driverUuid, FeedbackRole.DRIVER);
        var before = await calcService.GetDriverSummaryAsync(_driverUuid);
        Assert.Equal(3.00m, before.AverageRating);
        Assert.Equal(2, before.TotalRatings);

        // Hide f1
        f1.ModerationStatus = ModerationStatus.HIDDEN;
        await db.SaveChangesAsync();

        await calcService.RecalculateSummaryForTargetAsync(_driverUuid, FeedbackRole.DRIVER);
        var after = await calcService.GetDriverSummaryAsync(_driverUuid);
        Assert.Equal(5.00m, after.AverageRating);
        Assert.Equal(1, after.TotalRatings);
        Assert.Equal(1, after.ExcludedRatingsCount);
    }

    // =========================================================================
    // 4. REVIEW EDITING TESTS
    // =========================================================================

    [Fact]
    public async Task UpdateMyReview_WithinEditWindow_Succeeds()
    {
        var (feedbackService, _, _, _, db) = CreateTestServices(nameof(UpdateMyReview_WithinEditWindow_Succeeds));

        var feedback = new RideFeedback
        {
            FeedbackUuid = Guid.NewGuid().ToString(),
            RideUuid = Guid.NewGuid().ToString(),
            ReviewerUuid = _riderUuid,
            TargetUuid = _driverUuid,
            ReviewerRole = FeedbackRole.RIDER,
            TargetRole = FeedbackRole.DRIVER,
            Rating = 3,
            ReviewText = "Initial comment",
            EditableUntil = DateTime.UtcNow.AddHours(1),
            ModerationStatus = ModerationStatus.VISIBLE
        };
        db.Feedbacks.Add(feedback);
        await db.SaveChangesAsync();

        var principal = CreatePrincipal(_riderUuid, "RIDER");
        var updateReq = new UpdateRatingRequest
        {
            Rating = 5,
            ReviewText = "Updated to 5 stars!"
        };

        var response = await feedbackService.UpdateMyReviewAsync(principal, feedback.FeedbackUuid, updateReq);

        Assert.Equal(5, response.Rating);
        Assert.True(response.IsEdited);
        Assert.Equal("Updated to 5 stars!", response.ReviewText);
    }

    [Fact]
    public async Task UpdateMyReview_ExpiredEditWindow_ThrowsBadRequestException()
    {
        var (feedbackService, _, _, _, db) = CreateTestServices(nameof(UpdateMyReview_ExpiredEditWindow_ThrowsBadRequestException));

        var feedback = new RideFeedback
        {
            FeedbackUuid = Guid.NewGuid().ToString(),
            RideUuid = Guid.NewGuid().ToString(),
            ReviewerUuid = _riderUuid,
            TargetUuid = _driverUuid,
            ReviewerRole = FeedbackRole.RIDER,
            TargetRole = FeedbackRole.DRIVER,
            Rating = 4,
            EditableUntil = DateTime.UtcNow.AddMinutes(-5),
            ModerationStatus = ModerationStatus.VISIBLE
        };
        db.Feedbacks.Add(feedback);
        await db.SaveChangesAsync();

        var principal = CreatePrincipal(_riderUuid, "RIDER");
        var updateReq = new UpdateRatingRequest { Rating = 5 };

        await Assert.ThrowsAsync<BadRequestException>(() =>
            feedbackService.UpdateMyReviewAsync(principal, feedback.FeedbackUuid, updateReq));
    }

    // =========================================================================
    // 5. MODERATION AND REPORTING TESTS
    // =========================================================================

    [Fact]
    public async Task ReportReview_ValidAndDuplicate_BehavesCorrectly()
    {
        var (_, _, modService, _, db) = CreateTestServices(nameof(ReportReview_ValidAndDuplicate_BehavesCorrectly));

        var feedback = new RideFeedback
        {
            FeedbackUuid = Guid.NewGuid().ToString(),
            RideUuid = Guid.NewGuid().ToString(),
            ReviewerUuid = _riderUuid,
            TargetUuid = _driverUuid,
            ReviewerRole = FeedbackRole.RIDER,
            TargetRole = FeedbackRole.DRIVER,
            Rating = 1,
            ReviewText = "Harassment comment",
            ModerationStatus = ModerationStatus.VISIBLE
        };
        db.Feedbacks.Add(feedback);
        await db.SaveChangesAsync();

        var reporter = CreatePrincipal(_driverUuid, "DRIVER");
        var req = new ReportReviewRequest
        {
            Reason = ReportReason.HARASSMENT,
            Comments = "Offensive and abusive"
        };

        var reportRes = await modService.ReportReviewAsync(reporter, feedback.FeedbackUuid, req);
        Assert.NotNull(reportRes);
        Assert.Equal(ReportReason.HARASSMENT, reportRes.Reason);

        // Duplicate report by same user fails with ConflictException
        await Assert.ThrowsAsync<ConflictException>(() =>
            modService.ReportReviewAsync(reporter, feedback.FeedbackUuid, req));
    }

    [Fact]
    public async Task ApplyModerationAction_ValidAdminAction_UpdatesStatusAndAudits()
    {
        var (_, _, modService, _, db) = CreateTestServices(nameof(ApplyModerationAction_ValidAdminAction_UpdatesStatusAndAudits));

        var feedback = new RideFeedback
        {
            FeedbackUuid = Guid.NewGuid().ToString(),
            RideUuid = Guid.NewGuid().ToString(),
            ReviewerUuid = _riderUuid,
            TargetUuid = _driverUuid,
            ReviewerRole = FeedbackRole.RIDER,
            TargetRole = FeedbackRole.DRIVER,
            Rating = 1,
            ModerationStatus = ModerationStatus.VISIBLE
        };
        db.Feedbacks.Add(feedback);
        await db.SaveChangesAsync();

        var adminPrincipal = CreatePrincipal(_adminUuid, "ADMIN");
        var actionReq = new ModerationActionRequest
        {
            NewStatus = ModerationStatus.HIDDEN,
            Reason = "Confirmed harassment in review"
        };

        var modRes = await modService.ApplyModerationActionAsync(adminPrincipal, feedback.FeedbackUuid, actionReq);
        Assert.Equal(ModerationStatus.HIDDEN, modRes.ModerationStatus);

        var history = await modService.GetModerationHistoryAsync(feedback.FeedbackUuid);
        Assert.Single(history);
        Assert.Equal(ModerationStatus.HIDDEN, history[0].NewStatus);
        Assert.Equal("Confirmed harassment in review", history[0].Reason);
    }

    [Fact]
    public async Task ApplyModerationAction_InvalidTransition_ThrowsBadRequestException()
    {
        var (_, _, modService, _, db) = CreateTestServices(nameof(ApplyModerationAction_InvalidTransition_ThrowsBadRequestException));

        var feedback = new RideFeedback
        {
            FeedbackUuid = Guid.NewGuid().ToString(),
            RideUuid = Guid.NewGuid().ToString(),
            ReviewerUuid = _riderUuid,
            TargetUuid = _driverUuid,
            ReviewerRole = FeedbackRole.RIDER,
            TargetRole = FeedbackRole.DRIVER,
            Rating = 1,
            ModerationStatus = ModerationStatus.VISIBLE
        };
        db.Feedbacks.Add(feedback);
        await db.SaveChangesAsync();

        var adminPrincipal = CreatePrincipal(_adminUuid, "ADMIN");
        var sameStatusReq = new ModerationActionRequest
        {
            NewStatus = ModerationStatus.VISIBLE,
            Reason = "No change"
        };

        await Assert.ThrowsAsync<BadRequestException>(() =>
            modService.ApplyModerationActionAsync(adminPrincipal, feedback.FeedbackUuid, sameStatusReq));
    }

    // =========================================================================
    // 6. TAGS AND PUBLIC SUMMARY TESTS
    // =========================================================================

    [Fact]
    public async Task GetFeedbackTags_ReturnsActiveTags()
    {
        var (feedbackService, _, _, _, _) = CreateTestServices(nameof(GetFeedbackTags_ReturnsActiveTags));

        var tags = await feedbackService.GetFeedbackTagsAsync("DRIVER");
        Assert.NotEmpty(tags);
    }

    [Fact]
    public async Task GetDriverSummary_NewDriver_ReturnsDefaultFiveStars()
    {
        var (_, calcService, _, _, _) = CreateTestServices(nameof(GetDriverSummary_NewDriver_ReturnsDefaultFiveStars));
        var newDriverUuid = Guid.NewGuid().ToString();

        var summary = await calcService.GetDriverSummaryAsync(newDriverUuid);
        Assert.Equal(newDriverUuid, summary.DriverUuid);
        Assert.Equal(5.00m, summary.AverageRating);
        Assert.Equal(0, summary.TotalRatings);
    }
}
