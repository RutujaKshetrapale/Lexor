# LEXOR Platform — Environment Configuration Reference

This document catalogs every environment variable utilized by LEXOR backend microservices across both Spring Boot (Java 21) and ASP.NET Core (.NET 9) stacks.

---

## 1. Core Platform Security Variables

| Variable Name | Description | Consuming Services | Mandatory? | Default / Fallback |
| :--- | :--- | :--- | :--- | :--- |
| `JWT_SECRET` | HMAC-SHA256 symmetric signing secret (minimum 32 characters / 256 bits). Used to sign and verify user authentication tokens. | All Services, API Gateways, Auth Service | **YES** | None in production. Fails startup if `< 32` characters. |
| `JWT_ISSUER` | Authority issuing authentication tokens. | All Services, Gateways | No | `LEXOR` |
| `JWT_AUDIENCE` | Target audience for tokens. | All Services, Gateways | No | `LEXOR` |
| `INTERNAL_SERVICE_API_KEY` | Shared high-entropy secret key for inter-service HTTP communication (`X-Internal-Service-Key`). | All microservices | **YES** | None in production. Injected via secure secrets manager. |

---

## 2. Database & Persistence Variables (MySQL)

| Variable Name | Description | Consuming Services | Mandatory? | Default / Fallback |
| :--- | :--- | :--- | :--- | :--- |
| `DB_HOST` | MySQL hostname or cluster endpoint. | All persistent microservices | No | `localhost` |
| `DB_PORT` | MySQL connection port. | All persistent microservices | No | `3306` |
| `DB_NAME` | Database schema name. | All persistent microservices | No | `lexor_db` |
| `DB_USERNAME` | MySQL user account for microservices. | All persistent microservices | **YES** | `lexor_user` |
| `DB_PASSWORD` | MySQL account password. | All persistent microservices | **YES** | None in production. Required for startup. |
| `SPRING_DATASOURCE_URL` | Explicit Spring JDBC datasource URL. | Spring Boot microservices | No | Computed from `DB_HOST`/`DB_NAME` |
| `ConnectionStrings__DefaultConnection` | Explicit ADO.NET / EF Core connection string. | .NET microservices | No | Computed from `DB_HOST`/`DB_NAME` |

---

## 3. In-Memory Store & Cache (Redis)

| Variable Name | Description | Consuming Services | Mandatory? | Default / Fallback |
| :--- | :--- | :--- | :--- | :--- |
| `REDIS_HOST` | Redis cache hostname. | Driver Matching, Booking, Gateways | No | `localhost` |
| `REDIS_PORT` | Redis server port. | Driver Matching, Booking, Gateways | No | `6379` |
| `REDIS_PASSWORD` | Redis authentication password. | Driver Matching, Booking, Gateways | Conditional | None (blank for unsecured local dev) |

---

## 4. API Gateway & Edge Ingress Variables

| Variable Name | Description | Consuming Services | Mandatory? | Default / Fallback |
| :--- | :--- | :--- | :--- | :--- |
| `PORT` | Edge ingress listening port. | API Gateway | No | `8080` (Spring) / `8081` (.NET) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of permitted web origins. | API Gateway | No | `http://localhost:3000,http://localhost:5173` |
| `RATE_LIMIT_ENABLED` | Toggle for fixed-window rate limiting. | API Gateway | No | `true` |
| `RATE_LIMIT_REPLENISH_RATE` | Tokens replenished per window. | API Gateway | No | `50` |
| `RATE_LIMIT_BURST_CAPACITY` | Maximum burst requests permitted. | API Gateway | No | `100` |

---

## 5. Microservice Discovery & Upstream URLs

| Variable Name | Target Microservice | Spring Boot Port | .NET Port |
| :--- | :--- | :--- | :--- |
| `AUTH_SERVICE_URL` | Auth Service | `http://localhost:8081` | `http://localhost:8082` |
| `USER_SERVICE_URL` | User & Rider Service | `http://localhost:8083` | `http://localhost:8084` |
| `DRIVER_SERVICE_URL` | Driver Management Service | `http://localhost:8085` | `http://localhost:8086` |
| `VEHICLE_SERVICE_URL` | Vehicle Fleet Service | `http://localhost:8087` | `http://localhost:8088` |
| `RIDE_TYPE_SERVICE_URL` | Ride Types & Fare Rules | `http://localhost:8089` | `http://localhost:8090` |
| `RIDE_REQUEST_SERVICE_URL` | Ride Request Service | `http://localhost:8091` | `http://localhost:8092` |
| `DRIVER_MATCHING_SERVICE_URL` | Matching & Geo-Spatial Service | `http://localhost:8093` | `http://localhost:8094` |
| `BOOKING_SERVICE_URL` | Booking & Trip Lifecycle Service | `http://localhost:8095` | `http://localhost:8096` |
| `PAYMENT_SERVICE_URL` | Payment & Reconciliation Service | `http://localhost:8097` | `http://localhost:8098` |
| `NOTIFICATION_SERVICE_URL` | Multi-Channel Notification Service | `http://localhost:8099` | `http://localhost:8100` |
| `FEEDBACK_SERVICE_URL` | Ratings, Reviews & Moderation | `http://localhost:8101` | `http://localhost:8102` |
| `ADMIN_OPERATIONS_SERVICE_URL` | Operations & Admin Dashboard | `http://localhost:8103` | `http://localhost:8104` |

---

## 6. External Third-Party Integration Credentials

| Variable Name | Description | Consuming Service | Environment |
| :--- | :--- | :--- | :--- |
| `FCM_SERVER_KEY` | Firebase Cloud Messaging server key. | Notification Service | Staging / Prod |
| `FCM_PROJECT_ID` | Firebase Project ID. | Notification Service | Staging / Prod |
| `TWILIO_ACCOUNT_SID` | Twilio SMS API Account SID. | Notification Service | Staging / Prod |
| `TWILIO_AUTH_TOKEN` | Twilio SMS API Auth Token. | Notification Service | Staging / Prod |
| `SMTP_USERNAME` | SMTP Relay user for notification emails. | Notification Service | Staging / Prod |
| `SMTP_PASSWORD` | SMTP Relay password. | Notification Service | Staging / Prod |
