# LEXOR — Ride Request Microservice (Spring Boot)

The **Ride Request Service** manages rider ride requests, spatial journey validation, fare estimation, and initial request lifecycle state transitions across the LEXOR AI-Powered Smart Mobility Platform.

---

## 1. Service Overview

- **Service Name**: `ride-request-service`
- **Port**: `8091`
- **Runtime**: Java 21 / Spring Boot 3.2.5
- **Database**: MySQL 8.0/8.2 (Production / Dev: `lexor_db`) / H2 in MySQL mode (Automated Testing)
- **Security**: Stateless JWT Authentication compatible with LEXOR Auth Service
- **Documentation**: OpenAPI 3 / Swagger UI (`/swagger-ui.html`)
- **Package Base**: `com.lexor.riderequest`

---

## 2. API Endpoints & Role Authorization

### 2.1 Rider APIs (`ROLE_RIDER`)

Requires a valid JWT Bearer token with claim `"role": "ROLE_RIDER"` issued by `auth-service`. Rider identity is strictly derived from the validated security context; client-supplied rider IDs are never accepted as authority for ownership.

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/ride-requests` | `ROLE_RIDER` | Submit a new ride request with pickup, destination, and ride category. Computes fare, distance, and duration. |
| `GET` | `/api/v1/ride-requests/me` | `ROLE_RIDER` | List only the authenticated rider's requests (supports pagination and status filtering). |
| `GET` | `/api/v1/ride-requests/me/{requestUuid}` | `ROLE_RIDER` | Retrieve details of a single request owned by the authenticated rider (enforces ownership: returns 403 if accessed by another rider). |
| `PATCH` | `/api/v1/ride-requests/{requestUuid}/cancel` | `ROLE_RIDER` | Cancel rider's own request only when lifecycle status allows it (`REQUESTED` or `SEARCHING`). Rejects invalid or repeated cancellations with 409 Conflict. |

### 2.2 Administrator APIs (`ROLE_ADMIN`)

Requires a valid JWT Bearer token with claim `"role": "ROLE_ADMIN"`.

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/ride-requests` | `ROLE_ADMIN` | List all platform ride requests with pagination and optional filters (`status`, `riderId`). |
| `GET` | `/api/v1/admin/ride-requests/{requestUuid}` | `ROLE_ADMIN` | Retrieve full administrative details of any ride request by UUID. |

---

## 3. Request Lifecycle State Machine

The service implements a documented, validated domain state machine:

```text
       ┌───────────────┐
       │   REQUESTED   │ ◄── Initial status upon submission
       └───────┬───────┘
               │
        ┌──────┴──────┬──────────────────────┐
        │             │                      │
        ▼             ▼                      ▼
  ┌───────────┐ ┌───────────┐          ┌───────────┐
  │ SEARCHING │ │ CANCELLED │          │  EXPIRED  │
  └─────┬─────┘ └───────────┘          └───────────┘
        │         (Terminal)             (Terminal)
   ┌────┴────┬──────────────┐
   │         │              │
   ▼         ▼              ▼
┌────────┐ ┌───────────┐ ┌───────────┐
│ MATCHED│ │ CANCELLED │ │  EXPIRED  │
└────────┘ └───────────┘ └───────────┘
(Handed    (Terminal)    (Terminal)
 to Booking)
```

### Transition Rules:
1. **Creation**: Every ride request begins in `REQUESTED` status.
2. **Matching Discovery**: When picked up by the future Driver Matching Service, status transitions to `SEARCHING`.
3. **Driver Assignment**: When a driver accepts the dispatch, status transitions to `MATCHED` (and handoff occurs to Booking Service).
4. **Cancellation**: Permitted ONLY while in `REQUESTED` or `SEARCHING` status.
5. **Repeated Cancellation Protection**: Attempting to cancel an already `CANCELLED` request is rejected with `409 Conflict`.
6. **Post-Match Cancellation**: Attempting to cancel a `MATCHED` request via this endpoint is rejected with `409 Conflict` (post-match cancellation belongs to trip booking management).
7. **Timeout**: Requests in `REQUESTED` or `SEARCHING` that exceed driver dispatch timeout transition to `EXPIRED`.

---

## 4. Fare Estimation Model & Urban Routing Heuristic

The dedicated `FareEstimationService` computes journey metrics using decimal-safe `BigDecimal` arithmetic:

### Distance Heuristic (Development Estimator)
```text
Distance (km) = Haversine(pickupLat, pickupLng, dropLat, dropLng) × 1.30 (Urban Detour Factor)
```
- **Limitation Note**: In this microservice phase, an external commercial road network navigation provider (e.g., Google Maps Directions, OSRM, or Mapbox) is not integrated. Straight-line Haversine distance is multiplied by an empirical urban street-network tortuosity factor (1.30x) to approximate city road navigation. This estimate is for quotation and staging, not a certified turn-by-turn road quote.

### Duration Heuristic
```text
Duration (minutes) = max(5, round((Distance / 25.0 km/h) × 60))
```
- Assumes an average urban operating speed of 25 km/h with traffic lights. Minimum floor of 5 minutes is enforced.

### Fare Calculation Formula
```text
Distance Fare = Distance (km) × per_km_rate
Time Fare     = Duration (mins) × per_minute_rate
Subtotal      = base_fare + Distance Fare + Time Fare
Final Fare    = max(Subtotal, minimum_fare)
```
All currency amounts are rounded half-up to two decimal places in Indian Rupees (`INR`).

---

## 5. Configuration & Environment Variables

Copy `.env.example` to configure local or deployment environments. **No real secrets are hardcoded in source control.**

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8091` | HTTP server listening port |
| `DB_HOST` | `localhost` | MySQL database host |
| `DB_PORT` | `3306` | MySQL database port |
| `DB_NAME` | `lexor_db` | MySQL database schema name |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | *(None / Required)* | Database password (set via env var) |
| `DB_URL` | *(constructed)* | Custom JDBC connection string |
| `JWT_SECRET` | *(None / Required)* | HMAC-SHA secret key shared with Auth Service (256-bit) |
| `JWT_EXPIRATION_MS` | `86400000` | Token expiration in milliseconds (24h) |
| `RIDE_TYPE_SERVICE_URL` | `http://localhost:8089` | Base URL of Ride Type Service |
| `RIDE_TYPE_CONNECT_TIMEOUT` | `3000` | Client connect timeout in milliseconds |
| `RIDE_TYPE_READ_TIMEOUT` | `3000` | Client read timeout in milliseconds |
| `LOG_LEVEL_ROOT` | `INFO` | Root logging level |
| `LOG_LEVEL_RIDEREQUEST` | `DEBUG` | Microservice logger level |

---

## 6. Database Safety & Migration Architecture

### 6.1 Existing Verified Schema (`lexor_db.ride_requests`)
The service strictly maps the verified `ride_requests` table with `spring.jpa.hibernate.ddl-auto=validate`:

```sql
CREATE TABLE IF NOT EXISTS ride_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    rider_id BIGINT NOT NULL,
    ride_type_id BIGINT NOT NULL,
    pickup_address VARCHAR(255) NOT NULL,
    pickup_latitude DECIMAL(10, 8) NOT NULL,
    pickup_longitude DECIMAL(11, 8) NOT NULL,
    drop_address VARCHAR(255) NOT NULL,
    drop_latitude DECIMAL(10, 8) NOT NULL,
    drop_longitude DECIMAL(11, 8) NOT NULL,
    estimated_distance_km DECIMAL(8, 2) NOT NULL,
    estimated_duration_minutes INT NOT NULL,
    estimated_fare DECIMAL(10, 2) NOT NULL,
    coupon_id BIGINT DEFAULT NULL,
    status ENUM('REQUESTED', 'SEARCHING', 'MATCHED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ride_requests_rider FOREIGN KEY (rider_id) REFERENCES rider_profiles (id),
    CONSTRAINT fk_ride_requests_ride_type FOREIGN KEY (ride_type_id) REFERENCES ride_types (id),
    CONSTRAINT fk_ride_requests_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE SET NULL,
    INDEX idx_ride_req_rider (rider_id),
    INDEX idx_ride_req_status (status),
    INDEX idx_ride_req_created (requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 6.2 Additive Migration (V6)
An additive migration file has been created at:
`database/mysql/migrations/V6__ride_request_service_additive.sql`

This migration defines optional extensions:
- `cancellation_reason VARCHAR(500) NULL`
- `cancelled_at TIMESTAMP NULL`
- `ride_mode ENUM('IMMEDIATE', 'SCHEDULED') NOT NULL DEFAULT 'IMMEDIATE'`
- `scheduled_pickup_time TIMESTAMP NULL`
- Composite index `idx_ride_req_rider_status (rider_id, status)`

*Notice: In accordance with database safety rules, V6 is not automatically applied. The service is fully functional with the existing V1 table.*

---

## 7. Example Payloads (cURL)

### 7.1 Submit a Ride Request
```bash
curl -X POST http://localhost:8091/api/v1/ride-requests \
  -H "Authorization: Bearer <RIDER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "pickupAddress": "MG Road, Indiranagar, Bengaluru",
    "pickupLatitude": 12.97840000,
    "pickupLongitude": 77.64080000,
    "dropAddress": "Prestige Tech Park, Marathahalli, Bengaluru",
    "dropLatitude": 12.93720000,
    "dropLongitude": 77.69490000,
    "rideTypeCode": "GO"
  }'
```

**Response (`201 Created`):**
```json
{
  "requestUuid": "c7a8b9d0-1234-4567-89ab-cdef01234567",
  "riderUuid": "22222222-2222-4222-8222-222222222222",
  "riderName": "Rajesh Kumar",
  "riderEmail": "rajesh.kumar@example.com",
  "pickupAddress": "MG Road, Indiranagar, Bengaluru",
  "pickupLatitude": 12.97840000,
  "pickupLongitude": 77.64080000,
  "dropAddress": "Prestige Tech Park, Marathahalli, Bengaluru",
  "dropLatitude": 12.93720000,
  "dropLongitude": 77.69490000,
  "rideTypeCode": "GO",
  "rideTypeName": "LEXOR Go",
  "estimatedDistanceKm": 11.50,
  "estimatedDurationMinutes": 32,
  "estimatedFare": 250.00,
  "currency": "INR",
  "status": "REQUESTED",
  "requestedAt": "2026-10-16T12:00:00",
  "createdAt": "2026-10-16T12:00:00",
  "updatedAt": "2026-10-16T12:00:00"
}
```

### 7.2 List Authenticated Rider's Requests
```bash
curl -X GET "http://localhost:8091/api/v1/ride-requests/me?page=0&size=10&status=REQUESTED" \
  -H "Authorization: Bearer <RIDER_JWT_TOKEN>"
```

### 7.3 Cancel Ride Request
```bash
curl -X PATCH http://localhost:8091/api/v1/ride-requests/c7a8b9d0-1234-4567-89ab-cdef01234567/cancel \
  -H "Authorization: Bearer <RIDER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Plans changed, taking metro instead"}'
```

### 7.4 Admin List All Requests
```bash
curl -X GET "http://localhost:8091/api/v1/admin/ride-requests?page=0&size=20" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

---

## 8. Building and Running

### Run Automated Tests
```bash
mvn clean test
```

### Run Service Locally
```bash
mvn spring-boot:run
```

### Package Production JAR
```bash
mvn clean package -DskipTests
```

### Swagger Documentation
Once running, navigate to:
- **Swagger UI**: [http://localhost:8091/swagger-ui.html](http://localhost:8091/swagger-ui.html)
- **OpenAPI v3 JSON**: [http://localhost:8091/v3/api-docs](http://localhost:8091/v3/api-docs)
