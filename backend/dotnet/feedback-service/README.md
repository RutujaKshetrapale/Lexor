# LEXOR Feedback & Rating Service (.NET 9)

Intelligent Mobility. Elevated.

The **Feedback Service** is a high-performance, asynchronous .NET 9 microservice in the LEXOR AI-Powered Smart Mobility Platform. It provides bidirectional post-trip ratings, structured feedback tags, review moderation, abuse prevention, and trustworthy driver and rider rating summaries matching Uber/Ola operational standards.

---

## 1. Architecture & Design Principles

```
                                  +-----------------------+
                                  |      API Gateway      |
                                  |   (Port 8080/YARP)    |
                                  +-----------+-----------+
                                              |
                        +---------------------+---------------------+
                        |                                           |
                        v                                           v
         +-----------------------------+             +-----------------------------+
         | Rider Post-Trip Rating Flow |             | Driver Post-Trip Rating Flow|
         +--------------+--------------+             +--------------+--------------+
                        |                                           |
                        +---------------------+---------------------+
                                              |
                                              v
                              +-------------------------------+
                              |    Feedback Service (8102)    |
                              |  - Authoritative Eligibility  |
                              |  - Uniqueness & Concurrency   |
                              |  - Decimal-Safe Calculations  |
                              |  - 4-State Moderation Engine  |
                              +---------------+---------------+
                                              |
                 +----------------------------+----------------------------+
                 |                            |                            |
                 v                            v                            v
    +-------------------------+  +-------------------------+  +-------------------------+
    | Booking Service Client  |  | Notification Client     |  | MySQL Schema (V11)      |
    | (Authoritative Trip RPC)|  | (Async Trip Reminder    |  | (ride_feedback, tags,   |
    |                         |  |  & Moderation Alerts)   |  |  summaries, audit log)  |
    +-------------------------+  +-------------------------+  +-------------------------+
```

### Key Technical Pillars:
1. **Server-Side Authoritative Eligibility:** Ratings can only be submitted for completed trips (`COMPLETED` / `TRIP_COMPLETED`). The authenticated principal is verified against the booking's authoritative `riderUuid` or `driverUuid`.
2. **Duplicate & Race Condition Protection:** Database unique constraint on `(ride_uuid, reviewer_user_uuid)` coupled with EF Core transactions and optimistic concurrency guarantees prevention against duplicate submissions.
3. **Decimal-Safe Arithmetic:** Rating averages are calculated using C# `decimal` with `MidpointRounding.AwayFromZero` rounded to two decimal places:
   $$\text{Average Rating} = \frac{\sum \text{Eligible Rating Values}}{\text{Count of Eligible Rating Records}}$$
4. **Independent Moderation & Auditability:** 4-state review lifecycle (`VISIBLE`, `UNDER_REVIEW`, `HIDDEN`, `REMOVED`). Reviews are never physically hard-deleted. Moderation transitions record reason and admin identity in `feedback_moderation_history`.

---

## 2. API Endpoints

### 2.1 Rider Feedback Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/feedback/rides/{rideUuid}/driver-rating` | `ROLE_RIDER` | Submit 1–5 star rating, feedback tags, and optional review text for the assigned driver. |
| `GET` | `/api/v1/feedback/me/submitted` | `ROLE_RIDER` | Retrieve all ratings submitted by the authenticated rider (paged). |
| `GET` | `/api/v1/feedback/me/pending` | `ROLE_RIDER` | Retrieve completed rides awaiting rider feedback. |
| `GET` | `/api/v1/feedback/me/reviews/{reviewUuid}` | `ROLE_RIDER` | Retrieve a specific review submitted by the authenticated rider. |
| `PATCH` | `/api/v1/feedback/me/reviews/{reviewUuid}` | `ROLE_RIDER` | Edit review text/tags within the permitted edit window (default 120 min). |
| `POST` | `/api/v1/feedback/reviews/{reviewUuid}/report` | `ROLE_RIDER`, `ROLE_DRIVER` | Report an abusive or inappropriate review. |
| `GET` | `/api/v1/feedback/tags` | Any Authenticated | Retrieve available structured feedback tags by target role. |

### 2.2 Driver Feedback Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/drivers/me/rides/{rideUuid}/rider-rating` | `ROLE_DRIVER` | Driver rates rider for a completed trip with star rating and driver feedback tags. |
| `GET` | `/api/v1/drivers/me/feedback` | `ROLE_DRIVER` | Driver views their received reviews (filtered by visibility policy, anonymous text). |
| `GET` | `/api/v1/drivers/me/rating-summary` | `ROLE_DRIVER` | Driver views their aggregate rating summary and star distribution. |

### 2.3 Public / Authenticated Rating Summary

| Method | Path | Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/drivers/{driverUuid}/rating-summary` | Authenticated | View public driver rating summary (total reviews, average rating, star distribution). |
| `GET` | `/api/v1/feedback/drivers/{driverUuid}/summary` | Authenticated | Canonical alias for driver rating summary. |

### 2.4 Administrator Moderation & Analytics Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/feedback` | `ROLE_ADMIN` | List all reviews across the platform with filtering by status and target. |
| `GET` | `/api/v1/admin/feedback/{reviewUuid}` | `ROLE_ADMIN` | Inspect full review details including reports and audit history. |
| `GET` | `/api/v1/admin/feedback/reports` | `ROLE_ADMIN` | Inspect pending and resolved review reports. |
| `POST` | `/api/v1/admin/feedback/{reviewUuid}/moderation` | `ROLE_ADMIN` | Execute moderation action (`VISIBLE`, `UNDER_REVIEW`, `HIDDEN`, `REMOVED`). |
| `GET` | `/api/v1/admin/feedback/analytics` | `ROLE_ADMIN` | System-wide feedback health, average platform score, distribution, and report stats. |

---

## 3. Eligibility & Duplicate Prevention Rules

Before any feedback record is created, the service validates:
1. **Authentication:** Reviewer identity (`userUuid`) and role extracted from validated JWT token.
2. **Authoritative Ride State:** Verified via trusted inter-service call to `booking-service` (`/api/v1/bookings/by-ride/{rideUuid}` or `/api/v1/bookings/{bookingUuid}`).
   - Status must be `COMPLETED` (or ride status `TRIP_COMPLETED`, `PAYMENT_COMPLETED`, `FEEDBACK`).
   - Rides in `REQUESTED`, `MATCHED`, `ASSIGNED`, `STARTED`, or `CANCELLED` states are rejected with `400 BAD_REQUEST`.
3. **Trip Participant Matching:**
   - For rider rating driver: Authenticated user must equal `booking.riderUuid`; target must equal `booking.driverUuid`.
   - For driver rating rider: Authenticated user must equal `booking.driverUuid`; target must equal `booking.riderUuid`.
4. **Duplicate Prevention:**
   - Query-level check: Has reviewer already submitted feedback for this ride?
   - Database constraint: Unique index `uq_feedback_ride_reviewer (ride_uuid, reviewer_user_uuid)` guarantees idempotency under concurrent load.
5. **Submission Window:** Submissions older than `FeedbackSettings:SubmissionWindowDays` (default 7 days) post trip-completion are rejected.

---

## 4. Rating Calculation & Aggregation Formula

Summary calculations are computed using exact decimal arithmetic:
$$\text{Average} = \frac{\sum_{i=1}^N \text{rating}_i}{N} \quad (\text{rounded MidpointRounding.AwayFromZero to 2 decimal places})$$

- **Eligible Ratings:** Only reviews with `moderation_status = 'VISIBLE'` are counted toward public totals and averages.
- **Excluded Count:** Reviews with `moderation_status IN ('UNDER_REVIEW', 'HIDDEN', 'REMOVED')` are excluded from the public average and tracked in `excludedRatingsCount`.
- **Distribution:** Absolute counts for each star bucket (1 star, 2 stars, 3 stars, 4 stars, 5 stars).
- **Materialized Summaries:** Cached in `driver_rating_summaries` table and dynamically recalculated upon every new submission or moderation state transition.

---

## 5. Review Privacy & Moderation Policy

1. **Driver Review Privacy:** Drivers can view aggregate feedback ratings, counts, and tags. Individual rider comments are anonymized or shielded according to privacy policies to prevent retaliatory behavior.
2. **Moderation Statuses:**
   - `VISIBLE`: Active in public aggregates and visible to authorized viewers.
   - `UNDER_REVIEW`: Flagged for review (e.g. upon reaching 3 user reports); temporarily excluded from public score.
   - `HIDDEN`: Administratively hidden from public view due to minor policy violations.
   - `REMOVED`: Administratively disqualified due to severe abuse, harassment, or safety violations. Original row is preserved for audit trails.
3. **No Automatic Penalties:** Low star ratings do not trigger automated account bans or algorithmic punishments.

---

## 6. Database Design & Migration

Schema version: `V11__feedback_service_additive.sql` in `database/mysql/migrations/`.

### Tables Owned:
- `ride_feedback`: Primary review entity storing ride ID, reviewer, target, role, rating (1–5), review text, status, and edit deadlines.
- `feedback_tags`: Catalog of structured feedback tags categorized by target role (`DRIVER` or `RIDER`) and sentiment (`POSITIVE` or `CRITIQUE`).
- `feedback_tag_assignments`: Junction table linking `ride_feedback` to `feedback_tags`.
- `feedback_reports`: User reports lodged against abusive reviews.
- `feedback_moderation_history`: Immutable audit log of all administrative moderation actions.
- `driver_rating_summaries`: Materialized driver rating summaries.
- `rider_rating_summaries`: Materialized rider rating summaries.

### Manual Migration Application Command:
```bash
mysql -h localhost -P 3306 -u root -p lexor_db < database/mysql/migrations/V11__feedback_service_additive.sql
```

> **Note:** EF Core does **not** call `EnsureCreated()` against the shared MySQL database; schema is managed exclusively via versioned SQL scripts.

---

## 7. Authentication & Security

- **User Authentication:** Validated via Microsoft.AspNetCore.Authentication.JwtBearer. Claims extracted include `sub`/`NameIdentifier` and `role`/`ClaimTypes.Role` (`ROLE_RIDER`, `ROLE_DRIVER`, `ROLE_ADMIN`).
- **Inter-Service Security:** Outbound calls to Booking and Notification services include the header:
  ```http
  X-Internal-Service-Key: lexor-internal-service-secret-key-2026
  ```
- **Input Sanitization:** Star ratings constrained to range 1–5, review text truncated and sanitized up to 1000 characters.

---

## 8. Notification Service Integration

When feedback is submitted or reported, events are dispatched to `notification-service`:
- **Driver Feedback Received Notification:** Sent to the driver informing them of new feedback.
- **Report Alert Notification:** Sent to administrative channels when a review receives multiple abuse reports.

---

## 9. Configuration & Environment Variables

| Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8102` | HTTP listening port |
| `ConnectionStrings:DefaultConnection` | `Server=localhost;Port=3306;Database=lexor_db;...` | MySQL connection string |
| `Jwt:Secret` | `lexor-default-jwt-secret-key-32-chars-long!` | JWT signing secret |
| `InternalService:ApiKey` | `lexor-internal-service-secret-key-2026` | Service-to-service auth key |
| `Services:BookingServiceUrl` | `http://localhost:8096` | Booking Service base URL |
| `Services:NotificationServiceUrl` | `http://localhost:8100` | Notification Service base URL |
| `FeedbackSettings:EditWindowMinutes` | `120` | Review text editing allowance |
| `FeedbackSettings:SubmissionWindowDays`| `7` | Maximum days after trip completion to submit rating |

---

## 10. Build & Test Commands

```bash
# Navigate to service directory
cd backend/dotnet/feedback-service

# Run unit and integration tests
dotnet test

# Build release binary
dotnet build -c Release

# Run service locally
dotnet run
```

---

## 11. Example Requests & Responses

### 11.1 Submit Rider Rating for Driver
```http
POST /api/v1/feedback/rides/f0a1b2c3-d4e5-4a6b-8c7d-9e0f1a2b3c4d/driver-rating
Authorization: Bearer <RIDER_JWT>
Content-Type: application/json

{
  "rating": 5,
  "reviewText": "Excellent driver! Arrived punctually and vehicle was spotless.",
  "tagCodes": ["CLEAN_VEHICLE", "SAFE_DRIVING", "FRIENDLY_DRIVER"]
}
```

**Response (201 Created):**
```json
{
  "feedbackUuid": "9e8d7c6b-5a4b-3c2d-1e0f-9a8b7c6d5e4f",
  "rideUuid": "f0a1b2c3-d4e5-4a6b-8c7d-9e0f1a2b3c4d",
  "reviewerUserUuid": "11111111-1111-1111-1111-111111111111",
  "targetUserUuid": "22222222-2222-2222-2222-222222222222",
  "reviewerRole": "RIDER",
  "rating": 5,
  "reviewText": "Excellent driver! Arrived punctually and vehicle was spotless.",
  "moderationStatus": "VISIBLE",
  "editAllowed": true,
  "editDeadline": "2026-10-22T10:45:00Z",
  "tags": [
    { "code": "CLEAN_VEHICLE", "label": "Clean vehicle", "sentiment": "POSITIVE" },
    { "code": "SAFE_DRIVING", "label": "Safe driving", "sentiment": "POSITIVE" },
    { "code": "FRIENDLY_DRIVER", "label": "Friendly driver", "sentiment": "POSITIVE" }
  ],
  "createdAt": "2026-10-22T08:45:00Z"
}
```

### 11.2 Get Driver Rating Summary
```http
GET /api/v1/drivers/22222222-2222-2222-2222-222222222222/rating-summary
Authorization: Bearer <ANY_AUTH_JWT>
```

**Response (200 OK):**
```json
{
  "driverUuid": "22222222-2222-2222-2222-222222222222",
  "totalRatings": 150,
  "averageRating": 4.88,
  "starDistribution": {
    "1": 1,
    "2": 2,
    "3": 5,
    "4": 20,
    "5": 122
  },
  "excludedRatingsCount": 2,
  "totalCompletedRides": 165,
  "updatedAt": "2026-10-22T08:45:00Z"
}
```

---

## 12. Known Limitations & Production Readiness Gaps

1. **Async Event Consumer:** Currently relies on synchronous REST-based verification with `booking-service`. In high-throughput Kafka deployments, an asynchronous `ride.completed` event consumer will populate local eligibility caches.
2. **Natural Language Sentiment Analysis:** Automated text scanning for safety-critical keywords (e.g. accidents, harassment) can be layered into the report ingestion pipeline.
