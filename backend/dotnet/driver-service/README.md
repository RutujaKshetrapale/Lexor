# LEXOR ASP.NET Core Driver Service

**Microservice Name:** `driver-service`  
**Platform:** LEXOR — AI-Powered Smart Mobility Platform  
**Default Port:** `8086`  
**Technology Stack:** .NET 9.0 (ASP.NET Core Web API), Entity Framework Core 9, Pomelo MySQL, JWT Bearer Authentication, Swashbuckle OpenAPI / Swagger  
**Reference Parity:** Spring Boot Driver Service (`backend/springboot/driver-service/`)

---

## 1. Overview & Architecture

The **LEXOR ASP.NET Core Driver Service** provides enterprise-grade driver lifecycle management, self-service onboarding, secure multipart document uploads, multi-step verification workflows, and administrative compliance review. It operates as the .NET counterpart to the Spring Boot Driver Service, sharing the identical physical MySQL database (`lexor_db`) and JWT authentication contract.

### Core Domain Capabilities:
1. **Driver Profile Management**:
   - Identity retrieval via central `users` table linked to `driver_profiles`.
   - Full (`PUT`) and partial (`PATCH`) driver profile updates.
   - Separate separation of user account status, driver onboarding state, and online availability.
2. **Onboarding & Eligibility Engine**:
   - Real-time onboarding progress score and dynamic compliance checklist.
   - Validation of driver legal age (≥ 18 years) and license validity.
   - Gated profile submission for administrative review.
3. **Secure Document Management**:
   - Multipart file upload supporting `PDF`, `JPEG`, `PNG`, and `WEBP`.
   - Binary signature (magic byte) detection to prevent content-sniffing and file extension spoofing.
   - Private filesystem storage outside the web root with UUID-based keys.
   - Path-traversal defense and safe document streaming for authorized owners and reviewers.
   - Automatic versioning (`v1 -> v2`) and reset of approvals upon document resubmission.
4. **Administrative Compliance Review**:
   - Filterable driver list and queue of documents awaiting compliance inspection.
   - Fine-grained document approval, rejection with mandatory reasons, or resubmission request.
   - Driver-level verification decision enforcing prerequisite checks.
   - Administrative suspension and reinstatement with complete audit logging.

---

## 2. Configuration & Environment Variables

Configure via environment variables or `appsettings.json`:

| Environment Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8086` | HTTP listening port for Kestrel |
| `ConnectionStrings__DefaultConnection` | `Server=localhost;Port=3306;Database=lexor_db;Uid=root;Pwd=root;...` | MySQL connection string |
| `LEXOR_JWT_SECRET` | *(64-char hex key)* | HMAC-SHA256 signing secret shared across LEXOR services |
| `Jwt__Issuer` | `LEXOR` | Expected JWT issuer |
| `Jwt__Audience` | `LEXOR` | Expected JWT audience |
| `Storage__Location` | `uploads/documents` | Filesystem storage path for uploaded documents |
| `Storage__AllowedExtensions` | `pdf,jpg,jpeg,png,webp` | Permitted document extensions |
| `Storage__MaxFileSizeBytes` | `10485760` (10 MB) | Maximum permitted file upload size |
| `Onboarding__RequiredDocuments` | `DRIVING_LICENSE,GOVERNMENT_ID,ADDRESS_PROOF` | Comma-delimited list of mandatory document types |

---

## 3. API Route Map

### Driver Self-Service (`ROLE_DRIVER`)

- `GET /api/v1/drivers/me` — Retrieve authenticated driver's profile and metrics.
- `PUT /api/v1/drivers/me` — Fully update license, address, and contact details.
- `PATCH /api/v1/drivers/me` — Partially update profile without clearing omitted fields.
- `GET /api/v1/drivers/me/onboarding` — Onboarding completion score, checklist, and tasks.
- `POST /api/v1/drivers/me/submit-for-review` — Submit profile for administrative verification.
- `PATCH /api/v1/drivers/me/availability` — Toggle `ONLINE` / `OFFLINE` status (gated by `VERIFIED` & `ACTIVE`).
- `GET /api/v1/drivers/me/documents` — List current compliance documents.
- `POST /api/v1/drivers/me/documents` — Upload a multipart document (`multipart/form-data`).
- `GET /api/v1/drivers/me/documents/{documentId}` — View document metadata.
- `GET /api/v1/drivers/me/documents/{documentId}/download` — Securely stream binary document file.
- `POST /api/v1/drivers/me/documents/{documentId}/resubmit` — Resubmit rejected document.
- `DELETE /api/v1/drivers/me/documents/{documentId}` — Soft-delete unverified document.

### Administrative Compliance Review (`ROLE_ADMIN`)

- `GET /api/v1/admin/drivers` — Paginated driver list with filters (status, onboarding, search).
- `GET /api/v1/admin/drivers/{driverUuid}` — Retrieve driver administrative profile.
- `GET /api/v1/admin/drivers/{driverUuid}/documents` — View all driver documents (including historical versions).
- `GET /api/v1/admin/driver-documents` — Paginated queue of documents awaiting review.
- `GET /api/v1/admin/driver-documents/{documentId}/download` — Securely inspect document file.
- `PATCH /api/v1/admin/driver-documents/{documentId}/status` — Verify, reject, or request resubmission.
- `GET /api/v1/admin/drivers/{driverUuid}/verification-history` — Full audit history log.
- `PATCH /api/v1/admin/drivers/{driverUuid}/verification-status` — Apply driver verification decision.
- `PATCH /api/v1/admin/drivers/{driverUuid}/suspension` — Administratively suspend or reinstate driver.

---

## 4. How to Run & Test

### Run Unit Tests
```bash
dotnet test backend/dotnet/driver-service/DriverService.csproj
```

### Run Locally (Port 8086)
```bash
cd backend/dotnet/driver-service
dotnet run
```

### Access Swagger UI
Open your browser at:
`http://localhost:8086/swagger`
