# LEXOR — Ride Type Microservice

The **Ride Type Service** manages the catalogue of ride categories available to riders and the fare calculation parameters and vehicle tier rules that determine which categories can be booked across the LEXOR AI-Powered Smart Mobility Platform.

---

## 1. Service Overview

- **Service Name**: `ride-type-service`
- **Port**: `8089`
- **Runtime**: Java 21 / Spring Boot 3.2.5
- **Database**: MySQL 8.0 (Production / Dev) / H2 in MySQL mode (Automated Testing)
- **Security**: Stateless JWT Authentication compatible with LEXOR Auth Service
- **Documentation**: OpenAPI 3 / Swagger UI (`/swagger-ui.html`)

---

## 2. API Endpoints

### 2.1 Rider & Driver Catalogue Endpoints

Requires valid JWT Bearer token (`RIDER`, `DRIVER`, or `ADMIN`).

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/ride-types` | `AUTHENTICATED` | Retrieve list of all currently active/enabled ride categories |
| `GET` | `/api/v1/ride-types/{rideTypeUuid}` | `AUTHENTICATED` | Retrieve details for a single active ride type by public UUID (returns 404 if inactive) |

### 2.2 Administrator Endpoints

Requires valid JWT Bearer token with `ROLE_ADMIN`.

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/admin/ride-types` | `ADMIN` | Create a new ride type category with fare pricing rules |
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

### Migration Instructions (V5 Additive Migration)
If applying to an existing MySQL instance running prior schema versions, run:
```bash
mysql -u root -p lexor_db < database/mysql/migrations/V5__ride_type_service_additive.sql
```
This migration safely:
1. Adds `uuid VARCHAR(36) NULL AFTER id`
2. Assigns deterministic UUIDs to seed categories (`GO`, `XL`, `PREMIER`, `GREEN`)
3. Populates `UUID()` for existing records
4. Enforces `NOT NULL` and `UNIQUE KEY uq_ride_types_uuid`

---

## 4. Configuration & Environment Variables

Copy `.env.example` to configure local or deployment environments:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8089` | HTTP server listening port |
| `DB_HOST` | `localhost` | MySQL database host |
| `DB_PORT` | `3306` | MySQL database port |
| `DB_NAME` | `lexor_db` | MySQL database schema name |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | *(None / Required)* | Database password (set via env var) |
| `DB_URL` | *(constructed)* | Custom JDBC connection string |
| `JWT_SECRET` | *(256-bit hex)* | HMAC-SHA secret key shared with Auth Service |
| `JWT_EXPIRATION_MS` | `86400000` | Token expiration in milliseconds (24h) |
| `LOG_LEVEL_ROOT` | `INFO` | Root logging level |
| `LOG_LEVEL_RIDETYPE`| `DEBUG` | Microservice logger level |

---

## 5. Security & Architectural Decisions

1. **Stateless JWT Validation**: Tokens are issued by the centralized Auth Service (`auth-service` on port 8081). The Ride Type service validates token cryptographic signatures using the shared HMAC-SHA secret and extracts the user's UUID and authorities.
2. **Role Mapping**: Follows standard Spring Security `ROLE_` prefix conventions (`ROLE_ADMIN`, `ROLE_RIDER`, `ROLE_DRIVER`).
3. **Public Exposure via UUID**: Never exposes internal auto-increment database primary keys (`id`) in external REST APIs.
4. **Referential Integrity Protection**: Hard deletion of ride categories is forbidden to preserve historical referential integrity with bookings (`bookings.ride_type_id`) and completed rides (`rides.ride_type_id`). Categories are toggled active/inactive via `PATCH /api/v1/admin/ride-types/{uuid}/status`.
5. **Business Invariant Protection**: An administrator cannot disable the last remaining active ride category in the platform.

---

## 6. Building and Running

### Run Automated Tests
```bash
mvn clean test
```

### Run Service Locally
```bash
mvn spring-boot:run
```

### Package Production Jar
```bash
mvn clean package -DskipTests
```

### Swagger Documentation
Once running, navigate to:
- **Swagger UI**: [http://localhost:8089/swagger-ui.html](http://localhost:8089/swagger-ui.html)
- **OpenAPI v3 JSON**: [http://localhost:8089/v3/api-docs](http://localhost:8089/v3/api-docs)

---

## 7. Manual Testing Guide (cURL)

### 7.1 Authenticate and Obtain JWT
```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@lexor.mobility","password":"AdminPassword123!"}'
```

### 7.2 Get Enabled Ride Types (Rider / Driver)
```bash
curl -X GET http://localhost:8089/api/v1/ride-types \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### 7.3 Create a Ride Type (Admin)
```bash
curl -X POST http://localhost:8089/api/v1/admin/ride-types \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "LEXOR Pet",
    "code": "PET",
    "description": "Pet-friendly rides with certified drivers",
    "baseFare": 70.00,
    "perKmRate": 18.00,
    "perMinuteRate": 3.00,
    "minimumFare": 100.00,
    "cancellationFee": 40.00,
    "capacity": 4,
    "iconUrl": "https://cdn.lexor.mobility/icons/pet.png",
    "isActive": true
  }'
```

### 7.4 Disable a Ride Type (Admin)
```bash
curl -X PATCH http://localhost:8089/api/v1/admin/ride-types/<RIDE_TYPE_UUID>/status \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"isActive": false, "reason": "Seasonal category deactivation"}'
```
