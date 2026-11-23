# LEXOR — Authoritative Ride Lifecycle API Contracts

## 1. Scope & Common Conventions
This document defines the REST API contracts, headers, request/response JSON schemas, and HTTP status codes governing the LEXOR ride lifecycle. These contracts are symmetrically implemented across:
- **Spring Boot 3.2.x** (`/api/v1/...`)
- **.NET 9.0** (`/api/v1/...`)
- **API Gateways**: Spring Cloud Gateway (port 8080) and YARP .NET Gateway (port 5000).

### Standard Headers
| Header | Description | Required | Example |
| :--- | :--- | :--- | :--- |
| `Authorization` | Bearer JWT Token issued by Auth Service | Yes (except public) | `Bearer eyJhbGciOi...` |
| `X-Correlation-Id` | Distributed tracing request correlation UUID | Recommended | `c8b9d0e1-4567-4890-a123-456789abcdef` |
| `Idempotency-Key` | Safe retry token for financial and booking state | Optional / Recommended | `IDEM-98249-112` |
| `X-Internal-Service-Key` | Service-to-service authorization token | Internal Only | Set via `SECURITY_INTERNAL_SERVICE_KEY` |

---

## 2. Rider API Surface

### 2.1 Create Ride Request
- **Endpoint**: `POST /api/v1/ride-requests`
- **Role**: `ROLE_RIDER`
- **Request Body**:
```json
{
  "pickupAddress": "Indiranagar 100ft Rd, Bengaluru",
  "pickupLatitude": 12.9784,
  "pickupLongitude": 77.6408,
  "dropAddress": "Prestige Tech Park, Marathahalli, Bengaluru",
  "dropLatitude": 12.9372,
  "dropLongitude": 77.6949,
  "rideTypeCode": "GO"
}
```
- **Response `201 Created`**:
```json
{
  "requestUuid": "req-98234-a1",
  "riderUuid": "u-rider-001",
  "rideTypeCode": "GO",
  "pickupAddress": "Indiranagar 100ft Rd, Bengaluru",
  "pickupLatitude": 12.9784,
  "pickupLongitude": 77.6408,
  "dropAddress": "Prestige Tech Park, Marathahalli, Bengaluru",
  "dropLatitude": 12.9372,
  "dropLongitude": 77.6949,
  "estimatedDistanceKm": 7.4,
  "estimatedDurationMinutes": 22,
  "estimatedFare": 340.00,
  "currency": "INR",
  "status": "SEARCHING",
  "requestedAt": "2026-10-29T15:30:00Z"
}
```

### 2.2 Create Confirmed Booking (Trigger Matching & Dispatch)
- **Endpoint**: `POST /api/v1/bookings`
- **Role**: `ROLE_RIDER`
- **Request Body**:
```json
{
  "rideRequestUuid": "req-98234-a1",
  "scheduledTime": null,
  "idempotencyKey": "idemp-001-rider"
}
```
- **Response `201 Created`**:
```json
{
  "bookingUuid": "bk-e82a-4421",
  "rideRequestUuid": "req-98234-a1",
  "status": "CONFIRMED",
  "otp": "4829",
  "otpVerified": false,
  "pickupAddress": "Indiranagar 100ft Rd, Bengaluru",
  "dropAddress": "Prestige Tech Park, Marathahalli, Bengaluru",
  "totalFare": 340.00,
  "currency": "INR",
  "driver": {
    "uuid": "drv-001",
    "firstName": "Vikram",
    "lastName": "Singh",
    "phone": "+919876543210",
    "rating": 4.92,
    "licenseNumber": "KA-01-2022-0001"
  },
  "vehicle": {
    "uuid": "veh-001",
    "make": "Toyota",
    "model": "Hyryder",
    "color": "Black",
    "licensePlate": "KA-01-EQ-4829",
    "category": "GO"
  },
  "createdAt": "2026-10-29T15:30:15Z"
}
```
- **Error Responses**:
  - `409 Conflict`: `{"code": "CONFLICT", "message": "No eligible driver has been matched or reserved for this ride request yet."}`
  - `403 Forbidden`: Authenticated user is not the owner of the ride request.

### 2.3 Cancel Booking
- **Endpoint**: `POST /api/v1/bookings/{bookingUuid}/cancel`
- **Role**: `ROLE_RIDER`
- **Request Body**:
```json
{
  "reason": "Driver taking too long"
}
```
- **Response `200 OK`**:
```json
{
  "bookingUuid": "bk-e82a-4421",
  "status": "CANCELLED_BY_RIDER",
  "cancellationReason": "Driver taking too long",
  "cancelledBy": "RIDER",
  "cancelledAt": "2026-10-29T15:32:00Z"
}
```

### 2.4 Get Authoritative Receipt
- **Endpoint**: `GET /api/v1/payments/me/{paymentOrBookingUuid}/receipt`
- **Role**: `ROLE_RIDER`
- **Response `200 OK`**:
```json
{
  "receiptNumber": "RCP-FB637C0C",
  "paymentUuid": "fb637c0c-2644-4a6a-8fb8-3ea41e0960a2",
  "bookingUuid": "bk-e82a-4421",
  "paidAt": "2026-10-29T15:55:00Z",
  "riderName": "Rajesh Kumar",
  "driverName": "Vikram Singh",
  "vehicleModel": "Toyota Hyryder",
  "licensePlate": "KA-01-EQ-4829",
  "pickupAddress": "Indiranagar 100ft Rd, Bengaluru",
  "dropAddress": "Prestige Tech Park, Marathahalli, Bengaluru",
  "paymentMethod": "UPI",
  "status": "SUCCEEDED",
  "amountPaid": 340.00,
  "currency": "INR",
  "fareBreakdown": {
    "baseFare": 50.00,
    "distanceFare": 220.00,
    "timeFare": 50.00,
    "taxAmount": 20.00,
    "totalFare": 340.00,
    "platformCommission": 68.00,
    "driverEarnings": 272.00
  }
}
```

---

## 3. Driver Partner API Surface

### 3.1 Availability Duty Toggle
- **Endpoint**: `PATCH /api/v1/drivers/me/availability`
- **Role**: `ROLE_DRIVER`
- **Request Body**:
```json
{
  "isAvailable": true
}
```
- **Response `200 OK`**:
```json
{
  "driverUuid": "drv-001",
  "isAvailable": true,
  "status": "ONLINE"
}
```
- **Error `400 / 403`**: If driver status is not `VERIFIED` or lacks an approved vehicle.

### 3.2 Driver GPS Telemetry Beacon
- **Endpoint**: `PUT /api/v1/drivers/me/location`
- **Role**: `ROLE_DRIVER`
- **Request Body**:
```json
{
  "latitude": 12.9784,
  "longitude": 77.6408,
  "heading": 142.5,
  "speedKmh": 28.0,
  "timestamp": "2026-10-29T15:35:00Z"
}
```
- **Response `200 OK`**: `{"status": "RECORDED"}`

### 3.3 Accept Ride Offer (Atomic Claim)
- **Endpoint**: `POST /api/v1/drivers/me/bookings/{bookingUuid}/accept`
- **Role**: `ROLE_DRIVER`
- **Response `200 OK`**:
```json
{
  "bookingUuid": "bk-e82a-4421",
  "status": "ACCEPTED",
  "acceptedAt": "2026-10-29T15:30:25Z"
}
```
- **Error Responses**:
  - `409 Conflict`: Offer expired or already claimed by another driver.
  - `403 Forbidden`: Driver not assigned to this offer.

### 3.4 Confirm Arrival at Pickup
- **Endpoint**: `POST /api/v1/drivers/me/bookings/{bookingUuid}/arrived`
- **Role**: `ROLE_DRIVER`
- **Response `200 OK`**:
```json
{
  "bookingUuid": "bk-e82a-4421",
  "status": "ARRIVED",
  "arrivedAt": "2026-10-29T15:35:00Z"
}
```

### 3.5 Start Trip with OTP Verification
- **Endpoint**: `POST /api/v1/drivers/me/bookings/{bookingUuid}/start`
- **Role**: `ROLE_DRIVER`
- **Request Body**:
```json
{
  "otp": "4829"
}
```
- **Response `200 OK`**:
```json
{
  "bookingUuid": "bk-e82a-4421",
  "status": "IN_PROGRESS",
  "startedAt": "2026-10-29T15:36:10Z",
  "otpVerified": true
}
```
- **Error Responses**:
  - `400 Bad Request`: Invalid OTP code (attempt 1 or 2).
  - `409 Conflict`: Maximum OTP attempts exceeded; booking locked.

### 3.6 Complete Trip
- **Endpoint**: `POST /api/v1/drivers/me/bookings/{bookingUuid}/complete`
- **Role**: `ROLE_DRIVER`
- **Request Body**:
```json
{
  "actualDistanceKm": 7.6,
  "actualDurationMinutes": 24
}
```
- **Response `200 OK`**:
```json
{
  "bookingUuid": "bk-e82a-4421",
  "status": "COMPLETED",
  "completedAt": "2026-10-29T15:58:00Z",
  "actualDistanceKm": 7.6,
  "actualDurationMinutes": 24,
  "totalFare": 340.00
}
```

### 3.7 In-Person Cash Collection Confirmation
- **Endpoint**: `POST /api/v1/driver/payments/{paymentOrBookingUuid}/cash-collection`
- **Role**: `ROLE_DRIVER`
- **Response `200 OK`**:
```json
{
  "paymentUuid": "pmt-bk-e82a-4421",
  "bookingUuid": "bk-e82a-4421",
  "status": "SUCCEEDED",
  "paymentMethod": "CASH",
  "amount": 340.00,
  "currency": "INR",
  "collectedAt": "2026-10-29T15:58:30Z"
}
```

---

## 4. Two-Sided Ratings & Reviews

### 4.1 Rider Rates Driver
- **Endpoint**: `POST /api/v1/feedback/rides/{rideUuid}/driver-rating`
- **Role**: `ROLE_RIDER`
- **Request Body**:
```json
{
  "rating": 5,
  "comment": "Smooth driving and very clean car",
  "tagCodes": ["CLEAN_VEHICLE", "SAFE_DRIVER"]
}
```
- **Response `201 Created`**:
```json
{
  "feedbackUuid": "fb-0912-a1",
  "rideUuid": "bk-e82a-4421",
  "rating": 5,
  "reviewerRole": "RIDER",
  "moderationStatus": "VISIBLE",
  "createdAt": "2026-10-29T16:00:00Z"
}
```

### 4.2 Driver Rates Rider
- **Endpoint**: `POST /api/v1/drivers/me/rides/{rideUuid}/rider-rating`
- **Role**: `ROLE_DRIVER`
- **Request Body**:
```json
{
  "rating": 5,
  "comment": "Punctual passenger, ready at pickup location"
}
```
- **Response `201 Created`**

---

## 5. Admin Operations Surface

### 5.1 Real-Time Live Fleet Snapshot
- **Endpoint**: `GET /api/v1/admin/operations/live-snapshot`
- **Role**: `ROLE_ADMIN`
- **Response `200 OK`**:
```json
{
  "onlineDriversCount": 14,
  "activeTripsCount": 3,
  "dispatchedOffersCount": 1,
  "drivers": [
    {
      "driverUuid": "drv-001",
      "fullName": "Vikram Singh",
      "status": "ENGAGED",
      "latitude": 12.9550,
      "longitude": 77.6600,
      "freshness": "LIVE"
    }
  ]
}
```

### 5.2 Emergency Administrative Ride Cancellation
- **Endpoint**: `POST /api/v1/admin/operations/emergency-cancel-ride`
- **Role**: `ROLE_ADMIN`
- **Request Body**:
```json
{
  "bookingUuid": "bk-e82a-4421",
  "reason": "Vehicle breakdown reported by traffic police",
  "waiveFee": true
}
```
- **Response `200 OK`**:
```json
{
  "bookingUuid": "bk-e82a-4421",
  "status": "CANCELLED_BY_ADMIN",
  "auditLogId": "aud-89241"
}
```
