# LEXOR — Spring Cloud API Gateway

**Module**: `backend/springboot/api-gateway`  
**Framework**: Spring Boot 3.2.5, Spring Cloud 2023.0.1 (`spring-cloud-starter-gateway`)  
**Runtime**: Java 21, Project Reactor / Netty (Reactive Non-Blocking Architecture)  
**Default Port**: `8080` (Configurable via `PORT` environment variable)  
**Status**: **VERIFIED** (12/12 Automated Integration Tests Passing)

---

## 1. Overview & Architecture

The **Spring Cloud API Gateway** serves as the unified, secure perimeter entry point for the LEXOR Intelligent Mobility Platform. Built on Spring WebFlux and Project Reactor, it enforces centralized security, role-based access control (RBAC), edge rate limiting, request correlation tracking, and strict internal endpoint isolation without relying on blocking servlets.

```
                  ┌─────────────────────────────────────────────────────────┐
                  │                 CLIENT APPLICATIONS                     │
                  │         (Mobile App, Web Dashboard, Admin Portal)        │
                  └──────────────────────────┬──────────────────────────────┘
                                             │ HTTPS / REST
                                             ▼
                  ┌─────────────────────────────────────────────────────────┐
                  │          LEXOR SPRING CLOUD API GATEWAY (8080)          │
                  │                                                         │
                  │  1. CorrelationIdFilter      (X-Correlation-Id)         │
                  │  2. InternalEndpointFilter   (Blocks /api/v1/internal)  │
                  │  3. CorsWebFilter            (Strict Allowed Origins)   │
                  │  4. JwtAuthGatewayFilter     (Validates JWT & Roles)    │
                  │  5. RateLimiterFilter        (Token Bucket 429 Guard)   │
                  │  6. RedactedLoggingFilter    (Safe Audit Logging)       │
                  │  7. GlobalExceptionHandler   (503/504 Safe Errors)      │
                  └──────┬──────────┬──────────┬──────────┬──────────┬──────┘
                         │          │          │          │          │
         ┌───────────────┘          │          │          │          └───────────────┐
         ▼                          ▼          ▼          ▼                          ▼
   Auth Service (8081)       User (8083)  Driver (8085) Vehicle (8087)       Booking (8095)
   Ride Type (8089)          Ride Request (8091)        Driver Matching (8093)
```

---

## 2. Service Route Inventory

The gateway dispatches incoming traffic to underlying microservices based on deterministic, ordered route predicates:

| Route ID | Path Predicate | Target Service | Default Port | Access Policy |
| :--- | :--- | :--- | :--- | :--- |
| `auth-service-route` | `/api/v1/auth/**` | Auth Service | `8081` | **Public** (Bypasses JWT validation) |
| `user-service-users-route` | `/api/v1/users/**` | User Service | `8083` | **Authenticated** (`ROLE_RIDER`, `ROLE_DRIVER`, `ROLE_ADMIN`) |
| `user-service-riders-route` | `/api/v1/riders/**` | User Service | `8083` | **Rider / Admin** (`ROLE_RIDER`, `ROLE_ADMIN`) |
| `booking-service-driver-route` | `/api/v1/drivers/me/bookings/**` | Booking Service | `8095` | **Driver** (`ROLE_DRIVER`, `ROLE_ADMIN`) |
| `driver-matching-location-route`| `/api/v1/drivers/me/location/**` | Driver Matching | `8093` | **Driver** (`ROLE_DRIVER`, `ROLE_ADMIN`) |
| `driver-service-route` | `/api/v1/drivers/**` | Driver Service | `8085` | **Driver** (`ROLE_DRIVER`, `ROLE_ADMIN`) |
| `driver-service-admin-route` | `/api/v1/admin/drivers/**` | Driver Service | `8085` | **Admin Only** (`ROLE_ADMIN`) |
| `vehicle-service-route` | `/api/v1/vehicles/**` | Vehicle Service | `8087` | **Driver** (`ROLE_DRIVER`, `ROLE_ADMIN`) |
| `vehicle-service-admin-route` | `/api/v1/admin/vehicles/**` | Vehicle Service | `8087` | **Admin Only** (`ROLE_ADMIN`) |
| `ride-type-service-route` | `/api/v1/ride-types/**` | Ride Type Service | `8089` | **Public GET**; Admin for mutations |
| `ride-type-service-admin-route`| `/api/v1/admin/ride-types/**` | Ride Type Service | `8089` | **Admin Only** (`ROLE_ADMIN`) |
| `ride-request-service-route` | `/api/v1/ride-requests/**` | Ride Request Service | `8091` | **Rider / Admin** (`ROLE_RIDER`, `ROLE_ADMIN`) |
| `ride-request-service-admin-route`| `/api/v1/admin/ride-requests/**` | Ride Request Service | `8091` | **Admin Only** (`ROLE_ADMIN`) |
| `booking-service-route` | `/api/v1/bookings/**` | Booking Service | `8095` | **Rider / Admin** (`ROLE_RIDER`, `ROLE_ADMIN`) |
| `booking-service-admin-route` | `/api/v1/admin/bookings/**` | Booking Service | `8095` | **Admin Only** (`ROLE_ADMIN`) |
| *Internal Microservice RPC* | `/api/v1/internal/**` | *None* | *N/A* | **BLOCKED (403 Forbidden)** |

---

## 3. Centralized Authentication & Security Model

1. **JWT Verification**:
   - Validates HMAC-SHA256 tokens using the configured secret key.
   - Enforces token expiration, issuer (`LEXOR`), and audience (`LEXOR`).
   - Rejects unauthenticated requests with `401 Unauthorized` and standard error payload.
2. **Spoofed Header Stripping**:
   - Removes any client-provided `X-User-Uuid`, `X-User-Role`, `X-User-Email`, `X-User-Id`, and `X-Internal-Service-Key` before evaluating the request.
   - Downstream services receive only cryptographic claims verified and injected by the gateway.
3. **Role-Based Access Control (RBAC)**:
   - Admin routes (`/api/v1/admin/**`) require `ROLE_ADMIN`.
   - Driver routes (`/api/v1/drivers/**`, `/api/v1/vehicles/**`) require `ROLE_DRIVER` or `ROLE_ADMIN`.
   - Rider routes (`/api/v1/riders/**`, `/api/v1/ride-requests/**`, `/api/v1/bookings/**`) require `ROLE_RIDER` or `ROLE_ADMIN`.
4. **Internal Route Isolation**:
   - Any external request matching `/api/v1/internal/**` is blocked immediately with `403 Forbidden`.

---

## 4. Rate Limiting, CORS, and Observability

- **Rate Limiting**:
  - In-memory token-bucket algorithm per client IP or authenticated user UUID.
  - Returns `429 Too Many Requests` with a `Retry-After: 1` header.
  - Preflight `OPTIONS` and `/health` requests automatically bypass the rate limiter.
- **CORS**:
  - Pre-configured for `http://localhost:3000` and `http://localhost:5173`.
  - Supports `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`.
  - Handles credentials and preflight handshakes without wildcard vulnerabilities.
- **Health & Readiness**:
  - `GET /health`: Returns `{ "status": "UP", "service": "LEXOR Spring Cloud API Gateway", "version": "1.0.0" }`.
  - `GET /actuator/health`: Standard Spring Actuator health endpoint.

---

## 5. Configuration & Environment Variables

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8080` | Port on which the gateway listens |
| `JWT_SECRET` | *(Default 32-char key)* | Secret key for JWT verification |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Auth service downstream base URL |
| `USER_SERVICE_URL` | `http://localhost:8083` | User service downstream base URL |
| `DRIVER_SERVICE_URL` | `http://localhost:8085` | Driver service downstream base URL |
| `VEHICLE_SERVICE_URL` | `http://localhost:8087` | Vehicle service downstream base URL |
| `RIDE_TYPE_SERVICE_URL` | `http://localhost:8089` | Ride type service downstream base URL |
| `RIDE_REQUEST_SERVICE_URL` | `http://localhost:8091` | Ride request service downstream base URL |
| `DRIVER_MATCHING_SERVICE_URL` | `http://localhost:8093` | Driver matching service downstream base URL |
| `BOOKING_SERVICE_URL` | `http://localhost:8095` | Booking service downstream base URL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Comma-separated list of allowed origins |
| `RATE_LIMIT_ENABLED` | `true` | Toggle rate limiting |
| `RATE_LIMIT_BURST_CAPACITY` | `100` | Token bucket capacity |
| `RATE_LIMIT_REPLENISH_RATE` | `50` | Token replenish rate per second |

---

## 6. Build and Test Verification

### Run Automated Tests
```bash
cd backend/springboot/api-gateway
mvn test
```
**Results**:
- Tests run: **12**
- Failures: **0**
- Errors: **0**
- Skipped: **0**

### Run Locally
```bash
cd backend/springboot/api-gateway
mvn spring-boot:run
```
