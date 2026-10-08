# LEXOR — Spring Boot Authentication Service (`auth-service`)

The **Auth Service** is the central identity, authentication, and JWT token management microservice for the **LEXOR AI-Powered Smart Mobility Platform**.

---

## 🚀 Purpose

- Handles user registration (`RIDER` and `DRIVER` roles).
- Authenticates credentials against the existing `users` table in `lexor_db`.
- Issues signed HMAC-SHA256 JWT access tokens.
- Validates JWT tokens and injects security principals for protected routes (`GET /api/v1/auth/me`).
- Enforces BCrypt password hashing and safe role registration rules.

---

## 🛠️ Technology Stack

- **Java Version:** Java 21 LTS
- **Framework:** Spring Boot 3.2.5
- **Build Tool:** Maven 3.9+
- **Security:** Spring Security & JJWT (`0.12.5`)
- **Database:** MySQL 8.0+ (Spring Data JPA / Hibernate)
- **Validation:** Jakarta Bean Validation (`spring-boot-starter-validation`)
- **Utility:** Lombok
- **Testing:** JUnit 5, Mockito, Spring Security Test, H2 In-Memory DB

---

## 📁 Package Structure

```text
backend/springboot/auth-service/
├── pom.xml
├── .env.example
├── README.md
└── src/
    ├── main/
    │   ├── java/com/lexor/auth/
    │   │   ├── AuthServiceApplication.java
    │   │   ├── config/
    │   │   │   └── SecurityConfig.java
    │   │   ├── controller/
    │   │   │   └── AuthController.java
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   │   ├── RegisterRequest.java
    │   │   │   │   └── LoginRequest.java
    │   │   │   └── response/
    │   │   │       ├── AuthResponse.java
    │   │   │       ├── UserResponse.java
    │   │   │       └── ErrorResponse.java
    │   │   ├── entity/
    │   │   │   ├── User.java
    │   │   │   ├── Role.java
    │   │   │   └── UserStatus.java
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── DuplicateResourceException.java
    │   │   │   ├── InvalidCredentialsException.java
    │   │   │   ├── ResourceNotFoundException.java
    │   │   │   └── ForbiddenException.java
    │   │   ├── repository/
    │   │   │   └── UserRepository.java
    │   │   ├── security/
    │   │   │   ├── JwtTokenProvider.java
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── JwtAuthenticationEntryPoint.java
    │   │   │   ├── CustomUserDetailsService.java
    │   │   │   └── UserPrincipal.java
    │   │   └── service/
    │   │       ├── AuthService.java
    │   │       └── impl/
    │   │           └── AuthServiceImpl.java
    │   └── resources/
    │       └── application.yml
    └── test/
        ├── java/com/lexor/auth/
        │   ├── controller/
        │   │   └── AuthControllerTest.java
        │   ├── security/
        │   │   └── JwtTokenProviderTest.java
        │   └── service/
        │       └── AuthServiceTest.java
        └── resources/
            └── application-test.yml
```

---

## 🗄️ Database Integration

- **Table Mapped:** `users` (Existing `lexor_db` database).
- **Schema Control:** `spring.jpa.hibernate.ddl-auto=validate` (Ensures the application strictly validates the existing 24-table database schema without altering tables).

---

## ⚙️ Environment Variables

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8081` | Service HTTP Listen Port |
| `DB_HOST` | `localhost` | MySQL Server Host |
| `DB_PORT` | `3306` | MySQL Server Port |
| `DB_NAME` | `lexor_db` | Target Database Name |
| `DB_USERNAME` | `root` | Database User |
| `DB_PASSWORD` | `root` | Database Password |
| `JWT_SECRET` | `404E6352...` | HMAC-SHA256 Secret Key (Min 256 bits) |
| `JWT_EXPIRATION_MS` | `86400000` | JWT Expiry Duration (24 Hours in ms) |

---

## 🔑 API Endpoints

### 1. Register User
- **Endpoint:** `POST /api/v1/auth/register`
- **Access:** Public
- **Request Body:**
```json
{
  "firstName": "Rajesh",
  "lastName": "Kumar",
  "email": "rajesh.kumar@example.com",
  "phoneNumber": "+919876543211",
  "password": "Password123",
  "role": "RIDER"
}
```
- **Response (210 CREATED):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 2,
    "uuid": "22222222-2222-4222-8222-222222222222",
    "firstName": "Rajesh",
    "lastName": "Kumar",
    "email": "rajesh.kumar@example.com",
    "phoneNumber": "+919876543211",
    "role": "RIDER",
    "status": "ACTIVE",
    "emailVerified": false,
    "phoneVerified": false,
    "createdAt": "2026-10-08T12:00:00"
  }
}
```

---

### 2. User Login
- **Endpoint:** `POST /api/v1/auth/login`
- **Access:** Public
- **Request Body:**
```json
{
  "email": "rajesh.kumar@example.com",
  "password": "Password123"
}
```
- **Response (200 OK):** Returns `AuthResponse` with JWT token and user profile.

---

### 3. Get Current User Profile
- **Endpoint:** `GET /api/v1/auth/me`
- **Access:** Protected (Requires `Authorization: Bearer <accessToken>`)
- **Response (200 OK):** Returns `UserResponse` object.

---

## 🧪 Running Tests

To run the automated JUnit 5 and Mockito test suite:

```bash
mvn test
```

To start the service locally:

```bash
mvn spring-boot:run
```
