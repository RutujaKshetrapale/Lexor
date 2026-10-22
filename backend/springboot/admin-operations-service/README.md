# LEXOR Enterprise Admin & Operations Service (Spring Boot)

Intelligent Mobility. Elevated.

The **Admin & Operations Service** is the central management, operational intelligence, health diagnostics, and audit coordination engine of the LEXOR AI-Powered Smart Mobility Platform.

---

## 1. Architecture & Responsibilities

```
                                      +-----------------------+
                                      |     React Admin UI    |
                                      |    (Vite / Port 5173) |
                                      +-----------+-----------+
                                                  |
                                                  v
                                      +-----------------------+
                                      |      API Gateway      |
                                      |   (Port 8080/YARP)    |
                                      +-----------+-----------+
                                                  |
                                  +---------------+---------------+
                                  |                               |
                                  v                               v
                     +--------------------------+    +--------------------------+
                     | Entity Domain APIs       |    | Admin Operations Service |
                     | (/api/v1/admin/drivers)  |    | (Port 8103)              |
                     | (/api/v1/admin/bookings) |    | - Multi-Service Overview |
                     | (/api/v1/admin/payments) |    | - Live Operations Stream |
                     | (/api/v1/admin/feedback) |    | - Trend Analytics & CSV  |
                     +--------------------------+    | - System Health Matrix   |
                                                     | - Centralized Audit Log  |
                                                     +-------------+------------+
                                                                   |
                 +-------------------+--------------------+--------+-----------+-------------------+
                 |                   |                    |                    |                   |
                 v                   v                    v                    v                   v
        +-----------------+  +-----------------+  +-----------------+  +-----------------+  +-----------------+
        | Booking Service |  | Payment Service |  | Driver Service  |  | Feedback Service|  | MySQL & Redis   |
        | (Port 8095)     |  | (Port 8097)     |  | (Port 8085)     |  | (Port 8101)     |  | (Port 3306/6379)|
        +-----------------+  +-----------------+  +-----------------+  +-----------------+  +-----------------+
```

### Core Responsibilities:
1. **Executive Multi-Service Overview:** Aggregates 17 platform KPIs spanning 8 microservices (Total Rides, Completed Rides, Cancellations, Active Rides, Pending Requests, Online Drivers, In-Trip Drivers, Pending Verifications, Registered Riders, Registered Drivers, Registered Vehicles, Gross Booking Value, Collections, Refunds, Platform Commissions, Driver Rating, Pending Moderation).
2. **Live Operations Console:** High-frequency polling console providing real-time fleet state meters, active rides tracking, pending requests waiting times, and threshold-based operational alerts.
3. **Analytics & Compliance Export:** Daily/weekly ride volumes, completion vs cancellation ratios, hourly demand curves (0–23h), and authenticated streaming CSV exports.
4. **Platform Health Diagnostics:** Probes process responsiveness, port reachability, database connections, and roundtrip latencies across all 12 platform microservices + API Gateway + MySQL + Redis.
5. **Centralized Operations Audit Trail:** Immutable event log (`admin_audit_logs`) recording admin ID, action, resource, justification reason, IP address, and payload.

---

## 2. API Endpoints

### 2.1 Operations & Live Console

| Method | Path | Roles | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/operations/overview` | `ROLE_ADMIN` | Executive multi-service overview with time-range filtering (`TODAY`, `7D`, `30D`, `90D`, `CUSTOM`). |
| `GET` | `/api/v1/admin/operations/live` | `ROLE_ADMIN` | Real-time operations snapshot: driver meters, active rides, matching queue, and active alerts. |
| `POST` | `/api/v1/admin/operations/actions/cancel-ride` | `ROLE_ADMIN` | Emergency ride cancellation with mandatory audit reason. |

### 2.2 Analytics & Reporting

| Method | Path | Roles | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/analytics/trends` | `ROLE_ADMIN` | Historical trends: daily rides, completion rate, hourly demand curve, financial breakdown. |
| `GET` | `/api/v1/admin/analytics/export/csv` | `ROLE_ADMIN` | Authenticated streaming CSV export (`entityType=RIDES|PAYMENTS|DRIVERS`). |

### 2.3 System Health Monitor

| Method | Path | Roles | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/health-monitor/services` | `ROLE_ADMIN` | Health matrix probing all 13 services, ports, status (`UP`, `DEGRADED`, `DOWN`), and latency in ms. |

### 2.4 Audit Logs & Settings

| Method | Path | Roles | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/audit-logs` | `ROLE_ADMIN` | Paginated search of administrative action history with multi-dimensional filters. |
| `POST` | `/api/v1/admin/audit-logs` | `ROLE_ADMIN` | Record privileged administrative action event. |
| `GET` | `/api/v1/admin/audit-logs/settings` | `ROLE_ADMIN` | Retrieve operational platform settings. |
| `PUT` | `/api/v1/admin/audit-logs/settings` | `ROLE_ADMIN` | Update platform setting with automated audit trail logging. |

---

## 3. Database Schema & Migration

Migration: `database/mysql/migrations/V12__admin_operations_service_additive.sql`

- **Tables:**
  - `admin_audit_logs`: Immutable trail of administrative interventions.
  - `admin_dashboard_settings`: Dynamic operational tuning parameters.

### Manual Migration Application:
```bash
mysql -h localhost -P 3306 -u root -p lexor_db < database/mysql/migrations/V12__admin_operations_service_additive.sql
```

---

## 4. Configuration & Environment Variables

| Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8103` | HTTP listening port |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/lexor_db?...` | MySQL connection string |
| `SPRING_DATASOURCE_USERNAME` | `root` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `root` | Database password |
| `JWT_SECRET` | `lexor-default-jwt-secret-key-32-chars-long!` | JWT signing secret |
| `LEXOR_INTERNAL_SERVICE_KEY` | `lexor-internal-service-secret-key-2026` | Inter-service auth key |
| `BOOKING_SERVICE_URL` | `http://localhost:8095` | Booking Service base URL |
| `PAYMENT_SERVICE_URL` | `http://localhost:8097` | Payment Service base URL |
| `DRIVER_SERVICE_URL` | `http://localhost:8085` | Driver Service base URL |
| `FEEDBACK_SERVICE_URL` | `http://localhost:8101` | Feedback Service base URL |

---

## 5. Build & Test Commands

```bash
# Navigate to service directory
cd backend/springboot/admin-operations-service

# Run automated tests
mvn test

# Package release artifact
mvn clean package -DskipTests

# Run service locally
mvn spring-boot:run
```
