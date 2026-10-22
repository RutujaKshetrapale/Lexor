# LEXOR Platform — Security Architecture & Guidelines

## 1. Overview & Threat Model

LEXOR is an enterprise-grade smart mobility platform operating multi-tenant ride-hailing services. The platform runs microservices in two backend stacks:
- **Spring Boot 3.x / Java 21**
- **ASP.NET Core 9.x / C# 13**

All services communicate through API Gateways (Spring Cloud Gateway and YARP) and enforce role-based access control (RBAC), token validation, internal service authentication, and defense-in-depth principles.

---

## 2. Secrets Management Principles

### A. Zero Hardcoded Secrets Policy
- **No production secrets, API keys, or passwords may be committed to version control.**
- Hardcoded defaults in code (e.g., `lexor-default-jwt-secret-key-32-chars-long!`, `lexor-internal-service-secret-key-2026`) have been permanently excised in Phase 14.1 remediation.
- Application configuration files (`application.yml`, `appsettings.json`) only reference environment variables with blank fallbacks.

### B. Fail-Secure Startup Enforcement
- If a required cryptographic secret (such as `JWT_SECRET`) is missing or has length `< 32` characters (256 bits), microservices throw an immediate fatal exception during startup outside isolated unit test environments:
  - Spring Boot: `IllegalStateException("Missing or insufficiently secure JWT secret. 'lexor.security.jwt.secret' must be at least 32 characters.")`
  - .NET: `InvalidOperationException("Missing or insufficiently secure JWT secret. 'Jwt:Secret' or 'JWT_SECRET' must be at least 32 characters.")`
- Services never fall back to an empty string, predictable key, or hardcoded default in staging or production.

### C. Local Development Configuration
- Local developers configure credentials using `.env` or local profile files ignored by Git:
  - Spring Boot: `application-local.yml`
  - .NET: `appsettings.Local.json`
- Both file patterns are explicitly ignored in `.gitignore`.

---

## 3. Cryptographic Standards

### A. JWT Signing Algorithm (HS256)
- **Algorithm:** HMAC using SHA-256 (`HS256`).
- **Secret Entropy Requirement:** Minimum 256 bits (32 bytes). Secrets under 32 characters fail cryptographic validation on startup.
- **Token Claims Preservation:**
  - `sub`: Authoritative user UUID.
  - `userId`: Numeric identifier where applicable.
  - `email`: Authenticated user email.
  - `role`: Role claim (`ROLE_ADMIN`, `ROLE_DRIVER`, `ROLE_RIDER`).
- **Clock Skew:** Configured to `TimeSpan.Zero` / strict expiration validation to prevent replay windows.

### B. Internal Service-to-Service Authentication
- Microservices communicating directly behind the gateway utilize a cryptographically random internal service key via header:
  ```http
  X-Internal-Service-Key: <INTERNAL_SERVICE_API_KEY>
  ```
- The API Gateway actively strips any incoming client-supplied `X-Internal-Service-Key` header at the edge before proxying requests to upstream microservices.
- Edge gateway routes reject any external calls targeting `/api/v1/internal/**` with HTTP 403 Forbidden.

---

## 4. Role-Based Access Control (RBAC) Matrix

| Endpoint Route Pattern | Required Authority / Role | Permitted Actors | Rejection Code |
| :--- | :--- | :--- | :--- |
| `/api/v1/admin/**` | `ROLE_ADMIN` | Platform Administrators | `403 Forbidden` |
| `/api/v1/drivers/me/**` | `ROLE_DRIVER`, `ROLE_ADMIN` | Authenticated Driver | `403 Forbidden` |
| `/api/v1/riders/**` | `ROLE_RIDER`, `ROLE_ADMIN` | Authenticated Rider | `403 Forbidden` |
| `/api/v1/internal/**` | Internal Key / Protected | Gateway / Microservices | `403 Forbidden` |
| `/api/v1/auth/**` | Public / Anonymous | All Clients | N/A |
| `/health` | Public / Anonymous | Monitoring Agents | N/A |

### Defense-in-Depth Authorization
Even if a token bypasses the gateway, every administrative controller in both Spring Boot and .NET enforces server-side role validation:
- **Spring Boot:** `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`
- **.NET:** `[Authorize(Roles = "ROLE_ADMIN,ADMIN")]`

---

## 5. Safe Logging & Data Redaction

1. **Credentials Redaction:** Passwords, JWT tokens, Bearer authorization headers, and internal API keys are strictly excluded from logs and exception messages.
2. **PII Protection:** National IDs, driver license numbers, bank account numbers, and passenger addresses are masked or excluded from general application traces.
3. **Health Endpoints:**
   - `/health` endpoints return purely status indicators (`UP`, `DOWN`) and do not disclose connection strings, database usernames, or secret fingerprints.
