# LEXOR — Ride Request Microservice (.NET)

The **Ride Request Service** is the ASP.NET Core 9 / EF Core microservice managing ride request submissions, route & fare quotations, lifecycle state transitions, rider ownership enforcement, and platform administrative auditing across the LEXOR AI-Powered Smart Mobility Platform.

---

## 1. Service Overview

- **Service Name**: `ride-request-service`
- **Port**: `8092` (consistent with LEXOR .NET even-numbered microservice ports)
- **Runtime**: .NET 9.0 / C# 13 / ASP.NET Core Web API
- **ORM**: Entity Framework Core 9.0 (Pomelo MySQL)
- **Testing**: xUnit, Moq, EF Core InMemory Database (100% isolated, 44 tests)
- **Security**: Stateless JWT Bearer Authentication compatible with LEXOR Auth Service (`RIDER`, `ADMIN`, `DRIVER`)
- **Documentation**: OpenAPI 3 / Swagger UI (`/swagger`)

---

## 2. API Endpoints

### 2.1 Rider Endpoints (`/api/v1/ride-requests`)

Requires valid JWT Bearer token with `RIDER` or `ROLE_RIDER` role. Rider identity is strictly derived from validated claims (`uuid`, `userId`, `sub`). The client cannot spoof rider ID in the request body.

| Method | Endpoint | Access | Status Codes | Description |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/ride-requests` | `RIDER` | `201`, `400`, `401`, `403`, `404` | Submit a new ride request with pickup/drop coordinates and category. Calculates distance, duration, and fare quotation. |
| `GET` | `/api/v1/ride-requests/me` | `RIDER` | `200`, `401`, `403` | Retrieve paginated list of ride requests belonging strictly to the caller (`page`, `size`, optional `status`). |
| `GET` | `/api/v1/ride-requests/me/{requestUuid}` | `RIDER` | `200`, `401`, `403`, `404` | Retrieve details for a specific ride request. Enforces rider ownership (returns 403 if request belongs to another rider). |
| `PATCH` | `/api/v1/ride-requests/{requestUuid}/cancel` | `RIDER` | `200`, `401`, `403`, `404`, `409` | Cancel a ride request in `REQUESTED` or `SEARCHING` status. Prevents cancelling matched or terminal requests (returns 409). |

### 2.2 Administrator Endpoints (`/api/v1/admin/ride-requests`)

Requires valid JWT Bearer token with `ADMIN` or `ROLE_ADMIN` role.

| Method | Endpoint | Access | Status Codes | Description |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/ride-requests` | `ADMIN` | `200`, `401`, `403` | Paginated search across platform ride requests with optional filters (`status`, `riderId`, `page`, `size`). |
| `GET` | `/api/v1/admin/ride-requests/{requestUuid}` | `ADMIN` | `200`, `401`, `403`, `404` | Retrieve details of any ride request on the platform by its public UUID. |

---

## 3. Fare Estimation & Route Calculation

In this microservices stage, dedicated external mapping providers (e.g., Google Directions, Mapbox) are decoupled behind an internal estimation engine matching the Java Spring Boot service:

- **Great-Circle Haversine Formula**: Computes straight-line spherical distance with an earth radius of `6,371.0 km`.
- **Urban Detour Factor**: Augments straight-line distance by an empirical `1.30x` tortuosity factor to approximate actual street grid distance.
- **Estimated Duration**: Assumes an average urban velocity of `25.0 km/h` (accounting for traffic and signals) with an enforced minimum trip duration of `5 minutes`.
- **Fare Formula**:
  $$\text{DistanceFare} = \text{round}(\text{distanceKm} \times \text{perKmRate}, 2)$$
  $$\text{TimeFare} = \text{round}(\text{durationMinutes} \times \text{perMinuteRate}, 2)$$
  $$\text{Subtotal} = \text{round}(\text{baseFare} + \text{DistanceFare} + \text{TimeFare}, 2)$$
  $$\text{FinalFare} = \max(\text{Subtotal}, \text{minimumFare})$$
- **Location Guard**: Requires a minimum separation of `50 meters` between pickup and drop coordinates to prevent zero-distance exploitation.

---

## 4. Lifecycle State Machine

```
               +---------------------------+
               |         REQUESTED         |
               +-------------+-------------+
                             |
             +---------------+---------------+
             |                               |
             v                               v
    +-----------------+              +---------------+
    |    SEARCHING    |              |   CANCELLED   | (Terminal)
    +--------+--------+              +---------------+
             |                               ^
     +-------+-------+                       | (rider cancelled)
     |               |                       |
     v               v                       |
+---------+     +---------+                  |
| MATCHED |     | EXPIRED | (Terminal)       |
+----+----+     +---------+                  |
     |                                       |
     +---(Trip Lifecycle handover to Booking)
```

- **Cancellable Statuses**: Only `REQUESTED` and `SEARCHING` can be cancelled by riders.
- **Terminal Statuses**: `MATCHED`, `CANCELLED`, `EXPIRED`.
- Attempting to cancel an already cancelled, matched, or expired request produces an HTTP `409 Conflict`.

---

## 5. Shared Database Schema & Migration Status

The service maps the `ride_requests` table in `lexor_db`:

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
    INDEX idx_ride_requests_uuid (uuid),
    INDEX idx_ride_requests_rider_id (rider_id),
    INDEX idx_ride_requests_status (status),
    INDEX idx_ride_requests_requested_at (requested_at),
    CONSTRAINT fk_ride_requests_rider FOREIGN KEY (rider_id) REFERENCES rider_profiles(id),
    CONSTRAINT fk_ride_requests_ride_type FOREIGN KEY (ride_type_id) REFERENCES ride_types(id)
);
```

### Migration Policy
- In compliance with repository architecture rules, automatic schema mutations (`EnsureCreated` / `Migrate`) are **disabled**.
- The additive script `database/mysql/migrations/V6__ride_request_service_additive.sql` has been documented and is managed independently by database administrators.

---

## 6. Environment Variables & Configuration

Configuration is managed via environment variables (or `.env`):

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8092` | Kestrel HTTP listening port |
| `DB_CONNECTION_STRING` | *(constructed)* | MySQL connection string to `lexor_db` |
| `JWT_SECRET` / `LEXOR_JWT_SECRET` | *(secret)* | Shared HMAC-SHA256 signing secret |
| `JWT_ISSUER` | `LEXOR` | Token issuer validation claim |
| `JWT_AUDIENCE` | `LEXOR` | Token audience validation claim |
| `Services__RideTypeServiceUrl` | `http://localhost:8089` | Base URL for Ride Type Service inter-service HTTP client |
| `Services__TimeoutSeconds` | `3` | HTTP timeout for inter-service communication |

---

## 7. Build and Test Instructions

### Build
```powershell
cd backend/dotnet/ride-request-service
dotnet build
```

### Run Tests
```powershell
cd backend/dotnet/ride-request-service
dotnet test
```

### Run Service Locally
```powershell
cd backend/dotnet/ride-request-service
dotnet run
```
Once started, explore the interactive OpenAPI documentation at `http://localhost:8092/swagger`.
