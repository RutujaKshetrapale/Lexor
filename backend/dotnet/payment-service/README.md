# LEXOR Payment Microservice (ASP.NET Core .NET 9)
> Intelligent Mobility. Elevated.

The **LEXOR Payment Service (.NET)** provides an enterprise-grade ride-hailing payment backend using ASP.NET Core 9.0 and Entity Framework Core with Pomelo MySQL. It exposes 100% contract parity with the Spring Boot implementation, supporting UPI, Credit/Debit cards, Net Banking, Driver Cash collection reconciliation, cryptographic webhook signature verification, and double-entry immutable ledger accounting.

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
|                                   LEXOR Payment Service (Port 8098)                                  |
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
   | (UPI/Card/NB)   |             | (lexor_db / V9) |            |   (Port 8096)   |
   +-----------------+             +-----------------+            +-----------------+
```

---

## 2. Technology Stack & Prerequisites

- **Language & Runtime**: C# 13 / .NET 9.0 (`net9.0`)
- **Web Framework**: ASP.NET Core 9.0 Web API
- **Data Access**: Entity Framework Core 9.0 with `Pomelo.EntityFrameworkCore.MySql` (9.0.0-preview.2)
- **Testing Engine**: xUnit 2.9.3 with `Microsoft.EntityFrameworkCore.InMemory` (9.0.2)
- **API Documentation**: Swashbuckle Swagger UI 6.6.2
- **Default Port**: `8098`

---

## 3. Database Schema & Migration (V9)

The Payment Service owns dedicated additive tables defined in `database/mysql/migrations/V9__payment_service_additive.sql`:
1. `payments`: Authoritative payment records with UUIDs, status, decimal amounts, paise, and optimistic concurrency tokens (`Version`).
2. `payment_transactions`: Auditable log of all payment attempts and gateway payloads.
3. `payment_refunds`: Authoritative refund tracking.
4. `payment_webhook_events`: Durable webhook log with deduplication constraint on `(provider, provider_event_id)`.
5. `payment_idempotency_records`: Concurrency cache for `Idempotency-Key` headers.
6. `financial_ledger_entries`: Append-only, double-entry financial ledger recording platform commission and driver earnings.
7. `driver_payout_records`: Settlement reconciliation boundaries for driver payouts.

### Manual Migration Execution
```bash
mysql -u root -p lexor_db < database/mysql/migrations/V9__payment_service_additive.sql
```

---

## 4. Payment Modes & Capabilities

| Method | Provider Interaction | Lifecycle & Description |
|---|---|---|
| **UPI** | Razorpay Sandbox Order | Returns UPI intent URI (`upi://pay?...`) and QR string. Verified and captured via webhook or signature verification. |
| **CARD** | Razorpay Hosted Checkout | Supports tokenized card checkout. No raw CVV or PIN numbers are ever handled or stored. |
| **NET_BANKING** | Razorpay Bank Rail | Supports 50+ Indian commercial banks. |
| **CASH** | No Gateway Order | Rider selects cash; payment created in `PENDING` state. Upon trip completion, driver calls cash collection endpoint to confirm physical receipt, moving state to `SUCCEEDED` and posting platform commission liability. |
| **WALLET / CREDITS** | Architectural Boundary | Ledger structures (`financial_ledger_entries`) support double-entry promotional credits without real-money deposit liabilities. |

---

## 5. Financial Ledger & Revenue Split

- **Default Platform Commission**: 20.00% (configurable via `Payment:CommissionPercentage`)
- **Driver Net Earnings**: 80.00%
- **Digital Payment Flow**:
  - Credit `GATEWAY_CLEARING` (100% of fare)
  - Credit `PLATFORM_REVENUE` (20% commission)
  - Credit `DRIVER_PAYABLE` (80% driver payout due)
- **Cash Ride Flow**:
  - Debit `CASH_CLEARING` (100% physical cash received by driver)
  - Debit `DRIVER_CASH_LIABILITY` (Driver holds cash, platform records 20% commission liability)
  - Credit `DRIVER_PAYABLE` (80% driver net earnings retained directly from cash)
- **Monetary Precision**: Handled using `decimal` with 2 decimal places (`MidpointRounding.AwayFromZero`) and integer paise.

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
dotnet test

# Run application locally on port 8098
dotnet run
```

### Test Results
- **Suite**: `PaymentServiceIntegrationTests`
- **Tests Passed**: 13
- **Failures / Errors**: 0
- **Duration**: ~2 seconds
- **Build Status**: SUCCESS (0 warnings, 0 errors)
