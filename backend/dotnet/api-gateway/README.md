# LEXOR — ASP.NET Core YARP API Gateway

**Module**: `backend/dotnet/api-gateway`  
**Framework**: ASP.NET Core 9.0 (`net9.0`), `Yarp.ReverseProxy` 2.2.0  
**Runtime**: .NET 9.0 SDK, Kestrel HTTP/1.1 and HTTP/2  
**Default Port**: `8081` (Configurable via `PORT` environment variable)  
**Status**: **VERIFIED** (12/12 Automated Integration Tests Passing)

---

## 1. Overview & Architecture

The **ASP.NET Core YARP API Gateway** provides high-throughput, low-latency edge routing for the LEXOR Intelligent Mobility Platform using Microsoft's Yet Another Reverse Proxy (YARP). It mirrors the security policies, route definitions, rate limiting, and observability of the Spring Cloud Gateway to deliver an equivalent external API surface.

```
                  ┌─────────────────────────────────────────────────────────┐
                  │                 CLIENT APPLICATIONS                     │
                  │         (Mobile App, Web Dashboard, Admin Portal)        │
                  └──────────────────────────┬──────────────────────────────┘
                                             │ HTTPS / REST
                                             ▼
                  ┌─────────────────────────────────────────────────────────┐
                  │            LEXOR .NET YARP API GATEWAY (8081)           │
                  │                                                         │
                  │  1. GlobalExceptionMiddleware (503/504 Handling)        │
                  │  2. CorrelationIdMiddleware   (X-Correlation-Id)        │
                  │  3. InternalProtectionMiddleware (Blocks /internal)    │
                  │  4. RedactedLoggingMiddleware (Safe Audit Logging)      │
                  │  5. CorsMiddleware            (Strict Allowed Origins)  │
                  │  6. JwtAuthenticationMiddleware (Validates JWT & Roles) │
                  │  7. RateLimitingMiddleware    (Fixed-Window 429 Guard)  │
                  │  8. YarpReverseProxyEngine    (High-performance Proxy)  │
                  └──────┬──────────┬──────────┬──────────┬──────────┬──────┘
                         │          │          │          │          │
         ┌───────────────┘          │          │          │          └───────────────┐
         ▼                          ▼          ▼          ▼                          ▼
   Auth Service (8082)       User (8084)  Driver (8086) Vehicle (8088)       Booking (8096)
   Ride Type (8090)          Ride Request (8092)        Driver Matching (8094)
```

---

## 2. Service Route Inventory

Routes are configured in `appsettings.json` under `ReverseProxy:Routes` and mapped to upstream clusters:

| Route ID | Path Predicate | Target Cluster | Destination Port | Access Policy |
| :--- | :--- | :--- | :--- | :--- |
| `auth-route` | `/api/v1/auth/{**catch-all}` | `auth-cluster` | `8082` | **Public** (Bypasses JWT validation) |
| `user-users-route` | `/api/v1/users/{**catch-all}` | `user-cluster` | `8084` | **Authenticated** (`ROLE_RIDER`, `ROLE_DRIVER`, `ROLE_ADMIN`) |
| `user-riders-route` | `/api/v1/riders/{**catch-all}` | `user-cluster` | `8084` | **Rider / Admin** (`ROLE_RIDER`, `ROLE_ADMIN`) |
| `booking-driver-route` | `/api/v1/drivers/me/bookings/{**catch-all}` | `booking-cluster` | `8096` | **Driver** (`ROLE_DRIVER`, `ROLE_ADMIN`) |
| `driver-matching-location-route`| `/api/v1/drivers/me/location/{**catch-all}` | `driver-matching-cluster` | `8094` | **Driver** (`ROLE_DRIVER`, `ROLE_ADMIN`) |
| `driver-route` | `/api/v1/drivers/{**catch-all}` | `driver-cluster` | `8086` | **Driver** (`ROLE_DRIVER`, `ROLE_ADMIN`) |
| `driver-admin-route` | `/api/v1/admin/drivers/{**catch-all}` | `driver-cluster` | `8086` | **Admin Only** (`ROLE_ADMIN`) |
| `vehicle-route` | `/api/v1/vehicles/{**catch-all}` | `vehicle-cluster` | `8088` | **Driver** (`ROLE_DRIVER`, `ROLE_ADMIN`) |
| `vehicle-admin-route` | `/api/v1/admin/vehicles/{**catch-all}` | `vehicle-cluster` | `8088` | **Admin Only** (`ROLE_ADMIN`) |
| `ride-type-route` | `/api/v1/ride-types/{**catch-all}` | `ride-type-cluster` | `8090` | **Public GET**; Admin for mutations |
| `ride-type-admin-route`| `/api/v1/admin/ride-types/{**catch-all}` | `ride-type-cluster` | `8090` | **Admin Only** (`ROLE_ADMIN`) |
| `ride-request-route` | `/api/v1/ride-requests/{**catch-all}` | `ride-request-cluster` | `8092` | **Rider / Admin** (`ROLE_RIDER`, `ROLE_ADMIN`) |
| `ride-request-admin-route`| `/api/v1/admin/ride-requests/{**catch-all}` | `ride-request-cluster` | `8092` | **Admin Only** (`ROLE_ADMIN`) |
| `booking-route` | `/api/v1/bookings/{**catch-all}` | `booking-cluster` | `8096` | **Rider / Admin** (`ROLE_RIDER`, `ROLE_ADMIN`) |
| `booking-admin-route` | `/api/v1/admin/bookings/{**catch-all}` | `booking-cluster` | `8096` | **Admin Only** (`ROLE_ADMIN`) |
| *Internal Microservice RPC* | `/api/v1/internal/{**catch-all}` | *None* | *N/A* | **BLOCKED (403 Forbidden)** |

---

## 3. Centralized Authentication & Security Model

1. **JWT Verification**:
   - Validates HMAC-SHA256 tokens using `JwtSecurityTokenHandler`.
   - Validates `Issuer = "LEXOR"`, `Audience = "LEXOR"`, and `Lifetime`.
   - Rejects unauthenticated requests with `401 Unauthorized`.
2. **Spoofed Header Stripping**:
   - Removes incoming client headers: `X-User-Uuid`, `X-User-Role`, `X-User-Email`, `X-User-Id`, and `X-Internal-Service-Key`.
   - Downstream services receive only verified identity headers populated from authenticated JWT claims.
3. **Role-Based Access Control (RBAC)**:
   - Admin routes (`/api/v1/admin/*`) require `ROLE_ADMIN`.
   - Driver routes (`/api/v1/drivers/*`, `/api/v1/vehicles/*`) require `ROLE_DRIVER` or `ROLE_ADMIN`.
   - Rider routes (`/api/v1/riders/*`, `/api/v1/ride-requests/*`, `/api/v1/bookings/*`) require `ROLE_RIDER` or `ROLE_ADMIN`.
4. **Internal Route Isolation**:
   - Direct requests matching `/api/v1/internal/*` are rejected with `403 Forbidden`.

---

## 4. Rate Limiting, CORS, and Observability

- **Rate Limiting**:
  - Partitioned rate limiter using ASP.NET Core `Microsoft.AspNetCore.RateLimiting`.
  - Partitioned by user UUID or remote IP. Returns `429 Too Many Requests` with a `Retry-After: 1` header.
  - Preflight `OPTIONS` and `/health` requests bypass the rate limiter.
- **CORS**:
  - Configurable via `Cors:AllowedOrigins` (default: `http://localhost:3000,http://localhost:5173`).
  - Supports credentialed requests, custom headers (`X-Correlation-Id`, `Authorization`), and preflight requests.
- **Health Check**:
  - `GET /health`: Returns `{ "status": "UP", "service": "LEXOR .NET YARP API Gateway", "version": "1.0.0" }`.

---

## 5. Configuration & Environment Variables

| Variable / Setting | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8081` | Port on which the gateway listens |
| `Jwt:Secret` | *(Default 32-char key)* | Secret key for JWT verification |
| `Jwt:Issuer` | `LEXOR` | Token issuer validation |
| `Jwt:Audience` | `LEXOR` | Token audience validation |
| `Cors:AllowedOrigins`| `http://localhost:3000,http://localhost:5173` | Allowed frontend origins |
| `RateLimiting:PermitLimit` | `50` | Number of requests allowed per window |
| `RateLimiting:WindowSeconds`| `1` | Window duration in seconds |

---

## 6. Build and Test Verification

### Run Automated Tests
```bash
cd backend/dotnet/api-gateway
dotnet test
```
**Results**:
- Tests run: **12**
- Failures: **0**
- Errors: **0**
- Skipped: **0**

### Run Locally
```bash
cd backend/dotnet/api-gateway
dotnet run
```
