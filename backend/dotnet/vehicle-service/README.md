# LEXOR ASP.NET Core Vehicle Service

**Microservice Name:** `vehicle-service`  
**Platform:** LEXOR — AI-Powered Smart Mobility Platform  
**Default Port:** `8088`  
**Technology Stack:** .NET 9.0 (ASP.NET Core Web API), Entity Framework Core 9, Pomelo MySQL, JWT Bearer Authentication, Swashbuckle OpenAPI / Swagger  
**Reference Parity:** Spring Boot Vehicle Service (`backend/springboot/vehicle-service/`, Port 8087)

---

## 1. Overview & Architecture

The **LEXOR ASP.NET Core Vehicle Service** provides enterprise vehicle lifecycle management, driver vehicle registration, secure multipart document uploads, multi-step verification workflows, administrative review, operational suspension/reinstatement, and driver-vehicle assignments. It operates as the high-performance .NET counterpart to the Spring Boot Vehicle Service, sharing the identical MySQL database (`lexor_db`) and JWT authentication model.

### Core Domain Capabilities:
1. **Vehicle Fleet & Driver Ownership**:
   - Register vehicles with automatic plate normalization (e.g., `KA-01-EQ-4829`) and unique VIN checks.
   - Strict driver ownership boundaries: driver identity is derived from validated JWT claims (`sub` / `userId`), preventing ID spoofing.
   - Cross-driver access protection (HTTP 403 Forbidden).
   - Vehicle lifecycle states: `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`, `SUSPENDED`.
2. **Secure Compliance Document Storage**:
   - Multipart document upload (`REGISTRATION_CERTIFICATE`, `VEHICLE_INSURANCE`, `POLLUTION_UNDER_CONTROL`, `VEHICLE_PERMIT`, `FITNESS_CERTIFICATE`, `OTHER`).
   - Magic-byte file inspection (validates true binary signatures for PDF `%PDF`, JPEG `FF D8 FF`, PNG `89 50 4E 47`, and WEBP `RIFF...WEBP`).
   - Prevention of file extension spoofing, path traversal (`..`), and oversized uploads.
   - Versioning (`v1 -> v2`) upon document resubmission, archiving previous versions.
   - Secure authenticated streaming downloads with sanitized headers and no physical server paths exposed.
3. **Administrative Compliance & Audit**:
   - Paginated vehicle fleet listing with verification status, operational status, and search filters.
   - Dedicated pending verification queue.
   - Mandatory document policy enforcement: Vehicle approval requires `REGISTRATION_CERTIFICATE`, `VEHICLE_INSURANCE`, and `POLLUTION_UNDER_CONTROL` to be in `VERIFIED` status.
   - Complete audit trail logging in `vehicle_verification_history` for document reviews, approvals, rejections, suspensions, and reinstatements.
4. **Driver-Vehicle Assignments**:
   - Assign verified drivers to verified, active vehicles.
   - Pre-assignment validation: ensures driver is verified (`is_verified = true`, `onboarding_status = VERIFIED`) and not suspended.
   - Automatic termination/deactivation of previous active assignments upon reassignment or vehicle suspension.
   - Full historical audit trail in `driver_vehicle_assignments`.

---

## 2. Configuration & Environment Variables

Configure via environment variables or `appsettings.json`:

| Environment Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8088` | HTTP listening port for Kestrel |
| `ConnectionStrings__DefaultConnection` | `Server=localhost;Port=3306;Database=lexor_db;Uid=root;...` | MySQL connection string |
| `LEXOR_JWT_SECRET` | *(Supplied via env)* | HMAC-SHA256 signing secret shared across LEXOR services |
| `Jwt__Issuer` | `LEXOR` | Expected JWT issuer |
| `Jwt__Audience` | `LEXOR` | Expected JWT audience |
| `Jwt__ExpirationMinutes` | `1440` (24h) | JWT validity window |
| `Storage__Location` | `uploads/vehicle-documents` | Filesystem storage path for uploaded vehicle documents |
| `Storage__AllowedExtensions` | `pdf,jpg,jpeg,png,webp` | Permitted document extensions |
| `Storage__MaxFileSizeBytes` | `10485760` (10 MB) | Maximum permitted file upload size |
| `VerificationPolicy__RequiredDocuments` | `REGISTRATION_CERTIFICATE,VEHICLE_INSURANCE,POLLUTION_UNDER_CONTROL` | Comma-delimited list of mandatory compliance documents |

---

## 3. Database Compatibility & Migration Prerequisites

The service shares `lexor_db` with Spring Boot Vehicle Service and the rest of the LEXOR platform.

### Tables Mapped:
- `users` — Platform users and credentials
- `driver_profiles` — Driver records and onboarding verification states
- `vehicles` — Vehicle fleet records, fuel types, verification states, and reviewer references
- `vehicle_documents` — Document metadata, binary storage references, and verification status
- `vehicle_verification_history` — Comprehensive audit decisions and admin rationale
- `driver_vehicle_assignments` — Active and historical driver-vehicle linkages

### Migration Prerequisite:
Ensure migration `database/mysql/migrations/V4__vehicle_service_additive.sql` has been executed on `lexor_db` prior to running against live MySQL. The service never runs destructive operations or automatic DDL schema alterations against shared databases.

---

## 4. API Route Map

### Driver Operations (`ROLE_DRIVER`)
Base Path: `/api/v1/vehicles`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/vehicles` | Register a new vehicle under authenticated driver |
| `GET` | `/api/v1/vehicles` | List authenticated driver's vehicles |
| `GET` | `/api/v1/vehicles/{vehicleUuid}` | Get vehicle details (subject to ownership) |
| `PUT` | `/api/v1/vehicles/{vehicleUuid}` | Update permitted vehicle details |
| `PATCH` | `/api/v1/vehicles/{vehicleUuid}/status` | Toggle operational active/inactive status (requires `VERIFIED`) |
| `POST` | `/api/v1/vehicles/{vehicleUuid}/submit-verification` | Submit vehicle for administrative review |
| `GET` | `/api/v1/vehicles/{vehicleUuid}/verification-status` | Get verification checklist and missing requirements |
| `GET` | `/api/v1/vehicles/{vehicleUuid}/verification-history` | View verification audit history |
| `GET` | `/api/v1/vehicles/{vehicleUuid}/assignments` | View driver assignment history |
| `POST` | `/api/v1/vehicles/{vehicleUuid}/documents` | Upload multipart compliance document |
| `GET` | `/api/v1/vehicles/{vehicleUuid}/documents` | List current compliance documents |
| `GET` | `/api/v1/vehicles/{vehicleUuid}/documents/{documentId}` | Get document metadata by ID |
| `GET` | `/api/v1/vehicles/{vehicleUuid}/documents/{documentId}/download` | Securely download document binary stream |
| `POST` | `/api/v1/vehicles/{vehicleUuid}/documents/{documentId}/resubmit` | Resubmit document (increments version) |
| `DELETE` | `/api/v1/vehicles/{vehicleUuid}/documents/{documentId}` | Soft-delete unverified document |

### Admin Operations (`ROLE_ADMIN`)
Base Path: `/api/v1/admin/vehicles`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/admin/vehicles` | Paginated vehicle fleet list with status/search filters |
| `GET` | `/api/v1/admin/vehicles/pending` | Paginated queue of vehicles awaiting verification |
| `GET` | `/api/v1/admin/vehicles/{vehicleUuid}` | Get full vehicle details |
| `GET` | `/api/v1/admin/vehicles/{vehicleUuid}/documents` | List vehicle compliance documents |
| `GET` | `/api/v1/admin/vehicles/{vehicleUuid}/documents/{documentId}/download` | Securely stream vehicle document for review |
| `PATCH` | `/api/v1/admin/vehicles/{vehicleUuid}/documents/{documentId}/review` | Review individual document (`VERIFIED`, `REJECTED`, `RESUBMISSION_REQUIRED`) |
| `PATCH` | `/api/v1/admin/vehicles/{vehicleUuid}/verification` | Review vehicle (`VERIFIED` requires mandatory docs; `REJECTED` requires reason) |
| `PATCH` | `/api/v1/admin/vehicles/{vehicleUuid}/suspension` | Administratively suspend or reinstate vehicle |
| `GET` | `/api/v1/admin/vehicles/{vehicleUuid}/verification-history` | View vehicle verification audit history |
| `POST` | `/api/v1/admin/vehicles/{vehicleUuid}/assignments` | Assign verified driver to vehicle |
| `DELETE` | `/api/v1/admin/vehicles/{vehicleUuid}/assignments/{assignmentId}` | Terminate driver-vehicle assignment |
| `GET` | `/api/v1/admin/vehicles/{vehicleUuid}/assignments` | View driver-vehicle assignment history |

---

## 5. How to Run & Test

### Run Automated Tests
```bash
dotnet test backend/dotnet/vehicle-service/VehicleService.csproj
```

### Run Locally (Port 8088)
```bash
cd backend/dotnet/vehicle-service
dotnet run
```

### Access Swagger UI
When running locally, open:
```
http://localhost:8088/swagger
```

---

## 6. Manual Test Sequence

1. **Obtain JWT Tokens**:
   - Authenticate as driver via Auth Service (`POST http://localhost:8082/api/v1/auth/login`) -> obtain `DRIVER_TOKEN`.
   - Authenticate as admin (`POST http://localhost:8082/api/v1/auth/login`) -> obtain `ADMIN_TOKEN`.

2. **Register Vehicle (as Driver)**:
   ```bash
   curl -X POST http://localhost:8088/api/v1/vehicles \
     -H "Authorization: Bearer $DRIVER_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "make": "Toyota",
       "model": "Urban Cruiser Hyryder",
       "year": 2023,
       "color": "Midnight Black",
       "licensePlate": "KA-01-EQ-4829",
       "rideTypeCategory": "SEDAN",
       "seatCapacity": 4,
       "fuelType": "HYBRID"
     }'
   ```

3. **Upload Compliance Documents**:
   ```bash
   curl -X POST http://localhost:8088/api/v1/vehicles/$VEHICLE_UUID/documents \
     -H "Authorization: Bearer $DRIVER_TOKEN" \
     -F "documentType=REGISTRATION_CERTIFICATE" \
     -F "documentNumber=RC-KA01-4829" \
     -F "file=@rc.pdf"
   ```

4. **Submit for Verification**:
   ```bash
   curl -X POST http://localhost:8088/api/v1/vehicles/$VEHICLE_UUID/submit-verification \
     -H "Authorization: Bearer $DRIVER_TOKEN"
   ```

5. **Admin Review & Approval**:
   - Admin reviews document:
     ```bash
     curl -X PATCH http://localhost:8088/api/v1/admin/vehicles/$VEHICLE_UUID/documents/$DOC_ID/review \
       -H "Authorization: Bearer $ADMIN_TOKEN" \
       -H "Content-Type: application/json" \
       -d '{"status": "VERIFIED", "reviewNotes": "RC checked against Vahan"}'
     ```
   - Admin approves vehicle:
     ```bash
     curl -X PATCH http://localhost:8088/api/v1/admin/vehicles/$VEHICLE_UUID/verification \
       -H "Authorization: Bearer $ADMIN_TOKEN" \
       -H "Content-Type: application/json" \
       -d '{"status": "VERIFIED", "notes": "Approved for active dispatch"}'
     ```

6. **Admin Assigns Driver**:
   ```bash
   curl -X POST http://localhost:8088/api/v1/admin/vehicles/$VEHICLE_UUID/assignments \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"driverUuid": "'"$DRIVER_UUID"'", "reason": "Fleet vehicle deployment"}'
   ```
