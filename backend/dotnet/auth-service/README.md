# LEXOR ASP.NET Core Auth Service

Production-ready ASP.NET Core Authentication Microservice for the LEXOR Smart Mobility Platform.

## 🚀 Architectural Parity

This microservice provides 1:1 business capability and API contract parity with the Spring Boot authentication service (`backend/springboot/auth-service`). It integrates directly with the existing `lexor_db` MySQL database without introducing schema modifications or EF Core migrations.

- **Framework:** .NET 9 LTS / ASP.NET Core Web API
- **Port:** `8082` (`http://localhost:8082`)
- **Database:** MySQL 8 (`lexor_db.users` table)
- **ORM:** Entity Framework Core (Pomelo MySQL Provider)
- **Security:** BCrypt Password Hashing (`BCrypt.Net-Next`), HMAC-SHA256 JWT Bearer Authentication
- **Documentation:** OpenAPI / Swagger UI (`/swagger`)

---

## 🛠 Project Structure

```
backend/dotnet/auth-service/
├── Configuration/
│   └── JwtSettings.cs              # JWT Configuration binding model
├── Controllers/
│   └── AuthController.cs           # HTTP endpoints (/register, /login, /me)
├── Data/
│   └── LexorDbContext.cs           # EF Core DbContext mapped to existing 'users' table
├── DTOs/
│   ├── AuthResponse.cs             # Token & user payload response
│   ├── ErrorResponse.cs            # RFC 7807 compliant error structure
│   ├── LoginRequest.cs             # Credentials DTO
│   ├── RegisterRequest.cs          # Registration request payload
│   └── UserResponse.cs             # Sanitized user profile DTO
├── Entities/
│   ├── User.cs                     # User entity mapped to database columns
│   ├── UserRole.cs                 # Enums: RIDER, DRIVER, ADMIN
│   └── UserStatus.cs               # Enums: PENDING, ACTIVE, SUSPENDED, DEACTIVATED
├── Exceptions/
│   ├── DuplicateResourceException.cs (409 Conflict)
│   ├── ForbiddenException.cs         (403 Forbidden)
│   ├── InvalidCredentialsException.cs(401 Unauthorized)
│   └── ResourceNotFoundException.cs  (404 Not Found)
├── Middleware/
│   └── GlobalExceptionMiddleware.cs # Centralized error handler
├── Security/
│   ├── IPasswordHasher.cs / PasswordHasher.cs (BCrypt)
│   └── IJwtTokenService.cs / JwtTokenService.cs (JWT Token generation & claims)
├── Services/
│   └── IAuthService.cs / AuthService.cs       # Core authentication business logic
├── Tests/
│   ├── AuthControllerTests.cs      # Controller unit tests (Moq)
│   └── AuthServiceTests.cs         # Service unit tests (EF Core InMemory)
├── appsettings.json               # Main application config
├── appsettings.Development.json    # Development overrides
├── AuthService.csproj              # Project & dependency manifest
└── README.md                       # Service documentation
```

---

## 🔑 Key Features & Security Design

1. **User Registration Rules:**
   - Registration restricted to `RIDER` and `DRIVER` roles (`ADMIN` registration via endpoint rejected with `403 Forbidden`).
   - Phone verification status defaults to `true` for `RIDER`s and `false` for `DRIVER`s.
   - Status initialized to `ACTIVE` upon registration.
   - Email uniqueness checked (raises `409 Conflict` if registered).

2. **Password Security:**
   - Hashed using BCrypt with work factor 12.
   - Passwords and `password_hash` are **never** returned in API responses or logs.

3. **JWT Claims:**
   - Issued with `sub` (User UUID), `userId` (Database ID), `email`, and `role`.
   - Signed with HMAC-SHA256 using configurable Secret & Expiration.

4. **Global Exception Handling:**
   - Converts standard C# domain exceptions into RFC 7807 JSON error payloads (`Status`, `Error`, `Message`, `Timestamp`, `Path`).

---

## 🔌 API Endpoints

### 1. Register User
- **HTTP Method:** `POST /api/v1/auth/register`
- **Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "Password123!",
  "phoneNumber": "+1234567890",
  "role": "RIDER"
}
```
- **Response (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1234567890",
    "role": "RIDER",
    "status": "ACTIVE",
    "profilePictureUrl": null,
    "emailVerified": true,
    "phoneVerified": true,
    "createdAt": "2026-10-08T14:00:00Z",
    "updatedAt": "2026-10-08T14:00:00Z"
  }
}
```

### 2. Login User
- **HTTP Method:** `POST /api/v1/auth/login`
- **Request Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "Password123!"
}
```
- **Response (200 OK):** `AuthResponse` object.

### 3. Get Current User Profile
- **HTTP Method:** `GET /api/v1/auth/me`
- **Headers:** `Authorization: Bearer <token>`
- **Response (200 OK):** `UserResponse` object.

---

## 🧪 Running Tests

Execute the unit test suite targeting EF Core InMemory database:

```bash
dotnet test
```

All 10 unit tests in `AuthServiceTests` and `AuthControllerTests` run isolated from MySQL.

---

## 🟢 Running the Service Locally

1. Ensure MySQL is running with `lexor_db` created (or verify connection string in `appsettings.json`).
2. Run the application:

```bash
dotnet run
```

3. Access Swagger API Documentation:
   - `http://localhost:8082/swagger`
