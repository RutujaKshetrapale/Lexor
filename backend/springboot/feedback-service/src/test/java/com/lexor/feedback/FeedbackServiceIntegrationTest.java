package com.lexor.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.feedback.client.IBookingVerificationClient;
import com.lexor.feedback.dto.client.RideEligibilityDto;
import com.lexor.feedback.dto.request.ModerationActionRequest;
import com.lexor.feedback.dto.request.ReportReviewRequest;
import com.lexor.feedback.dto.request.SubmitRatingRequest;
import com.lexor.feedback.dto.request.UpdateRatingRequest;
import com.lexor.feedback.dto.response.DriverRatingSummaryResponse;
import com.lexor.feedback.entity.FeedbackTag;
import com.lexor.feedback.entity.RideFeedback;
import com.lexor.feedback.enums.FeedbackRole;
import com.lexor.feedback.enums.ModerationStatus;
import com.lexor.feedback.enums.ReportReason;
import com.lexor.feedback.repository.FeedbackReportRepository;
import com.lexor.feedback.repository.FeedbackTagRepository;
import com.lexor.feedback.repository.RideFeedbackRepository;
import com.lexor.feedback.security.JwtTokenProvider;
import com.lexor.feedback.service.IRatingCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FeedbackServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private IBookingVerificationClient bookingClient;

    @Autowired
    private RideFeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackReportRepository reportRepository;

    @Autowired
    private com.lexor.feedback.repository.FeedbackModerationHistoryRepository historyRepository;

    @Autowired
    private FeedbackTagRepository tagRepository;

    @Autowired
    private IRatingCalculationService ratingCalculationService;

    private String riderUuid;
    private String driverUuid;
    private String otherRiderUuid;
    private String adminUuid;

    private String riderToken;
    private String driverToken;
    private String otherRiderToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        reportRepository.deleteAll();
        feedbackRepository.deleteAll();
        bookingClient.clearMockRides();

        riderUuid = "11111111-1111-4111-8111-111111111111";
        driverUuid = "22222222-2222-4222-8222-222222222222";
        otherRiderUuid = "33333333-3333-4333-8333-333333333333";
        adminUuid = "99999999-9999-4999-8999-999999999999";

        riderToken = "Bearer " + jwtTokenProvider.generateToken(riderUuid, 1L, "rider@lexor.com", "ROLE_RIDER");
        driverToken = "Bearer " + jwtTokenProvider.generateToken(driverUuid, 2L, "driver@lexor.com", "ROLE_DRIVER");
        otherRiderToken = "Bearer " + jwtTokenProvider.generateToken(otherRiderUuid, 3L, "other@lexor.com", "ROLE_RIDER");
        adminToken = "Bearer " + jwtTokenProvider.generateToken(adminUuid, 99L, "admin@lexor.com", "ROLE_ADMIN");
    }

    // =========================================================================
    // 1. ELIGIBILITY TESTS
    // =========================================================================

    @Test
    @DisplayName("Eligibility: Rider can rate assigned driver for a completed ride")
    void testRiderRatesDriverCompletedRide() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        bookingClient.registerMockRide(RideEligibilityDto.builder()
                .rideUuid(rideUuid)
                .bookingUuid(rideUuid)
                .status("COMPLETED")
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .completedAt(LocalDateTime.now().minusHours(1))
                .build());

        SubmitRatingRequest req = SubmitRatingRequest.builder()
                .rating(5)
                .reviewText("Excellent journey, friendly and punctual!")
                .tagCodes(List.of("FRIENDLY_DRIVER", "SAFE_DRIVING"))
                .build();

        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.rating", is(5)))
                .andExpect(jsonPath("$.data.reviewerRole", is("RIDER")))
                .andExpect(jsonPath("$.data.targetRole", is("DRIVER")))
                .andExpect(jsonPath("$.data.moderationStatus", is("VISIBLE")));
    }

    @Test
    @DisplayName("Eligibility: Driver can rate rider for a completed ride")
    void testDriverRatesRiderCompletedRide() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        bookingClient.registerMockRide(RideEligibilityDto.builder()
                .rideUuid(rideUuid)
                .bookingUuid(rideUuid)
                .status("COMPLETED")
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .completedAt(LocalDateTime.now().minusHours(1))
                .build());

        SubmitRatingRequest req = SubmitRatingRequest.builder()
                .rating(5)
                .reviewText("Polite passenger, on time at pickup point.")
                .tagCodes(List.of("RESPECTFUL", "PUNCTUAL_PICKUP"))
                .build();

        mockMvc.perform(post("/api/v1/drivers/me/rides/{rideUuid}/rider-rating", rideUuid)
                        .header("Authorization", driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.rating", is(5)))
                .andExpect(jsonPath("$.data.reviewerRole", is("DRIVER")))
                .andExpect(jsonPath("$.data.targetRole", is("RIDER")));
    }

    @Test
    @DisplayName("Eligibility: Non-participant rider cannot review ride")
    void testNonParticipantRiderCannotReview() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        bookingClient.registerMockRide(RideEligibilityDto.builder()
                .rideUuid(rideUuid)
                .status("COMPLETED")
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .completedAt(LocalDateTime.now().minusHours(1))
                .build());

        SubmitRatingRequest req = SubmitRatingRequest.builder().rating(4).build();

        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", otherRiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Eligibility: Cancelled ride cannot be reviewed")
    void testCancelledRideIneligible() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        bookingClient.registerMockRide(RideEligibilityDto.builder()
                .rideUuid(rideUuid)
                .status("CANCELLED")
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .build());

        SubmitRatingRequest req = SubmitRatingRequest.builder().rating(5).build();

        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Eligibility: In-progress ride cannot be reviewed")
    void testInProgressRideIneligible() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        bookingClient.registerMockRide(RideEligibilityDto.builder()
                .rideUuid(rideUuid)
                .status("IN_PROGRESS")
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .build());

        SubmitRatingRequest req = SubmitRatingRequest.builder().rating(5).build();

        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Eligibility: Nonexistent ride returns 404")
    void testNonExistentRideReturns404() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        SubmitRatingRequest req = SubmitRatingRequest.builder().rating(5).build();

        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Eligibility: Submission past deadline is rejected")
    void testReviewPastDeadlineRejected() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        bookingClient.registerMockRide(RideEligibilityDto.builder()
                .rideUuid(rideUuid)
                .status("COMPLETED")
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .completedAt(LocalDateTime.now().minusDays(10)) // past 7 days deadline
                .build());

        SubmitRatingRequest req = SubmitRatingRequest.builder().rating(5).build();

        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 2. DUPLICATE AND CONCURRENCY TESTS
    // =========================================================================

    @Test
    @DisplayName("Duplicate: Submitting duplicate review returns 409 Conflict")
    void testDuplicateReviewReturnsConflict() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        bookingClient.registerMockRide(RideEligibilityDto.builder()
                .rideUuid(rideUuid)
                .status("COMPLETED")
                .riderUuid(riderUuid)
                .driverUuid(driverUuid)
                .completedAt(LocalDateTime.now().minusHours(1))
                .build());

        SubmitRatingRequest req = SubmitRatingRequest.builder().rating(5).build();

        // First submission succeeds
        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second submission fails with 409 Conflict
        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // 3. RATING CALCULATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Calculation: Driver average rating and breakdown calculated correctly")
    void testDriverRatingCalculationAndRounding() {
        // Submit 3 reviews: 5 stars, 4 stars, 4 stars -> Sum = 13, Avg = 13 / 3 = 4.33
        RideFeedback f1 = RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(UUID.randomUUID().toString())
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(5)
                .moderationStatus(ModerationStatus.VISIBLE)
                .build();
        feedbackRepository.save(f1);

        RideFeedback f2 = RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(UUID.randomUUID().toString())
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(4)
                .moderationStatus(ModerationStatus.VISIBLE)
                .build();
        feedbackRepository.save(f2);

        RideFeedback f3 = RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(UUID.randomUUID().toString())
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(4)
                .moderationStatus(ModerationStatus.VISIBLE)
                .build();
        feedbackRepository.save(f3);

        ratingCalculationService.recalculateSummaryForTarget(driverUuid, FeedbackRole.DRIVER);

        DriverRatingSummaryResponse summary = ratingCalculationService.getDriverSummary(driverUuid);
        assertNotNull(summary);
        assertEquals(3, summary.getTotalRatings());
        assertEquals("4.33", summary.getAverageRating().toString());
        assertEquals(1, summary.getRatingDistribution().get(5));
        assertEquals(2, summary.getRatingDistribution().get(4));
        assertEquals(0, summary.getRatingDistribution().get(3));
    }

    @Test
    @DisplayName("Calculation: Recalculation adjusts average when review is hidden by moderation")
    void testRecalculationOnModerationStatusChange() {
        RideFeedback f1 = feedbackRepository.save(RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(UUID.randomUUID().toString())
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(1) // 1-star
                .moderationStatus(ModerationStatus.VISIBLE)
                .build());

        RideFeedback f2 = feedbackRepository.save(RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(UUID.randomUUID().toString())
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(5) // 5-star
                .moderationStatus(ModerationStatus.VISIBLE)
                .build());

        ratingCalculationService.recalculateSummaryForTarget(driverUuid, FeedbackRole.DRIVER);
        DriverRatingSummaryResponse summaryBefore = ratingCalculationService.getDriverSummary(driverUuid);
        assertEquals("3.00", summaryBefore.getAverageRating().toString());
        assertEquals(2, summaryBefore.getTotalRatings());

        // Hide the 1-star review
        f1.setModerationStatus(ModerationStatus.HIDDEN);
        feedbackRepository.save(f1);

        ratingCalculationService.recalculateSummaryForTarget(driverUuid, FeedbackRole.DRIVER);
        DriverRatingSummaryResponse summaryAfter = ratingCalculationService.getDriverSummary(driverUuid);
        assertEquals("5.00", summaryAfter.getAverageRating().toString());
        assertEquals(1, summaryAfter.getTotalRatings());
        assertEquals(1, summaryAfter.getExcludedRatingsCount());
    }

    // =========================================================================
    // 4. REVIEW EDITING TESTS
    // =========================================================================

    @Test
    @DisplayName("Editing: User can update rating and comments within edit window")
    void testUserCanUpdateReviewWithinWindow() throws Exception {
        RideFeedback feedback = feedbackRepository.save(RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(riderUuid)
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(3)
                .reviewText("Average ride")
                .editableUntil(LocalDateTime.now().plusHours(1))
                .moderationStatus(ModerationStatus.VISIBLE)
                .build());

        UpdateRatingRequest updateReq = UpdateRatingRequest.builder()
                .rating(5)
                .reviewText("Driver corrected route, updated to 5 stars!")
                .build();

        mockMvc.perform(patch("/api/v1/feedback/me/reviews/{reviewUuid}", feedback.getFeedbackUuid())
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating", is(5)))
                .andExpect(jsonPath("$.data.isEdited", is(true)));
    }

    @Test
    @DisplayName("Editing: Update rejected after edit window expiration")
    void testUpdateRejectedAfterEditWindowExpired() throws Exception {
        RideFeedback feedback = feedbackRepository.save(RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(riderUuid)
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(4)
                .editableUntil(LocalDateTime.now().minusMinutes(5)) // Expired
                .moderationStatus(ModerationStatus.VISIBLE)
                .build());

        UpdateRatingRequest updateReq = UpdateRatingRequest.builder().rating(5).build();

        mockMvc.perform(patch("/api/v1/feedback/me/reviews/{reviewUuid}", feedback.getFeedbackUuid())
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 5. MODERATION AND REPORTING TESTS
    // =========================================================================

    @Test
    @DisplayName("Moderation: Reporting review succeeds and duplicate report is rejected")
    void testReportReviewAndDuplicateRejection() throws Exception {
        RideFeedback feedback = feedbackRepository.save(RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(riderUuid)
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(1)
                .reviewText("Spam comment with abusive words")
                .moderationStatus(ModerationStatus.VISIBLE)
                .build());

        ReportReviewRequest reportReq = ReportReviewRequest.builder()
                .reason(ReportReason.OFFENSIVE_CONTENT)
                .comments("Abusive language used.")
                .build();

        // First report succeeds
        mockMvc.perform(post("/api/v1/feedback/reviews/{reviewUuid}/report", feedback.getFeedbackUuid())
                        .header("Authorization", driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)));

        // Duplicate report by same user is rejected with 409 Conflict
        mockMvc.perform(post("/api/v1/feedback/reviews/{reviewUuid}/report", feedback.getFeedbackUuid())
                        .header("Authorization", driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Moderation: Admin can execute moderation action with audit reason")
    void testAdminModerationAction() throws Exception {
        RideFeedback feedback = feedbackRepository.save(RideFeedback.builder()
                .feedbackUuid(UUID.randomUUID().toString())
                .rideUuid(UUID.randomUUID().toString())
                .reviewerUuid(riderUuid)
                .targetUuid(driverUuid)
                .reviewerRole(FeedbackRole.RIDER)
                .targetRole(FeedbackRole.DRIVER)
                .rating(1)
                .moderationStatus(ModerationStatus.VISIBLE)
                .build());

        ModerationActionRequest action = ModerationActionRequest.builder()
                .newStatus(ModerationStatus.HIDDEN)
                .reason("Contains verified profanity")
                .build();

        mockMvc.perform(post("/api/v1/admin/feedback/{reviewUuid}/moderation", feedback.getFeedbackUuid())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(action)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus", is("HIDDEN")));

        // History can be viewed
        mockMvc.perform(get("/api/v1/admin/feedback/{reviewUuid}/history", feedback.getFeedbackUuid())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].newStatus", is("HIDDEN")))
                .andExpect(jsonPath("$.data[0].reason", is("Contains verified profanity")));
    }

    @Test
    @DisplayName("Authorization: Non-admin caller cannot perform moderation")
    void testNonAdminCannotModerate() throws Exception {
        String reviewUuid = UUID.randomUUID().toString();
        ModerationActionRequest action = ModerationActionRequest.builder()
                .newStatus(ModerationStatus.HIDDEN)
                .reason("Test reason")
                .build();

        mockMvc.perform(post("/api/v1/admin/feedback/{reviewUuid}/moderation", reviewUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(action)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 6. VALIDATION AND PRIVACY TESTS
    // =========================================================================

    @Test
    @DisplayName("Validation: Invalid rating values (< 1 or > 5) fail validation")
    void testInvalidRatingValidation() throws Exception {
        String rideUuid = UUID.randomUUID().toString();
        SubmitRatingRequest reqZero = SubmitRatingRequest.builder().rating(0).build();
        SubmitRatingRequest reqSix = SubmitRatingRequest.builder().rating(6).build();

        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqZero)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/feedback/rides/{rideUuid}/driver-rating", rideUuid)
                        .header("Authorization", riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSix)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tags: Public GET /api/v1/feedback/tags returns catalog")
    void testGetFeedbackTags() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())));
    }

    @Test
    @DisplayName("Summary: Public GET /api/v1/drivers/{uuid}/rating-summary returns default on new driver")
    void testGetPublicDriverSummary() throws Exception {
        String newDriverUuid = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/drivers/{driverUuid}/rating-summary", newDriverUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driverUuid", is(newDriverUuid)))
                .andExpect(jsonPath("$.data.averageRating", is(5.00)))
                .andExpect(jsonPath("$.data.totalRatings", is(0)));
    }
}
