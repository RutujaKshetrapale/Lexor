# LEXOR — Driver Matching Microservice (Spring Boot)

The **Driver Matching Service** is responsible for geospatial driver candidate discovery, eligibility filtering, deterministic candidate scoring, atomic driver reservation locks, and driver location telemetry ingestion across the LEXOR AI-Powered Smart Mobility Platform.

---

## 1. Service Overview

- **Service Name**: `driver-matching-service`
- **Port**: `8093`
- **Runtime**: Java 21 / Spring Boot 3.2.5
- **Database**: MySQL 8.0/8.2 (Production / Dev: `lexor_db`) / H2 in MySQL mode (Automated Testing)
- **Security**: Stateless JWT Authentication compatible with LEXOR Auth Service (`ROLE_ADMIN`, `ROLE_DRIVER`) + `X-Internal-Service-Key` header for microservice-to-microservice RPC calls
- **Documentation**: OpenAPI 3 / Swagger UI (`/swagger-ui.html`)
- **Package Base**: `com.lexor.matching`

---

## 2. API Endpoints & Role Authorization

### 2.1 Internal Matching APIs (`ROLE_ADMIN` / Service Token)

Protected matching endpoints invoked by the ride request orchestrator or internal platform administrators:

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/internal/matching/ride-requests/{requestUuid}/attempts` | `ROLE_ADMIN` / Internal Key | Initiates a matching attempt for an eligible ride request. Discovers, filters, scores, and atomically reserves the top driver candidate. |
| `GET` | `/api/v1/internal/matching/ride-requests/{requestUuid}` | `ROLE_ADMIN` / Internal Key | Retrieves the latest matching attempt and reservation outcome for a ride request. |
| `POST` | `/api/v1/internal/matching/ride-requests/{requestUuid}/retry` | `ROLE_ADMIN` / Internal Key | Retries matching for a request that previously yielded `NO_DRIVERS_FOUND` or failed, incrementing the attempt counter. |

### 2.2 Driver Telemetry APIs (`ROLE_DRIVER`)

Requires a valid JWT Bearer token with claim `"role": "ROLE_DRIVER"` issued by `auth-service`. Driver identity is strictly derived from the validated security context (`UserPrincipal`):

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `PUT` | `/api/v1/drivers/me/location` | `ROLE_DRIVER` | Ingests validated driver GPS coordinates, heading, and speed. Updates persistent telemetry log (`driver_locations`) and high-performance in-memory cache. |

> [!NOTE]
> **Availability Management Ownership**: Driver availability (`ONLINE` / `OFFLINE`) is owned and managed by the existing **Driver Service** via `PATCH /api/v1/drivers/me/availability`. The Driver Matching Service does not duplicate this endpoint; it integrates with and evaluates the driver's real-time `status` in `driver_profiles`.

---

## 3. Matching Domain & Candidate Eligibility Rules

A candidate driver is evaluated against strict platform compliance rules:

```text
┌─────────────────────────────────────────────────────────────┐
│                 DRIVER CANDIDATE EVALUATION                 │
├─────────────────────────────────────────────────────────────┤
│ 1. Account Status       │ users.status == 'ACTIVE'          │
│ 2. Driver Availability  │ driver_profiles.status == 'ONLINE'│
│ 3. Driver Verification  │ is_verified == true               │
│                         │ onboarding_status == 'VERIFIED'   │
│                         │ background_check == 'PASSED'      │
│ 4. Active Vehicle Link  │ driver_vehicle_assignments.status │
│                         │   == 'ACTIVE'                     │
│ 5. Vehicle Compliance   │ vehicles.is_active == true        │
│                         │ vehicles.verification_status      │
│                         │   == 'VERIFIED'                   │
│                         │ vehicles.is_inspected == true     │
│                         │ vehicle category matches request  │
│ 6. No Active Trips/Locks│ No active reservation in          │
│                         │   driver_reservations             │
│ 7. GPS Location Fresh   │ driver_locations recorded within  │
│                         │   maxLocationAgeSeconds (<= 300s) │
│ 8. Proximity Radius     │ Haversine(pickup, driver)         │
│                         │   <= maxRadiusKm (<= 10.0 km)     │
└─────────────────────────────────────────────────────────────┘
```

Disqualification Reasons:
- Driver is `OFFLINE`, `SUSPENDED`, or `IN_TRIP`.
- Driver is unverified, under review, or rejected.
- Assigned vehicle is inactive, suspended, or uninspected.
- Vehicle category does not match the requested ride category (e.g., driver with `PREMIER` cannot fulfill a `GO` request unless explicitly compatible).
- Driver GPS coordinates are missing or older than 300 seconds.
- Driver is outside the 10.0 km matching radius.
- Driver is currently held by an active concurrent reservation lock.

---

## 4. Deterministic Scoring Algorithm

Eligible candidate drivers are ranked using a deterministic, transparent formula:

$$\text{Score} = \text{DistanceScore} + \text{RatingScore} + \text{AcceptanceScore}$$

1. **Distance Score (0 – 50 points):**
   $$\text{DistanceScore} = \max\left(0, 50 \times \left(1 - \frac{\text{Distance (km)}}{\text{Max Radius (km)}}\right)\right)$$
2. **Rating Score (0 – 30 points):**
   $$\text{RatingScore} = \min\left(30, \frac{\text{Rating}}{5.0} \times 30\right)$$
3. **Acceptance Rate Score (0 – 20 points):**
   $$\text{AcceptanceScore} = \min\left(20, \frac{\text{AcceptanceRate}}{100.0} \times 20\right)$$

**Deterministic Tie-Breaking:**
Candidates are ordered by:
1. `Match Score DESC`
2. `Distance ASC`
3. `Driver Profile ID ASC`

---

## 5. Concurrency & Atomic Driver Reservation

To prevent two concurrent ride requests from assigning the same driver simultaneously:
1. The matching service queries active locks in `driver_reservations` where `status = 'CONFIRMED' OR (status = 'RESERVED' AND expires_at > NOW())`.
2. A candidate reservation is claimed atomically under a transactional mutex (`RESERVATION_LOCK`).
3. If contention occurs, the service automatically advances to the next ranked candidate.
4. If all candidates are claimed, the attempt resolves as `NO_DRIVERS_FOUND`.
5. Created reservations hold a configurable TTL (`matching.reservation-ttl-seconds: 120`) to allow booking confirmation without deadlocking the driver indefinitely.

---

## 6. Sourcing Real Driver Location

**Audit Gap Identified:**
Prior to this phase, MySQL `driver_locations` existed in the initial schema, but no backend service had an API endpoint or persistence logic to ingest driver coordinates.

**Implemented Contract:**
- `PUT /api/v1/drivers/me/location` ingests GPS latitude, longitude, heading, speed, and timestamp.
- Strict coordinate boundary validation: Latitude $\in [-90.0, 90.0]$, Longitude $\in [-180.0, 180.0]$.
- Future timestamp rejection (exceeding 60-second clock skew).
- Data persisted to `driver_locations` and cached in high-performance memory for sub-millisecond distance calculation.
- Never fabricates fake coordinates; drivers with missing or stale location are safely disqualified.

---

## 7. Database Migrations

Migration `database/mysql/migrations/V7__driver_matching_service_additive.sql` creates:
- **`matching_attempts`**: Full audit log of attempts, candidate counts, distance, score, and outcomes (`PENDING`, `SEARCHING`, `MATCHED`, `NO_DRIVERS_FOUND`, `CANCELLED`, `FAILED`, `EXPIRED`).
- **`driver_reservations`**: Concurrency lock table for atomic driver reservation.

> [!WARNING]
> Database migrations are manual and documented. Hibernate `ddl-auto` is set to `validate` in production configuration.

---

## 8. Event Bus & Kafka Integration Status

- **Status**: Kafka is not yet provisioned in the local environment.
- **Implementation**: Clean `EventPublisher` abstraction backed by `LoggingEventPublisher` (and in-memory test buffer).
- **Events Published**:
  - `DRIVER_MATCHING_REQUESTED`
  - `DRIVER_MATCHING_SUCCEEDED`
  - `DRIVER_MATCHING_FAILED`
  - `DRIVER_ASSIGNMENT_RESERVED`
  - `DRIVER_ASSIGNMENT_RELEASED`

---

## 9. Environment Variables & Configuration

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8093` | HTTP server listening port |
| `DB_URL` | `jdbc:mysql://localhost:3306/lexor_db` | MySQL JDBC connection string |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | `""` | Database password |
| `JWT_SECRET` | None (Environment) | 256-bit HMAC-SHA256 signing secret |
| `MATCHING_MAX_RADIUS_KM` | `10.0` | Maximum candidate search radius in km |
| `MATCHING_MAX_LOCATION_AGE_SECONDS` | `300` | Stale GPS coordinate threshold (seconds) |
| `MATCHING_RESERVATION_TTL_SECONDS` | `120` | Driver reservation lock expiration (seconds) |
| `MATCHING_INTERNAL_API_KEY` | `lexor_internal_matching_service_key_2026` | Inter-service shared secret |

---

## 10. Local Build & Test Commands

```bash
# Build and run tests
mvn test -f backend/springboot/driver-matching-service/pom.xml

# Run application locally
mvn spring-boot:run -f backend/springboot/driver-matching-service/pom.xml
```

---

## 11. Example API Responses

### Successful Match (`200 OK`)
```json
{
  "attemptUuid": "99999999-9999-4999-8999-999999999999",
  "rideRequestUuid": "44444444-4444-4444-8444-444444444444",
  "status": "MATCHED",
  "attemptNumber": 1,
  "candidatesEvaluated": 3,
  "driver": {
    "driverUuid": "33333333-3333-4333-8333-333333333333",
    "firstName": "Vikram",
    "lastName": "Singh",
    "rating": 4.90,
    "totalTrips": 145,
    "acceptanceRate": 98.50,
    "distanceKm": 1.50,
    "matchScore": 91.20
  },
  "vehicle": {
    "vehicleUuid": "e7b1a2c3-4d5e-6f7a-8b9c-0d1e2f3a4b5c",
    "make": "Toyota",
    "model": "Urban Cruiser Hyryder",
    "color": "Midnight Black",
    "licensePlate": "KA-01-EQ-4829",
    "seatCapacity": 4,
    "fuelType": "HYBRID",
    "rideTypeCategory": "GO"
  },
  "distanceKm": 1.50,
  "matchScore": 91.20,
  "createdAt": "2026-10-17T12:00:00",
  "completedAt": "2026-10-17T12:00:01"
}
```

### No Drivers Found (`200 OK`)
```json
{
  "attemptUuid": "88888888-8888-4888-8888-888888888888",
  "rideRequestUuid": "44444444-4444-4444-8444-444444444444",
  "status": "NO_DRIVERS_FOUND",
  "attemptNumber": 1,
  "candidatesEvaluated": 0,
  "failureReason": "No eligible, active drivers found within 10.0 km",
  "createdAt": "2026-10-17T12:00:00",
  "completedAt": "2026-10-17T12:00:00"
}
```
