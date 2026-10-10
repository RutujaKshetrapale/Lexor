# LEXOR — User Microservice (Spring Boot)

Production-ready User Profile and Administrative Management Microservice for **LEXOR — AI-Powered Smart Mobility Platform**.

---

## 1. Overview and Boundaries

The User Service handles user profile lifecycle management, self-profile updates, administrative user provisioning, role-based user directory searches, and soft account deactivation.

### Boundaries:
- **Auth Service (`port: 8081`)**: Registration, authentication, password hashing, JWT token issuance.
- **User Service (`port: 8083`)**: User profile retrieval (`/me`), profile updates (`PUT`/`PATCH`), administrative provisioning (`POST /users`), user directory listing and filtering (`GET /users`), status transitions, and soft-deactivation.

---

## 2. Architecture & Package Structure

Built with a clean layered architecture consistent with LEXOR enterprise backend standard:

```
backend/springboot/user-service/
├── src/main/java/com/lexor/user/
│   ├── UserServiceApplication.java      # Application entry point
│   ├── config/
│   │   ├── SecurityConfig.java          # Spring Security & JWT Filter Chain
│   │   └── OpenApiConfig.java           # OpenAPI/Swagger configuration
│   ├── controller/
│   │   ├── UserProfileController.java   # /api/v1/users/me endpoints
│   │   └── AdminUserController.java     # /api/v1/users administrative endpoints
│   ├── dto/
│   │   ├── request/                     # Validated request DTOs
│   │   └── response/                    # Safe response DTOs (no credentials)
│   ├── entity/                          # JPA Entities (mapped to lexor_db.users)
│   │   ├── User.java
│   │   ├── Role.java
│   │   └── UserStatus.java
│   ├── exception/                       # Custom exceptions & GlobalExceptionHandler
│   ├── repository/                      # Spring Data JPA Repository with Specification support
│   ├── security/                        # JwtTokenProvider, JwtAuthenticationFilter, UserPrincipal
│   └── service/                         # Business logic interfaces & implementation
│       ├── UserService.java
│       └── impl/UserServiceImpl.java
└── src/test/java/com/lexor/user/        # Automated JUnit 5 & MockMvc tests
```

---

## 3. Tech Stack & Compatibility

- **Java Version**: Java 21
- **Spring Boot**: 3.2.5
- **Build Tool**: Apache Maven
- **Database**: MySQL 8.0 (`lexor_db`)
- **Security**: Spring Security 6.x + JJWT (`0.12.5`)
- **Documentation**: Springdoc OpenAPI / Swagger (`2.5.0`)
- **Testing**: JUnit 5, Mockito, Spring Security Test, H2 In-Memory DB

---

## 4. Database Schema Mapping

Uses the existing `lexor_db.users` MySQL central identity table as source of truth (`ddl-auto=validate`):

| Column | Type | Constraints / Details |
|---|---|---|
| `id` | BIGINT | Auto Increment Primary Key |
| `uuid` | VARCHAR(36) | Unique UUID string |
| `first_name` | VARCHAR(50) | Not Null |
| `last_name` | VARCHAR(50) | Not Null |
| `email` | VARCHAR(100) | Not Null, Unique |
| `phone_number` | VARCHAR(20) | Not Null, Unique |
| `password_hash` | VARCHAR(255) | BCrypt Hashed Password |
| `role` | ENUM | `'RIDER'`, `'DRIVER'`, `'ADMIN'` |
| `status` | ENUM | `'PENDING'`, `'ACTIVE'`, `'SUSPENDED'`, `'DEACTIVATED'` |
| `profile_picture_url` | VARCHAR(500) | Nullable |
| `email_verified` | BOOLEAN | Default FALSE |
| `phone_verified` | BOOLEAN | Default FALSE |
| `created_at` | TIMESTAMP | Creation timestamp |
| `updated_at` | TIMESTAMP | Last update timestamp |

---

## 5. Security & JWT Integration

1. **Shared Token Model**: Uses HMAC-SHA256 signing with `JWT_SECRET`. Claims expected and produced:
   - `sub`: User UUID string
   - `userId`: Internal database ID
   - `email`: User email
   - `role`: Spring Security authority format (e.g. `ROLE_ADMIN`, `ROLE_RIDER`, `ROLE_DRIVER`)
2. **Access Control**:
   - `/api/v1/users/me/**`: Accessible by any authenticated user. Identity is derived directly from JWT claims.
   - `/api/v1/users/**`: Strictly ADMIN-only (`hasRole('ADMIN')`).
3. **Data Protection**:
   - `password_hash` is never exposed in any DTO response.
   - Role escalation by non-admins is prevented.
   - Soft deactivation prevents future authentication.

---

## 6. API Endpoint Summary

### Current User Profile (`/api/v1/users/me`)

| Method | Endpoint | Authorization | Description |
|---|---|---|---|
| `GET` | `/api/v1/users/me` | Authenticated | Retrieve profile for authenticated user |
| `PUT` | `/api/v1/users/me` | Authenticated | Fully update editable profile fields (`firstName`, `lastName`, `phoneNumber`, `profilePictureUrl`) |
| `PATCH` | `/api/v1/users/me` | Authenticated | Partially update editable profile fields |
| `DELETE` | `/api/v1/users/me` | Authenticated | Soft-deactivate user account (`status = DEACTIVATED`). Admin accounts forbidden. |

### User Administration (`/api/v1/users`)

| Method | Endpoint | Authorization | Description |
|---|---|---|---|
| `POST` | `/api/v1/users` | ADMIN Only | Provision a new user account |
| `GET` | `/api/v1/users` | ADMIN Only | List and filter users with pagination & search |
| `GET` | `/api/v1/users/{uuid}` | ADMIN Only | Get detailed profile of user by UUID |
| `PUT` | `/api/v1/users/{uuid}` | ADMIN Only | Fully update user administrative profile |
| `PATCH` | `/api/v1/users/{uuid}` | ADMIN Only | Partially update user administrative profile |
| `PATCH` | `/api/v1/users/{uuid}/status` | ADMIN Only | Update account status (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`, `PENDING`) |
| `DELETE` | `/api/v1/users/{uuid}` | ADMIN Only | Soft-deactivate target user. Protects last active admin. |

---

## 7. How to Run & Test

### Prerequisites
- Java 21 JDK
- Maven 3.8+
- MySQL 8.0 running with `lexor_db` database initialized (`database/mysql/schema.sql`)

### Environment Setup
Copy `.env.example` to `.env` or set environment variables:
```bash
PORT=8083
DB_HOST=localhost
DB_PORT=3306
DB_NAME=lexor_db
DB_USERNAME=root
DB_PASSWORD=root
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_EXPIRATION_MS=86400000
```

### Running Automated Tests
```bash
mvn clean test
```

### Running Service Locally
```bash
mvn spring-boot:run
```

### Swagger API Documentation
Once running, open:
`http://localhost:8083/swagger-ui.html`
