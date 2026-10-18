# LEXOR — Booking Service (Spring Boot)

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

- **Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 3.2.5 (Spring MVC, Spring Data JPA, Spring Security)
- **Database**: MySQL 8.0 / H2 (In-Memory for Automated Testing)
- **Security**: JJWT 0.12.5 (HMAC-SHA256), Spring Security Stateful/Stateless Filters
- **Documentation**: OpenAPI 3.0 / SpringDoc Swagger UI 2.5.0
- **Default Port**: `8095`

---

## 3. Configuration & Environment Variables

| Variable | Description | Default Value |
|---|---|---|
| `PORT` | HTTP server listening port | `8095` |
| `DB_HOST` | MySQL hostname | `localhost` |
| `DB_PORT` | MySQL port | `3306` |
| `DB_NAME` | Database schema name | `lexor_db` |
| `DB_USERNAME` | MySQL database username | `root` |
| `DB_PASSWORD` | MySQL database password | *(empty)* |
| `JWT_SECRET` | 256-bit HMAC secret signing key | *(configured externally)* |
| `JWT_EXPIRATION_MS` | JWT token validity in milliseconds | `86400000` (24h) |
| `DRIVER_MATCHING_SERVICE_URL` | Driver Matching Service base URL | `http://localhost:8093` |
| `RIDE_REQUEST_SERVICE_URL` | Ride Request Service base URL | `http://localhost:8091` |
| `DRIVER_SERVICE_URL` | Driver Service base URL | `http://localhost:8085` |
| `MATCHING_INTERNAL_API_KEY` | Shared internal service authorization key | `lexor_internal_matching_service_key_2026` |
| `BOOKING_OTP_EXPIRATION_MINUTES` | Rider OTP expiration window | `120` |

---

## 4. State Machine & Lifecycle Transitions

```
               [Rider creates booking]
                         │
                         ▼
                     CONFIRMED (or OFFERED)
                         │
           ┌─────────────┴─────────────┐
           │ (Driver accepts)          │ (Driver declines / cancels)
           ▼                           ▼
        ACCEPTED               DECLINED_BY_DRIVER / CANCELLED_BY_RIDER
           │                           ▲
           │ (Driver arrives)          │ (Rider cancels before pickup)
           ▼                           │
        ARRIVED ───────────────────────┘
           │
           │ (Driver enters OTP & starts)
           ▼
      IN_PROGRESS
           │
           │ (Driver arrives at drop-off & completes)
           ▼
       COMPLETED (Terminal & Idempotent)
```

### Business Rules Enforced
1. **Ownership**: Riders can only view/cancel their own bookings. Drivers can only interact with assigned bookings.
2. **OTP Security**: A 4-digit numeric OTP is generated on creation and securely stored as a BCrypt hash. Drivers must supply this code at trip start (`POST .../start`).
3. **Idempotency**: Client retries with `idempotencyKey` return existing records without duplicate insertions. Trip completion (`POST .../complete`) is idempotent.
4. **Concurrency Safety**: Active drivers with in-progress trips cannot be assigned to conflicting concurrent bookings. Unique constraint on `ride_request_id` prevents duplicate bookings for the same request.
5. **Cancellation Policies**: In-progress or completed journeys cannot be cancelled by riders.

---

## 5. API Endpoints Reference

### Rider Endpoints (`ROLE_RIDER`)
- `POST /api/v1/bookings` — Create a booking from an eligible ride request.
- `GET /api/v1/bookings/me` — Paginated list of the authenticated rider's bookings.
- `GET /api/v1/bookings/me/{bookingUuid}` — Retrieve booking details by UUID.
- `POST /api/v1/bookings/{bookingUuid}/cancel` — Cancel an active booking before trip start.

### Driver Endpoints (`ROLE_DRIVER`)
- `GET /api/v1/drivers/me/bookings` — Paginated list of assigned bookings.
- `GET /api/v1/drivers/me/bookings/{bookingUuid}` — Retrieve assigned booking details.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/accept` — Accept an offered booking.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/decline` — Decline an offered booking.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/arrived` — Record physical arrival at pickup.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/start` — Verify OTP and start journey.
- `POST /api/v1/drivers/me/bookings/{bookingUuid}/complete` — Complete trip with metrics.

### Admin Endpoints (`ROLE_ADMIN`)
- `GET /api/v1/admin/bookings` — Paginated listing with optional filters (`riderId`, `driverId`, `status`).
- `GET /api/v1/admin/bookings/{bookingUuid}` — Administrative detail view.

---

## 6. How to Run Locally

```bash
# Navigate to directory
cd backend/springboot/booking-service

# Run automated tests
mvn clean test

# Launch service
mvn spring-boot:run
```
Swagger UI will be available at: `http://localhost:8095/swagger-ui.html`
OpenAPI specification at: `http://localhost:8095/v3/api-docs`
