# LEXOR — Booking Service (.NET)

> **Intelligent Mobility. Elevated.**  
> Real-Time Booking Lifecycle, Driver Allocation, and Journey Management Microservice

---

## 1. Service Overview & Domain Ownership

The **Booking Service** owns the core booking record and journey execution lifecycle across the LEXOR platform. It coordinates rider booking creation, driver assignment verification, OTP generation and validation, state machine transitions, and concurrency safety.

### Service Boundaries & Non-Overlapping Responsibilities
- **Authentication Service (`auth-service`)**: Identity verification, token generation, and password hashing.
- **User Service (`user-service`)**: User entity profiles and account credentials.
- **Driver Service (`driver-service`)**: Driver verification, document compliance, and driver status.
- **Vehicle Service (`vehicle-service`)**: Vehicle approval, inspection, and specifications.
- **Ride Type Service (`ride-type-service`)**: Category pricing configuration (rates, base fares, cancellation fees).
- **Ride Request Service (`ride-request-service`)**: Rider journey intent, route calculation, and fare estimation.
- **Driver Matching Service (`driver-matching-service`)**: Proximity search, ranking, and atomic driver reservation.
- **Booking Service (`booking-service`)**: Authoritative booking record, rider OTP verification, trip state machine (`OFFERED` -> `ACCEPTED` -> `ARRIVED` -> `IN_PROGRESS` -> `COMPLETED`), and driver journey coordination.
- **Payment Service (`payment-service`)**: Financial settlement and transaction accounting (out of scope for Booking Service).

---

## 2. Technology Stack & Runtime

- **Runtime**: .NET 9.0 (`net9.0`)
- **Framework**: ASP.NET Core 9.0 (Controllers, DI, Authorization, Middleware)
- **Database & ORM**: MySQL 8.0 with Pomelo EF Core / EF Core In-Memory for Automated Testing
- **Security**: Microsoft.AspNetCore.Authentication.JwtBearer (HMAC-SHA256, BCrypt.Net-Next)
- **Documentation**: Swagger / OpenAPI (Swashbuckle.AspNetCore)
- **Default Port**: `8096`

---

## 3. Configuration & Environment Variables

| Variable | Description | Default Value |
|---|---|---|
| `PORT` | HTTP server listening port | `8096` |
| `DB_CONNECTION_STRING` | MySQL database connection string | `Server=localhost;Port=3306;Database=lexor_db;Uid=root;Pwd=;AllowPublicKeyRetrieval=True;SSLMode=None;` |
| `JWT_SECRET` | 256-bit HMAC secret signing key | *(configured externally)* |
| `JWT_ISSUER` | JWT token issuer | `LEXOR` |
| `JWT_AUDIENCE` | JWT token audience | `LEXOR` |
| `ServiceUrls__DriverMatchingServiceUrl` | Driver Matching Service base URL | `http://localhost:8094` |
| `ServiceUrls__RideRequestServiceUrl` | Ride Request Service base URL | `http://localhost:8092` |
| `ServiceUrls__DriverServiceUrl` | Driver Service base URL | `http://localhost:8086` |
| `BookingSettings__InternalApiKey` | Shared internal service authorization key | `lexor_internal_matching_service_key_2026` |
| `BookingSettings__OtpExpirationMinutes` | Rider OTP expiration window | `120` |

---

## 4. State Machine & Lifecycle Transitions

```
               [Rider creates booking]
                         │
                         ▼
                     CONFIRMED
                   (Driver offered)
                    │           │
   Driver Declines  │           │  Driver Accepts
   or Timeout       │           │
         ▼          ▼           ▼
   DECLINED_BY_DRIVER        ACCEPTED
                                │
                                │  Driver Arrives
                                ▼
                             ARRIVED
                                │
                                │  Rider OTP Validated
                                ▼  Trip Starts
                           IN_PROGRESS
                                │
                                │  Driver Drops Off
                                ▼
                            COMPLETED

   * At any point prior to IN_PROGRESS, Rider can cancel -> CANCELLED_BY_RIDER
   * Admin can cancel any non-terminal booking -> CANCELLED_BY_ADMIN
```

---

## 5. API Endpoints Specification

### Rider Endpoints (`ROLE_RIDER`)
- `POST /api/v1/bookings` — Create a new booking from a valid, eligible ride request. Returns plain 4-digit OTP.
- `GET /api/v1/bookings/me` — List paginated bookings for the authenticated rider (supports `?status=...&page=0&size=10`).
- `GET /api/v1/bookings/me/{bookingUuid}` — Retrieve one booking owned by the authenticated rider.
- `POST /api/v1/bookings/{bookingUuid}/cancel` — Cancel a booking before trip start under permitted cancellation rules.

### Driver Endpoints (`ROLE_DRIVER`)
- `GET /api/v1/drivers/me/bookings` — List bookings assigned to the authenticated driver.
- `GET /api/v1/drivers/me/bookings/{bookingUuid}` — Retrieve details of an assigned booking.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/accept` — Accept an offered booking; confirms driver reservation lock.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/decline` — Decline an offered booking; releases driver reservation.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/arrived` — Record driver arrival at pickup address.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/start` — Start trip; validates optional 4-digit rider OTP.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/complete` — Complete trip with actual distance and duration.

### Admin Endpoints (`ROLE_ADMIN`)
- `GET /api/v1/admin/bookings` — Paginated booking search with optional filters: `riderId`, `driverId`, `status`.
- `GET /api/v1/admin/bookings/{bookingUuid}` — Retrieve full booking details by UUID.

---

## 6. How to Run Locally

### Build
```bash
dotnet build
```

### Run Tests
```bash
dotnet test
```

### Start Service
```bash
dotnet run
```
Swagger UI will be accessible at `http://localhost:8096/swagger`.

---

## 7. Database Migration Prerequisites

Before running the service against MySQL:
1. Ensure `V1` through `V7` have been applied to `lexor_db`.
2. Apply `database/mysql/migrations/V8__booking_service_additive.sql`:
   ```bash
   mysql -u root -p lexor_db < database/mysql/migrations/V8__booking_service_additive.sql
   ```
