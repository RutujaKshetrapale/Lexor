# LEXOR — Ride Type Microservice (.NET)

The **Ride Type Service** is the ASP.NET Core 9 / EF Core microservice managing the catalogue of ride categories, fare rules, and vehicle tiers across the LEXOR AI-Powered Smart Mobility Platform.

---

## 1. Service Overview

- **Service Name**: `ride-type-service`
- **Port**: `8090`
- **Runtime**: .NET 9.0 / C# 13 / ASP.NET Core Web API
- **ORM**: Entity Framework Core 9.0 (Pomelo MySQL)
- **Testing**: xUnit, Moq, EF Core InMemory Database
- **Security**: Stateless JWT Bearer Authentication compatible with LEXOR Auth Service
- **Documentation**: OpenAPI 3 / Swagger UI (`/swagger`)

---

## 2. API Endpoints

### 2.1 Rider & Driver Catalogue Endpoints

Requires valid JWT Bearer token (`RIDER`, `DRIVER`, or `ADMIN`).

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/ride-types` | `AUTHENTICATED` | Retrieve list of all currently active/enabled ride categories |
| `GET` | `/api/v1/ride-types/{rideTypeUuid}` | `AUTHENTICATED` | Retrieve details for a single active ride type by public UUID (returns 404 if inactive) |

### 2.2 Administrator Endpoints

Requires valid JWT Bearer token with `ROLE_ADMIN` or `ADMIN`.

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/admin/ride-types` | `ADMIN` | Create a new ride category with fare pricing rules |
| `GET` | `/api/v1/admin/ride-types` | `ADMIN` | List all ride categories (including disabled entries) |
| `GET` | `/api/v1/admin/ride-types/{rideTypeUuid}` | `ADMIN` | Retrieve full administrative details of any ride type |
| `PUT` | `/api/v1/admin/ride-types/{rideTypeUuid}` | `ADMIN` | Update permitted fields (name, description, fares, capacity, icon) |
| `PATCH` | `/api/v1/admin/ride-types/{rideTypeUuid}/status` | `ADMIN` | Enable or disable a ride category (protects last active category) |
| `DELETE` | `/api/v1/admin/ride-types/{rideTypeUuid}` | `ADMIN` | Protected endpoint rejecting hard deletion to preserve booking integrity |

---

## 3. Database Architecture & Additive Migration (V5)

The service maps the canonical `ride_types` table in MySQL (`lexor_db`).

### Schema Definition
```sql
CREATE TABLE IF NOT EXISTS ride_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(30) NOT NULL UNIQUE,
    description TEXT DEFAULT NULL,
    base_fare DECIMAL(10, 2) NOT NULL,
    per_km_rate DECIMAL(10, 2) NOT NULL,
    per_minute_rate DECIMAL(10, 2) NOT NULL,
    minimum_fare DECIMAL(10, 2) NOT NULL,
    cancellation_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    capacity TINYINT UNSIGNED NOT NULL DEFAULT 4,
    icon_url VARCHAR(500) DEFAULT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ride_types_uuid (uuid),
    INDEX idx_ride_types_code (code),
    INDEX idx_ride_types_active (is_active)
);
```

### Additive Migration Prerequisite (V5)
Ensure migration `database/mysql/migrations/V5__ride_type_service_additive.sql` is applied:
```bash
mysql -u root -p lexor_db < database/mysql/migrations/V5__ride_type_service_additive.sql
```
*Note: The service does not run automatic DDL migrations at startup (`EnsureCreated()` / `Migrate()` are disabled).*

---

## 4. Configuration & Environment Variables

Copy `.env.example` to configure:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8090` | HTTP server listening port |
| `DB_HOST` | `localhost` | MySQL database host |
| `DB_PORT` | `3306` | MySQL database port |
| `DB_NAME` | `lexor_db` | MySQL database name |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | `21011017` | Database password |
| `ConnectionStrings__DefaultConnection` | *(constructed)* | Custom MySQL connection string |
| `LEXOR_JWT_SECRET` | *(256-bit hex)* | HMAC-SHA secret key shared with Auth Service |
| `JWT_ISSUER` | `LEXOR` | Token issuer |
| `JWT_AUDIENCE` | `LEXOR` | Token audience |

---

## 5. Security & Parity with Spring Boot

1. **Stateless JWT Validation**: Validates cryptographic signature using the shared HMAC-SHA key. Extracts UUID from `sub` and roles from `role` claims.
2. **Role Mapping Parity**: Automatically normalizes roles between `ROLE_ADMIN` and `ADMIN`, `ROLE_RIDER` and `RIDER`, `ROLE_DRIVER` and `DRIVER`.
3. **Status Enforcement**: `UserStatusMiddleware` checks user account status and rejects `SUSPENDED` or `DEACTIVATED` users.
4. **Referential Integrity Protection**: Hard deletion of ride categories is rejected (400 Bad Request) to protect bookings (`bookings.ride_type_id`) and completed rides (`rides.ride_type_id`). Categories can only be disabled.
5. **Business Invariant Protection**: Cannot disable the last remaining active ride category in the platform.

---

## 6. Building and Testing

### Build the Project
```bash
dotnet build
```

### Run Tests
```bash
dotnet test
```

### Run Locally
```bash
dotnet run
```

### Swagger Documentation
Once running, navigate to:
- **Swagger UI**: [http://localhost:8090/swagger](http://localhost:8090/swagger)
- **OpenAPI JSON**: [http://localhost:8090/swagger/v1/swagger.json](http://localhost:8090/swagger/v1/swagger.json)

---

## 7. Manual Testing Guide (cURL)

### 7.1 Login via Auth Service
```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@lexor.mobility","password":"AdminPassword123!"}'
```

### 7.2 Get Available Ride Types
```bash
curl -X GET http://localhost:8090/api/v1/ride-types \
  -H "Authorization: Bearer <TOKEN>"
```

### 7.3 Create a Ride Type (Admin)
```bash
curl -X POST http://localhost:8090/api/v1/admin/ride-types \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "LEXOR Electric",
    "code": "ELECTRIC",
    "description": "Zero-emission pure EV mobility",
    "baseFare": 65.00,
    "perKmRate": 17.00,
    "perMinuteRate": 2.90,
    "minimumFare": 95.00,
    "cancellationFee": 35.00,
    "capacity": 4,
    "iconUrl": "https://cdn.lexor.mobility/icons/electric.png",
    "isActive": true
  }'
```

### 7.4 Disable Ride Type (Admin)
```bash
curl -X PATCH http://localhost:8090/api/v1/admin/ride-types/<UUID>/status \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"isActive": false, "reason": "Fleet maintenance"}'
```
