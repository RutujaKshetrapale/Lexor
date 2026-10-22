# LEXOR Platform — Admin Dashboard Metrics & Aggregation Specification

## 1. Executive Summary & Purpose

The **Admin Operations Dashboard** provides platform leadership and operations personnel with accurate, real-time and historical visibility into rides, fleet status, financial metrics, customer feedback, and system health.

In Phase 14.1, critical aggregation and resilience corrections were applied across both Spring Boot (`admin-operations-service`) and ASP.NET Core (`AdminOperationsService`) backends to eliminate page truncation, duplicate counting, incorrect revenue calculations, and silent upstream failures.

---

## 2. Core Operational Metrics Catalog

| Metric Key | Metric Name | Source Service | Business Meaning & Lifecycle Treatment | Aggregation Method |
| :--- | :--- | :--- | :--- | :--- |
| `totalRides` | Total Platform Rides | `booking-service` | Total count of authoritative bookings created in the system, excluding purged test data. | Multi-page traversal with UUID deduplication. |
| `completedRides` | Completed Rides | `booking-service` | Rides where the lifecycle successfully reached terminal `COMPLETED` state. | Filtered count of `status == 'COMPLETED'`. |
| `cancelledRides` | Cancelled Rides | `booking-service` | Rides terminated before completion (`CANCELLED_BY_RIDER`, `CANCELLED_BY_DRIVER`, `CANCELLED_BY_ADMIN`, or `EXPIRED`). | Filtered count across all cancellation/expiry statuses. |
| `activeRides` | Active Rides In Progress | `booking-service` | Live trips currently underway or en route (`CONFIRMED`, `ACCEPTED`, `ARRIVED`, `IN_PROGRESS`). | Filtered count of non-terminal in-flight rides. |
| `pendingRideRequests` | Pending Requests | `ride-request-service` | Rider ride requests awaiting dispatch or driver acceptance in the matching engine. | Live count of requests in `PENDING` status. |
| `onlineDrivers` | Available Online Drivers | `driver-service` | Registered drivers whose current status is `ONLINE` and available for dispatch. | Aggregated from driver profiles. |
| `inTripDrivers` | Drivers In Trip | `driver-service` | Drivers currently engaged in active passenger transport (`IN_TRIP`). | Count of drivers in active bookings. |
| `pendingDriverVerifications`| Pending Verifications | `driver-service` | Driver accounts whose onboarding documents await admin compliance verification. | Count of drivers in `PENDING_VERIFICATION` status. |
| `registeredRiders` | Registered Riders | `user-service` | Total passenger accounts with verified `RIDER` role. | Platform-wide count from user directory. |
| `registeredDrivers` | Registered Drivers | `driver-service` / `user-service` | Total driver accounts onboarded on the platform. | Authoritative count from driver profiles. |
| `registeredVehicles` | Active Fleet Vehicles | `vehicle-service` | Approved vehicles registered in the platform fleet. | Count of approved vehicle assets. |
| `grossBookingValue` | Gross Booking Value (GBV) | `booking-service` | Gross fare accrued exclusively from **completed platform rides**. Unpaid, cancelled, or failed rides are strictly excluded from gross booking value. | Multi-page summation of `fareAmount` / `totalFare` for `COMPLETED` bookings only. |
| `successfulPaymentTotals` | Net Collected Payments | `payment-service` | Actual monetary transactions successfully processed and collected in `COMPLETED` state via payment gateways. | Authoritative server-side reconciliation from payment-service. |
| `refundTotals` | Total Refunds Disbursed | `payment-service` | Sum of customer refunds processed for cancellations or driver disputes. | Sum of refund records from payment reconciliation. |
| `platformCommissionEarned` | Platform Commission Cut | `payment-service` | Net platform margin retained by LEXOR after driver payouts and payment gateway processing fees. | Sum of retained commission from completed payments. |
| `averageDriverRating` | Platform Driver Rating | `feedback-service` | Average rating score (1.00 to 5.00) calculated with half-up decimal precision across all approved reviews. | Calculated rating summary endpoint in feedback-service. |
| `feedbackReportsAwaitingModeration`| Pending Moderation | `feedback-service` | Rider/driver reviews reported for policy violations awaiting administrator moderation. | Count of reviews in `UNDER_REVIEW` moderation status. |

---

## 3. Aggregation Algorithm & Pagination Correctness

### A. The Truncation Bug (Phase 14 Problem)
Previously, the executive overview fetched only page 0 with `size=100`:
- Platforms with `> 100` total rides had their `completedRides`, `cancelledRides`, `activeRides`, and `grossBookingValue` truncated to the first 100 records.
- Gross booking value previously included cancelled rides, artificially inflating platform revenue figures.

### B. The Phase 14.1 Solution
Both Spring Boot and .NET implementations now execute a safe, bounded multi-page pagination algorithm:
1. **Loop Termination:** Iterates `page = 0` up to `totalPages`, with a strict safety cap of `maxPages = 50` (5,000 records) to prevent infinite loops.
2. **Deduplication:** A `Set<String> seenBookingUuids` (`HashSet<string>` in C#) tracks processed records. If concurrent writes shift records across page boundaries, duplicates are safely discarded.
3. **Completed-Only Revenue Accrual:** Fares are only added to `grossBookingValue` if `status == "COMPLETED"`. Unpaid, cancelled, or expired rides do not contribute to revenue.
4. **Empty Dataset Handling:** If 0 bookings exist, all metrics safely evaluate to zero without null reference exceptions or division-by-zero errors.

---

## 4. Upstream Failure & Partial Data Reporting

If an upstream microservice (`booking-service`, `payment-service`, etc.) is offline, times out, or returns a 5xx error:
1. **No Silent Zero Fallbacks:** The platform does not misreport an offline service as "zero rides" or "zero revenue" without explanation.
2. **Partial Data Response:** The API response sets:
   - `isPartialData = true`
   - `unavailableSources = ["booking-service", "payment-service"]`
3. **Structured Logging:** Upstream failures are logged with WARN severity including destination URL and error reason, but without printing credentials or sensitive payload tokens.
