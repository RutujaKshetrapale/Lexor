# LEXOR — ASP.NET Core User Service

Production-ready User Profile and Administrative Management Microservice (.NET 9 Web API) for **LEXOR — AI-Powered Smart Mobility Platform**.

---

## 1. Responsibilities & Service Boundaries

The .NET User Service is the ASP.NET Core equivalent of the Spring Boot User Service, sharing the exact same database schema (`lexor_db.users`), REST API contracts, security rules, and business logic.

### Boundaries:
- **Auth Service (`port: 8082`)**: Registration, authentication, password hashing, JWT token issuance.
- **User Service (`port: 8084`)**: Current user profile retrieval (`/me`), profile updates (`PUT`/`PATCH`), administrative provisioning (`POST /users`), user directory listing and filtering (`GET /users`), status transitions, and soft-deactivation.

---

## 2. Architecture & Project Structure

Follows clean layered architecture consistent with the LEXOR .NET Auth Service:

```
backend/dotnet/user-service/
├── .env.example
├── appsettings.json
├── appsettings.Development.json
├── Program.cs                         # Application entrypoint & middleware pipeline
├── UserService.csproj                 # Project dependencies (.NET 9.0)
├── Configuration/
│   └── JwtSettings.cs                 # JWT configuration binding
├── Controllers/
│   ├── UserProfileController.cs       # /api/v1/users/me endpoints
│   └── AdminUserController.cs         # /api/v1/users administrative endpoints
├── Data/
│   └── LexorDbContext.cs              # EF Core context mapped to lexor_db.users
├── DTOs/
│   ├── Request/                       # Validated input DTOs
│   │   ├── AdminCreateUserRequest.cs
│   │   ├── AdminPatchUserRequest.cs
│   │   ├── AdminUpdateUserRequest.cs
│   │   ├── PatchProfileRequest.cs
│   │   ├── UpdateProfileRequest.cs
│   │   └── UpdateUserStatusRequest.cs
│   └── Response/                      # Safe output DTOs (credentials omitted)
│       ├── ErrorResponse.cs
│       ├── PagedResponse.cs
│       └── UserResponse.cs
├── Entities/                          # EF Core entities & enums
│   ├── User.cs
│   ├── UserRole.cs
│   └── UserStatus.cs
├── Exceptions/                        # Custom domain exceptions
│   ├── BadRequestException.cs
│   ├── DuplicateResourceException.cs
│   ├── ForbiddenException.cs
│   ├── InvalidStatusTransitionException.cs
│   └── ResourceNotFoundException.cs
├── Middleware/                        # Custom middlewares
│   ├── GlobalExceptionMiddleware.cs   # Exception handler & error formatting
│   └── UserStatusMiddleware.cs        # Instant revocation for deactivated users
├── Security/                          # Cryptographic & token services
│   ├── IPasswordHasher.cs
│   ├── PasswordHasher.cs              # BCrypt hasher
│   ├── IJwtTokenService.cs
│   └── JwtTokenService.cs             # JWT issuer & claim validator
├── Services/                          # Business logic service
│   ├── IUserService.cs
│   └── UserService.cs
└── Tests/                             # Automated xUnit unit & controller tests
    ├── AdminUserControllerTests.cs
    ├── JwtTokenServiceTests.cs
    ├── UserProfileControllerTests.cs
    └── UserServiceTests.cs
```

---

## 3. Technology Stack & Prerequisites

- **Target Framework**: .NET 9.0 (`net9.0`)
- **Database Provider**: `Pomelo.EntityFrameworkCore.MySql` (MySQL 8.0 `lexor_db`)
- **Authentication**: JWT Bearer (`Microsoft.AspNetCore.Authentication.JwtBearer`)
- **Password Hashing**: BCrypt (`BCrypt.Net-Next`)
- **API Documentation**: OpenAPI / Swagger UI (`Swashbuckle.AspNetCore`)
- **Testing**: xUnit, Moq, EF Core In-Memory Database

---

## 4. Database Mapping (`lexor_db.users`)

Maps the central identity table (`users`) using Entity Framework Core data annotations and fluent API configurations:

| Property | Column | Mapping / Constraint |
|---|---|---|
| `Id` | `id` | `BIGINT` PK Auto-Increment |
| `Uuid` | `uuid` | `VARCHAR(36)` Unique |
| `FirstName` | `first_name` | `VARCHAR(50)` Required |
| `LastName` | `last_name` | `VARCHAR(50)` Required |
| `Email` | `email` | `VARCHAR(100)` Unique Required |
| `PhoneNumber` | `phone_number` | `VARCHAR(20)` Unique Required |
| `PasswordHash` | `password_hash` | `VARCHAR(255)` BCrypt Hashed |
| `Role` | `role` | `enum('RIDER','DRIVER','ADMIN')` |
| `Status` | `status` | `enum('PENDING','ACTIVE','SUSPENDED','DEACTIVATED')` |
| `ProfilePictureUrl` | `profile_picture_url` | `VARCHAR(500)` Nullable |
| `EmailVerified` | `email_verified` | `BOOLEAN` |
| `PhoneVerified` | `phone_verified` | `BOOLEAN` |
| `CreatedAt` | `created_at` | `TIMESTAMP` |
| `UpdatedAt` | `updated_at` | `TIMESTAMP` |

*Enum JSON Serialization*: Configured with `JsonStringEnumConverter` so API clients receive and submit string enum values (`"RIDER"`, `"ACTIVE"`, etc.) identical to Spring Boot.

---

## 5. Security & Token Revocation

1. **JWT Verification**: Validates tokens issued by LEXOR Auth Services (Spring Boot / .NET). Key claims:
   - `sub` / `NameIdentifier`: User UUID
   - `role` / `ClaimTypes.Role`: `"ROLE_ADMIN"`, `"ROLE_RIDER"`, `"ROLE_DRIVER"`
2. **Instant Status Check (`UserStatusMiddleware`)**: Validates that the user's status in `lexor_db` is `ACTIVE`. If an account is soft-deactivated or suspended, requests are immediately rejected with `401 Unauthorized` regardless of JWT token expiration.
3. **Admin Protection Rules**:
   - Self-deactivation of ADMIN accounts via `/me` is forbidden (`403 Forbidden`).
   - Administrative deletion or status deactivation of the last active administrator is forbidden (`403 Forbidden`).
   - `PasswordHash` is never exposed in response objects.

---

## 6. HTTP API Endpoints

### Current User Profile (`/api/v1/users/me`)

| Method | Route | Auth | Description | Status Codes |
|---|---|---|---|---|
| `GET` | `/api/v1/users/me` | Authenticated | Get current authenticated user profile | `200`, `401`, `404` |
| `PUT` | `/api/v1/users/me` | Authenticated | Full update of editable fields (`FirstName`, `LastName`, `PhoneNumber`, `ProfilePictureUrl`) | `200`, `400`, `401`, `409` |
| `PATCH` | `/api/v1/users/me` | Authenticated | Partial update of permitted profile fields | `200`, `400`, `401`, `409` |
| `DELETE` | `/api/v1/users/me` | Authenticated | Soft-deactivate user account (`status = DEACTIVATED`). Admin self-deactivation forbidden. | `204`, `401`, `403` |

### Administrative User Management (`/api/v1/users`)

| Method | Route | Auth | Description | Status Codes |
|---|---|---|---|---|
| `POST` | `/api/v1/users` | ADMIN Only | Provision a user with BCrypt password hashing. No token issued. | `201`, `400`, `403`, `409` |
| `GET` | `/api/v1/users` | ADMIN Only | List and filter users with pagination & search | `200`, `403` |
| `GET` | `/api/v1/users/{uuid}` | ADMIN Only | Get user profile by UUID | `200`, `403`, `404` |
| `PUT` | `/api/v1/users/{uuid}` | ADMIN Only | Full update of administrative profile fields | `200`, `400`, `403`, `404`, `409` |
| `PATCH` | `/api/v1/users/{uuid}` | ADMIN Only | Partial update of administrative profile fields | `200`, `400`, `403`, `404`, `409` |
| `PATCH` | `/api/v1/users/{uuid}/status` | ADMIN Only | Update account status (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`, `PENDING`). Protects last active admin. | `200`, `400`, `403`, `404` |
| `DELETE` | `/api/v1/users/{uuid}` | ADMIN Only | Soft-deactivate user account. Never deletes database row. | `204`, `400`, `403`, `404` |

---

## 7. How to Run & Test

### Build Project
```bash
dotnet restore
dotnet build
```

### Run Automated Tests
```bash
dotnet test
```

### Run Service Locally (Port 8084)
```bash
dotnet run
```

### Swagger Documentation
Access Swagger UI at:
`http://localhost:8084/swagger`
