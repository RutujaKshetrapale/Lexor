# LEXOR Payment Microservice (Spring Boot)
> Intelligent Mobility. Elevated.

The **LEXOR Payment Service** provides a production-grade, secure, multi-method payment architecture inspired by premier mobility platforms (Ola & Uber). It orchestrates payment initiation, Razorpay Indian market integration (UPI, Cards, Net Banking), driver physical cash reconciliation, cryptographic webhook processing, idempotent state transitions, immutable double-entry financial ledger recording, and administrative audit/refund operations.

---

## 1. Architectural Overview

```
                                      +------------------------------------+
                                      |     LEXOR API Gateway (8080/8081)  |
                                      +-----------------+------------------+
                                                        |
                                          HTTP / REST (JWT Auth)
                                                        v
+------------------------------------------------------------------------------------------------------+
|                                   LEXOR Payment Service (Port 8097)                                  |
|                                                                                                      |
|   +-----------------------+   +------------------------+   +---------------------+                   |
|   | RiderPaymentController|   | DriverPaymentController|   | PaymentWebhookCtrl  |                   |
|   +-----------+-----------+   +-----------+------------+   +----------+----------+                   |
|               |                           |                           |                              |
|               v                           v                           v                              |
|   +----------------------------------------------------------------------------------------------+   |
|   |                                       PaymentService                                         |   |
|   |   - Validates booking ownership, fare integrity, and idempotent requests                     |   |
|   |   - Enforces explicit Payment State Machine (CREATED -> PENDING -> SUCCEEDED / REFUNDED)     |   |
|   +-------+-------------------------------+------------------------------+-----------------------+   |
|           |                               |                              |                           |
|           v                               v                              v                           |
|   +---------------+               +---------------+              +---------------+                   |
|   |IPaymentProvider|               |ILedgerService |              |IBookingClient |                   |
|   |  (Razorpay)   |               |  (Immutable)  |              |  (Downstream) |                   |
|   +-------+-------+               +-------+-------+              +-------+-------+                   |
+-----------|-------------------------------|------------------------------|---------------------------+
            |                               |                              |
            v                               v                              v
   +-----------------+             +-----------------+            +-----------------+
   | Razorpay Sandbox|             | MySQL 8 Schema  |            | Booking Service |
   | (UPI/Card/NB)   |             | (lexor_db / V9) |            |   (Port 8095)   |
   +-----------------+             +-----------------+            +-----------------+
```

---

## 2. Technology Stack & Prerequisites

- **Language & Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 3.2.5 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`)
- **JWT**: `io.jsonwebtoken:jjwt-api:0.12.5`
- **Database**: MySQL 8.0+ (InnoDB) with `com.mysql:mysql-connector-j`
- **In-Memory Testing**: H2 In-Memory Database (`com.h2database:h2`)
- **API Documentation**: Springdoc OpenAPI Swagger UI 2.5.0
- **Default Port**: `8097`

---

## 3. Database Schema & Migration (V9)

The Payment Service owns dedicated additive tables in `database/mysql/migrations/V9__payment_service_additive.sql`:
1. `payments`: Authoritative payment records with UUIDs, status, amount in INR and paise, method, and optimistic locking (`version`).
2. `payment_transactions`: Auditable log of all payment attempts, capture requests, and gateway responses.
3. `payment_refunds`: Authoritative full/partial refund tracking with provider refund IDs and audit reasons.
4. `payment_webhook_events`: Durable ingress log with unique provider event IDs for deduplication.
5. `payment_idempotency_records`: Anti-double charging concurrency cache for requests bearing `Idempotency-Key`.
6. `financial_ledger_entries`: Append-only, double-entry financial ledger recording platform commission and driver earnings.
7. `driver_payout_records`: Settlement reconciliation boundaries for periodic driver bank payouts.

### Manual Migration Execution
Per repository safety protocols, migrations are NOT automatically applied to live databases.
```bash
mysql -u root -p lexor_db < database/mysql/migrations/V9__payment_service_additive.sql
```

---

## 4. Payment Modes & Capabilities

| Method | Provider Interaction | Lifecycle & Description |
|---|---|---|
| **UPI** | Razorpay Sandbox Order | Returns UPI intent URI (`upi://pay?...`) and QR string. Verified and captured via webhook or signature verification. |
| **CARD** | Razorpay Hosted Checkout | Supports tokenized card checkout. No raw CVV or PIN numbers are ever handled or stored. |
| **NET_BANKING** | Razorpay Bank Rail | Supports 50+ Indian commercial banks (HDFC, ICICI, SBI, Axis, Kotak). |
| **CASH** | No Gateway Order | Rider selects cash; payment created in `PENDING` state. Upon trip completion, driver calls cash collection endpoint to confirm physical receipt, moving state to `SUCCEEDED` and posting platform commission liability. |
| **WALLET / CREDITS** | Architectural Boundary | Ledger structures (`financial_ledger_entries`) support double-entry promotional credits without real-money deposit liabilities. |

---

## 5. Financial Ledger & Revenue Split

- **Default Platform Commission**: 20.00% (configurable via `PLATFORM_COMMISSION_PERCENTAGE`)
- **Driver Net Earnings**: 80.00%
- **Digital Payment Flow**:
  - Credit `GATEWAY_CLEARING` (100% of fare)
  - Credit `PLATFORM_REVENUE` (20% commission)
  - Credit `DRIVER_PAYABLE` (80% driver payout due)
- **Cash Ride Flow**:
  - Debit `CASH_CLEARING` (100% physical cash received by driver)
  - Debit `DRIVER_CASH_LIABILITY` (Driver holds cash, platform records 20% commission liability)
  - Credit `DRIVER_PAYABLE` (80% driver net earnings retained directly from cash)
- **Monetary Precision**: Handled using `BigDecimal` with 2 decimal places (`RoundingMode.HALF_UP`) and integer paise to eliminate binary floating-point inaccuracies.

---

## 6. API Endpoints

### Rider Endpoints (`ROLE_RIDER`)
- `GET /api/v1/payments/methods`: List active payment methods.
- `POST /api/v1/payments`: Initiate payment for an authorized booking (requires `Idempotency-Key`).
- `GET /api/v1/payments/me`: Paginated payment history for authenticated rider.
- `GET /api/v1/payments/me/{paymentUuid}`: Detailed payment record (enforces rider ownership).
- `GET /api/v1/payments/me/{paymentUuid}/receipt`: Itemized ride payment receipt (fare breakdown, commission, tax).
- `POST /api/v1/payments/{paymentUuid}/retry`: Retry failed or expired payment attempt.
- `POST /api/v1/payments/{paymentUuid}/refund`: Request refund for eligible payment.
- `GET /api/v1/payments/{paymentUuid}/status`: Authoritative payment status check.

### Driver Endpoints (`ROLE_DRIVER`)
- `POST /api/v1/driver/payments/{paymentUuid}/cash-collection`: Confirm physical cash collection for assigned trip.

### Provider Webhooks (Public with Signature Verification)
- `POST /api/v1/payments/webhooks/{provider}`: Cryptographically verified (`X-Razorpay-Signature` HMAC-SHA256) callback receiver with event deduplication.

### Administrator Endpoints (`ROLE_ADMIN`)
- `GET /api/v1/admin/payments`: Filter platform payments by status, method, rider, driver.
- `GET /api/v1/admin/payments/{paymentUuid}`: Full payment details and metadata.
- `POST /api/v1/admin/payments/{paymentUuid}/refund`: Issue administrative refund with mandatory audit notes.
- `GET /api/v1/admin/payments/reconciliation`: Platform-wide ledger balances and reconciliation metrics.
- `GET /api/v1/admin/payments/{paymentUuid}/audit`: Complete low-level transaction attempt history.

### Health Endpoints (Public)
- `GET /health`: Liveness probe (`{"status":"UP","service":"payment-service"}`).
- `GET /health/ready`: Readiness probe (`{"status":"READY","service":"payment-service"}`).

---

## 7. Local Startup & Verification Commands

```bash
# Build and execute all integration tests
mvn clean test

# Run application locally on port 8097
mvn spring-boot:run
```

### Test Results
- **Suite**: `PaymentServiceIntegrationTest`
- **Tests Passed**: 13
- **Failures / Errors**: 0
- **Build Status**: SUCCESS
