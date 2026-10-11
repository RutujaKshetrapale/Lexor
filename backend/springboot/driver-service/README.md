# LEXOR — Driver Microservice (Spring Boot)

Production-ready Driver Profile, Onboarding, Secure Document Management, and Administrative Verification Microservice for **LEXOR — AI-Powered Smart Mobility Platform**.

---

## 1. Overview and Boundaries

The Driver Service manages driver self-service profile workflows, multi-step onboarding, secure document upload/verification, and administrative compliance review.

### Service Boundaries:
- **Auth Service (`port: 8081`)**: User identity registration, credential verification, and JWT issuance.
- **User Service (`port: 8083`)**: Central user identity records (`users`), profile updates, and admin user provisioning.
- **Driver Service (`port: 8085`)**:
  - Driver profile management (`driver_profiles`) and personal/address/licence details.
  - Multipart document upload, content inspection (magic bytes), and secure private filesystem storage.
  - Multi-step onboarding calculation and dynamic task checklist (`INCOMPLETE`, `PENDING_REVIEW`, `UNDER_REVIEW`, `VERIFIED`, `REJECTED`, `SUSPENDED`).
  - Driver availability toggle (`ONLINE` / `OFFLINE`), enforcing that only fully `VERIFIED` drivers with `ACTIVE` accounts can go online.
  - Administrative compliance reviews, document approvals/rejections, driver-level verification decisions, driver suspension, and full audit logs (`driver_verification_history`).

---

## 2. Architecture & Package Structure

Built with a clean layered architecture following LEXOR enterprise patterns:

```
backend/springboot/driver-service/
├── src/main/java/com/lexor/driver/
│   ├── DriverServiceApplication.java          # Spring Boot Application Entry Point
│   ├── config/
│   │   ├── SecurityConfig.java              # Spring Security & Stateless JWT Filter Chain
│   │   ├── OpenApiConfig.java               # Swagger OpenAPI 3.0 Documentation Config
│   │   └── OnboardingPolicyConfig.java      # Configurable Required Compliance Documents
│   ├── controller/
│   │   ├── DriverProfileController.java     # /api/v1/drivers/me (Profile, Onboarding, Availability)
│   │   ├── DriverDocumentController.java    # /api/v1/drivers/me/documents (Upload, Download, Resubmit)
│   │   └── AdminDriverController.java       # /api/v1/admin/drivers & /driver-documents
│   ├── dto/
│   │   ├── EmergencyContactDto.java         # Emergency contact data
│   │   ├── request/                         # Validated Request DTOs
│   │   │   ├── UpdateDriverProfileRequest.java
│   │   │   ├── PatchDriverProfileRequest.java
│   │   │   ├── ReviewDocumentRequest.java
│   │   │   ├── VerifyDriverRequest.java
│   │   │   ├── DriverSuspensionRequest.java
│   │   │   └── DriverAvailabilityRequest.java
│   │   └── response/                        # Safe Response DTOs
│   │       ├── DriverProfileResponse.java
│   │       ├── DriverOnboardingResponse.java
│   │       ├── DocumentResponse.java
│   │       ├── DocumentRequirementStatus.java
│   │       ├── DriverAvailabilityResponse.java
│   │       ├── VerificationHistoryResponse.java
│   │       ├── PagedResponse.java
│   │       └── ErrorResponse.java
│   ├── entity/                              # JPA Entities mapped to lexor_db
│   │   ├── User.java                        # users table mapping
│   │   ├── Role.java                        # RIDER, DRIVER, ADMIN
│   │   ├── UserStatus.java                  # PENDING, ACTIVE, SUSPENDED, DEACTIVATED
│   │   ├── DriverProfile.java               # driver_profiles table mapping
│   │   ├── DriverStatus.java                # OFFLINE, ONLINE, IN_TRIP, SUSPENDED
│   │   ├── OnboardingStatus.java            # INCOMPLETE, PENDING_REVIEW, UNDER_REVIEW, VERIFIED, REJECTED, SUSPENDED
│   │   ├── BackgroundCheckStatus.java       # PENDING, PASSED, FAILED
│   │   ├── DriverDocument.java              # driver_documents table mapping
│   │   ├── DocumentType.java                # DRIVING_LICENSE, GOVERNMENT_ID, ADDRESS_PROOF, etc.
│   │   ├── DocumentVerificationStatus.java  # PENDING_REVIEW, UNDER_REVIEW, VERIFIED, REJECTED, etc.
│   │   ├── DriverVerificationHistory.java   # driver_verification_history audit table
│   │   └── EmergencyContact.java            # emergency_contacts table mapping
│   ├── exception/                           # Custom Business Exceptions & GlobalExceptionHandler
│   │   ├── BadRequestException.java
│   │   ├── DuplicateResourceException.java
│   │   ├── ForbiddenException.java
│   │   ├── InvalidStatusTransitionException.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── FileStorageException.java
│   │   └── GlobalExceptionHandler.java
│   ├── repository/                          # Spring Data JPA Repositories
│   │   ├── UserRepository.java
│   │   ├── DriverProfileRepository.java
│   │   ├── DriverDocumentRepository.java
│   │   ├── DriverVerificationHistoryRepository.java
│   │   └── EmergencyContactRepository.java
│   ├── security/                            # JWT Validation & Security Filter
│   │   ├── CustomUserDetailsService.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtTokenProvider.java
│   │   └── UserPrincipal.java
│   ├── service/                             # Business Logic Contracts & Implementations
│   │   ├── DriverProfileService.java
│   │   ├── DriverDocumentService.java
│   │   ├── DriverVerificationService.java
│   │   ├── OnboardingPolicyService.java
│   │   └── impl/
│   │       ├── DriverProfileServiceImpl.java
│   │       ├── DriverDocumentServiceImpl.java
│   │       ├── DriverVerificationServiceImpl.java
│   │       └── OnboardingPolicyServiceImpl.java
│   └── storage/                             # Secure Storage Abstraction
│       ├── DocumentStorageService.java
│       ├── FileSystemDocumentStorageService.java
│       └── StoredDocument.java
├── src/main/resources/                      # application.yml
├── src/test/java/                           # Automated JUnit 5 & MockMvc Tests (54 passed)
├── src/test/resources/                      # application-test.yml (H2 In-Memory)
├── pom.xml                                  # Maven dependencies and build plugin
└── .env.example                             # Environment variable template
```

---

## 3. Tech Stack & Compatibility

- **Java Version**: Java 21
- **Spring Boot**: 3.2.5
- **Build Tool**: Apache Maven
- **Database**: MySQL 8.0 / 8.2 (`lexor_db`)
- **JPA Mode**: `ddl-auto=validate` (Zero runtime schema modification)
- **Security**: Spring Security 6.x + JJWT (`0.12.5`)
- **Documentation**: Springdoc OpenAPI / Swagger UI (`2.5.0`)
- **Testing**: JUnit 5, Mockito, Spring Security Test, H2 In-Memory Database
- **Port**: `8085` (Configurable via `PORT`)

---

## 4. Database Schema Mapping & Additive Migration

The database schema matches `database/mysql/schema.sql` (26 tables) and `migrations/V3__driver_service_additive.sql`:

### 1. `driver_profiles` (Extended)
- `user_id`: BIGINT NOT NULL UNIQUE (FK -> `users.id`)
- `license_number`: VARCHAR(50) NULL UNIQUE
- `license_expiry_date`: DATE NULL
- `license_issue_date`: DATE NULL
- `license_issuing_authority`: VARCHAR(100) NULL
- `license_issuing_state`: VARCHAR(100) NULL
- `license_issuing_country`: VARCHAR(100) NULL DEFAULT 'India'
- `license_class`: VARCHAR(50) NULL DEFAULT 'LMV'
- `date_of_birth`: DATE NULL
- `residential_address`: VARCHAR(255) NULL
- `city`, `state`, `postal_code`, `country`: VARCHAR
- `preferred_language`: VARCHAR(50) DEFAULT 'English'
- `status`: ENUM('OFFLINE', 'ONLINE', 'IN_TRIP', 'SUSPENDED') DEFAULT 'OFFLINE'
- `rating`: DECIMAL(3,2) DEFAULT 5.00
- `total_trips`: INT UNSIGNED DEFAULT 0
- `acceptance_rate`: DECIMAL(5,2) DEFAULT 100.00
- `cancellation_rate`: DECIMAL(5,2) DEFAULT 0.00
- `is_verified`: BOOLEAN DEFAULT FALSE
- `verified_at`: TIMESTAMP NULL
- `background_check_status`: ENUM('PENDING', 'PASSED', 'FAILED') DEFAULT 'PENDING'
- `onboarding_status`: ENUM('INCOMPLETE', 'PENDING_REVIEW', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'SUSPENDED') DEFAULT 'INCOMPLETE'
- `rejection_reason`: VARCHAR(500) NULL

### 2. `driver_documents` (Table 25)
- `id`: BIGINT PRIMARY KEY AUTO_INCREMENT
- `driver_id`: BIGINT NOT NULL (FK -> `driver_profiles.id` ON DELETE CASCADE)
- `document_type`: ENUM('DRIVING_LICENSE', 'GOVERNMENT_ID', 'ADDRESS_PROOF', 'PROFILE_PHOTO', 'BACKGROUND_CHECK', 'OTHER') NOT NULL
- `document_number`: VARCHAR(100) NULL
- `file_name`: VARCHAR(255) NOT NULL
- `file_path`: VARCHAR(500) NOT NULL (UUID filename on disk outside web root)
- `content_type`: VARCHAR(100) NOT NULL
- `file_size`: BIGINT NOT NULL
- `issue_date`: DATE NULL
- `expiry_date`: DATE NULL
- `issuing_authority`: VARCHAR(100) NULL
- `verification_status`: ENUM('PENDING_REVIEW', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'EXPIRED', 'RESUBMISSION_REQUIRED') NOT NULL DEFAULT 'PENDING_REVIEW'
- `rejection_reason`: VARCHAR(500) NULL
- `review_notes`: VARCHAR(500) NULL
- `reviewed_by_admin_id`: BIGINT NULL (FK -> `users.id`)
- `reviewed_at`: TIMESTAMP NULL
- `version`: INT UNSIGNED NOT NULL DEFAULT 1
- `is_current`: BOOLEAN NOT NULL DEFAULT TRUE

### 3. `driver_verification_history` (Table 26)
- `id`: BIGINT PRIMARY KEY AUTO_INCREMENT
- `driver_id`: BIGINT NOT NULL (FK -> `driver_profiles.id` ON DELETE CASCADE)
- `document_id`: BIGINT NULL (FK -> `driver_documents.id` ON DELETE SET NULL)
- `action_type`: VARCHAR(50) NOT NULL ('DOCUMENT_REVIEW', 'DRIVER_VERIFICATION', 'DRIVER_SUSPENSION', 'PROFILE_SUBMISSION', etc.)
- `previous_status`: VARCHAR(50) NOT NULL
- `new_status`: VARCHAR(50) NOT NULL
- `reviewed_by_admin_id`: BIGINT NOT NULL (FK -> `users.id`)
- `reason`: VARCHAR(500) NULL
- `created_at`: TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

---

## 5. Security & Private File Storage

1. **Authentication**: Reuses the central LEXOR JWT standard issued by `auth-service`.
2. **Role Enforcement**:
   - `/api/v1/drivers/**`: Requires `ROLE_DRIVER`.
   - `/api/v1/admin/**`: Requires `ROLE_ADMIN`.
   - Non-driver users (e.g. `RIDER`) receive `403 Forbidden`.
3. **Resource Ownership**:
   - Drivers cannot access, inspect, or download another driver's documents.
   - Resource access validates that `document.driver.id == authenticatedDriver.id`.
4. **Document Storage Security**:
   - Files are stored in a private directory outside the public web root (`uploads/documents`).
   - Server-side UUID filenames prevent predictable enumeration or file overwrites.
   - Filesystem path traversal attacks (`../`) are strictly blocked.
   - **Magic Byte Inspection**: Content signatures are validated before storage (PDF `%PDF`, JPEG `FF D8 FF`, PNG `89 50 4E 47`, WEBP `RIFF....WEBP`). Fake file extensions and executables are rejected with `400 Bad Request`.
   - No direct URL file serving; files are streamed exclusively via authenticated controller endpoints with `Content-Disposition`.
5. **Driver Availability Gatekeeper**:
   - Unverified drivers (`isVerified == false` or `onboardingStatus != VERIFIED`) or drivers with inactive/suspended accounts are forbidden from setting availability to `ONLINE`.

---

## 6. API Reference

### Driver Endpoints (`/api/v1/drivers/me`)

| Method | Endpoint | Required Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/drivers/me` | `DRIVER` | Retrieve driver profile, license, address, and status |
| `PUT` | `/api/v1/drivers/me` | `DRIVER` | Fully update driver profile fields and emergency contact |
| `PATCH` | `/api/v1/drivers/me` | `DRIVER` | Partially update permitted fields without clearing omitted ones |
| `GET` | `/api/v1/drivers/me/onboarding` | `DRIVER` | Retrieve completion score, required document checklist, and tasks |
| `POST` | `/api/v1/drivers/me/submit-for-review`| `DRIVER` | Submit completed profile and required documents for review |
| `PATCH` | `/api/v1/drivers/me/availability` | `DRIVER` | Toggle driver availability between `ONLINE` and `OFFLINE` |
| `GET` | `/api/v1/drivers/me/documents` | `DRIVER` | List all current documents submitted by authenticated driver |
| `POST` | `/api/v1/drivers/me/documents` | `DRIVER` | Upload document with multipart form data (PDF, PNG, JPEG, WEBP) |
| `GET` | `/api/v1/drivers/me/documents/{id}` | `DRIVER` | Retrieve document metadata by ID (verifies ownership) |
| `GET` | `/api/v1/drivers/me/documents/{id}/download` | `DRIVER` | Securely download document binary with verified ownership |
| `POST` | `/api/v1/drivers/me/documents/{id}/resubmit` | `DRIVER` | Resubmit corrected version of rejected/expired document |
| `DELETE` | `/api/v1/drivers/me/documents/{id}` | `DRIVER` | Logically remove unverified document without breaking audit history |

### Administrative Review Endpoints (`/api/v1/admin`)

| Method | Endpoint | Required Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/drivers` | `ADMIN` | Paginated driver list with filters (status, verification, search) |
| `GET` | `/api/v1/admin/drivers/{uuid}` | `ADMIN` | Administrative driver profile and onboarding details |
| `GET` | `/api/v1/admin/drivers/{uuid}/documents` | `ADMIN` | List all current and historical documents submitted by driver |
| `GET` | `/api/v1/admin/driver-documents` | `ADMIN` | Paginated list of documents awaiting review |
| `GET` | `/api/v1/admin/driver-documents/{id}/download` | `ADMIN` | Securely download document binary for review |
| `PATCH` | `/api/v1/admin/driver-documents/{id}/status` | `ADMIN` | Approve, reject, or request resubmission (mandatory reason) |
| `GET` | `/api/v1/admin/drivers/{uuid}/verification-history`| `ADMIN` | Retrieve chronological audit history for driver decisions |
| `PATCH` | `/api/v1/admin/drivers/{uuid}/verification-status`| `ADMIN` | Driver-level decision (enforces document prerequisites) |
| `PATCH` | `/api/v1/admin/drivers/{uuid}/suspension` | `ADMIN` | Suspend or reinstate driver eligibility |

---

## 7. Running Locally & Configuration

### Prerequisites
- Java 21 JDK
- Maven 3.8+
- MySQL 8.0+ running with `lexor_db` database initialized

### Configuration
Copy `.env.example` or configure environment variables:
```bash
PORT=8085
DB_HOST=localhost
DB_PORT=3306
DB_NAME=lexor_db
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
STORAGE_LOCATION=uploads/documents
```

### Build & Run
```bash
# Build with Maven
mvn clean package -DskipTests

# Run JAR
java -jar target/driver-service-1.0.0-SNAPSHOT.jar
```

---

## 8. Swagger / OpenAPI Testing

Once running:
- **Swagger UI**: [http://localhost:8085/swagger-ui.html](http://localhost:8085/swagger-ui.html)
- **OpenAPI 3.0 JSON**: [http://localhost:8085/v3/api-docs](http://localhost:8085/v3/api-docs)

### Testing via Swagger with Bearer Tokens:
1. Generate or retrieve a JWT token from LEXOR Auth Service (`POST http://localhost:8081/api/v1/auth/login`).
   - Driver Demo: `vikram.singh@example.com`
   - Admin Demo: `admin@lexor.mobility`
2. Click **Authorize** at the top right in Swagger UI.
3. Paste the token into `BearerAuthentication` and click **Authorize**.
4. Test driver self-service endpoints (`/api/v1/drivers/me/**`) or admin review endpoints (`/api/v1/admin/**`).

---

## 9. Automated Test Suite

Run the automated JUnit 5 test suite:
```bash
mvn test
```

### Test Coverage Highlights:
- **`OnboardingPolicyServiceTest`**: Dynamic calculation of onboarding progress, completeness score, prerequisite checks, document verification requirements, and age verification (>= 18 years).
- **`DriverProfileServiceTest`**: Profile creation, full update, partial PATCH preserving omitted values, duplicate license rejection, and availability gatekeeper (forbidding unverified drivers from going online).
- **`DriverDocumentServiceTest`**: Multipart upload, automatic versioning (v1 -> v2) on replacement, resetting verification status, ownership validation, and blocking deletion of verified documents.
- **`DriverVerificationServiceTest`**: Document approval, mandatory rejection reasons, prerequisite enforcement on driver approval, driver suspension/reinstatement, and audit logging.
- **`FileSystemDocumentStorageServiceTest`**: Magic byte detection (PDF, JPEG, PNG, WEBP), signature mismatch rejection, path traversal protection, and safe deletion.
- **`DriverProfileControllerTest`**: MockMvc security tests (401 unauthenticated, 403 rider access, 200 driver self-service, 400 validation failures).
- **`DriverDocumentControllerTest`**: MockMvc multipart upload tests, ownership isolation, and logical document removal.
- **`AdminDriverControllerTest`**: MockMvc tests verifying admin permissions, paginated listing, document review, and driver suspension.
- **`JwtSecurityIntegrationTest`**: JJWT claim generation, signature verification, and expired/tampered token rejection.
