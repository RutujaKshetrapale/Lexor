# LEXOR — MySQL Database Architecture & Setup Guide

Welcome to the official **MySQL Database Layer** for the **LEXOR AI-Powered Smart Mobility Platform**.

---

## 📌 Single Database Architecture

LEXOR uses **one physical MySQL database**:

- **Database Name:** `lexor_db`
- **Database Engine:** MySQL 8.0+ (InnoDB Engine)
- **Character Set:** `utf8mb4`
- **Collation:** `utf8mb4_unicode_ci`
- **Default Currency:** `INR` (Indian Rupee)
- **Total Tables:** 24 relational tables

All 24 core platform tables reside inside `lexor_db`.

---

## 🚀 Quick Setup in MySQL Workbench (Primary Workflow)

To set up and test the database directly on your local MySQL Server:

```text
Step 1: Open MySQL Workbench
        ↓
Step 2: Run database/mysql/schema.sql   (Creates lexor_db + all 24 tables)
        ↓
Step 3: Run database/mysql/seed_data.sql (Populates demo data for all 24 tables)
        ↓
Database Ready for Querying & Testing!
```

### Exact Execution Commands

#### Option A: MySQL Workbench / Query Tab
1. Open `database/mysql/schema.sql` and click **Execute (⚡)**.
2. Open `database/mysql/seed_data.sql` and click **Execute (⚡)**.

#### Option B: MySQL Command Line Client / Terminal
```bash
# 1. Create database and 24 tables
mysql -u root -p < database/mysql/schema.sql

# 2. Populate demo data across all 24 tables
mysql -u root -p lexor_db < database/mysql/seed_data.sql
```

---

## 📦 Future Migration Note (Flyway Integration)
The `database/mysql/migrations/` directory contains versioned scripts (`V1__init_schema.sql`, `V2__seed_data.sql`). These files are preserved for future automated Flyway migrations when integrating the Spring Boot backend microservices. At present, direct execution of `schema.sql` and `seed_data.sql` is the primary development workflow.

---

## 🗄️ Database Tables & Domain Breakdown (24 Tables)

### 1. Identity & Profile Module
1. **`users`**: Central identity & authentication table storing global role (`RIDER`, `DRIVER`, `ADMIN`), UUID, credentials (`password_hash`), contact details, and account status.
2. **`rider_profiles`**: 1:1 profile for riders (`user_id UNIQUE`). Stores rating, total rides, preferred payment method (`UPI`), and default addresses. Supports two-sided ratings.
3. **`driver_profiles`**: 1:1 profile for drivers (`user_id UNIQUE`). Stores license number, expiry date, online status (`OFFLINE`, `ONLINE`, `IN_TRIP`, `SUSPENDED`), rating, acceptance/cancellation rates, and verification status.
4. **`admin_profiles`**: 1:1 profile for admins (`user_id UNIQUE`). Stores department assignment and access permission level (`SUPER_ADMIN`, `OPERATIONS`, `SUPPORT`, `FINANCE`).

### 2. Vehicle & Ride Category Module
5. **`vehicles`**: Fleet vehicle registry owned by drivers (`driver_id REFERENCES driver_profiles`). Stores make, model, license plate, VIN, seat capacity, inspection expiration, and vehicle category (`ride_type_category`).
   * *Extensibility Note:* Uses `vehicles.ride_type_category VARCHAR(30)` for current MVP simplicity. A junction table `vehicle_ride_types` can be added in future versions if a vehicle requires mapping to multiple ride categories.
6. **`ride_types`**: Ride tiers (`LEXOR Go`, `LEXOR XL`, `LEXOR Premier`, `LEXOR Green`), defining base fare (₹), per-km rate (₹/km), per-minute rate (₹/min), minimum fare, and cancellation fees in `INR`.

### 3. Promotions & Discounts Module
7. **`coupons`**: Discount coupons (`PERCENTAGE` or `FIXED_AMOUNT` in `INR`) with spending thresholds, validity periods, usage caps, and tracking counters.

### 4. Ride Lifecycle & Booking Module
8. **`ride_requests`**: Spatial ride request submissions with status tracking (`REQUESTED`, `SEARCHING`, `MATCHED`, `CANCELLED`, `EXPIRED`).
   * *Lifecycle:* `REQUESTED` → `SEARCHING` → `MATCHED` → Booking Creation & Driver Assignment.
9. **`bookings`**: Confirmed trip bookings linking rider, driver, vehicle, ride request, and ride type.
   * *OTP Security:* Stores securely hashed OTP credentials (`otp_hash VARCHAR(255)`), expiration timestamp (`otp_expires_at`), and verification timestamp (`otp_verified_at`) rather than plaintext OTP codes.
10. **`rides`**: Central trip entity driving the 10-state lifecycle:
    `REQUESTED` → `SEARCHING_DRIVER` → `DRIVER_ASSIGNED` → `DRIVER_ARRIVING` → `DRIVER_ARRIVED` → `TRIP_STARTED` → `TRIP_COMPLETED` → `PAYMENT_COMPLETED` → `FEEDBACK` (or `CANCELLED`).
11. **`ride_status_history`**: Audit trail recording state transitions along with timestamps, triggering user ID, and optional notes.
12. **`ride_stops`**: Multi-stop routing support for intermediate trip waypoints.
13. **`driver_locations`**: Historical driver location telemetry (GPS coordinates, heading, speed).
    * *Architecture Note:* Active real-time driver locations live primarily in **Redis** for sub-millisecond proximity lookups. MySQL `driver_locations` stores persistent historical location logs for audit and route replaying.

### 5. Financial, Payments & Refunds Module
14. **`fare_breakdowns`**: Granular financial breakdown per ride in `INR` (base, distance, time, surge multiplier, surge amount, tax, discount, total fare, driver earnings, platform commission). `currency` defaults to `'INR'`.
15. **`payments`**: Core payment records tracking payment method (`CARD`, `CASH`, `WALLET`, `UPI`) and payment lifecycle status (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `REFUNDED`, `PARTIALLY_REFUNDED`).
16. **`payment_transactions`**: Gateway transaction logs (Razorpay Sandbox) storing external gateway transaction IDs, reference IDs, status, currency (`'INR'`), and raw response JSON.
17. **`refunds`**: Refund requests and audit records in `INR` linked to payments.

### 6. Ratings, Feedback & AI Analytics Module
18. **`favorite_locations`**: Saved addresses (Home, Work, Gym) for riders.
19. **`feedback`**: Post-trip ratings (1-5 stars) supporting two-sided feedback (`driver_rating` and `rider_rating`), cleanliness rating, safety rating, and written rider comments.
20. **`ai_feedback_analysis`**: Asynchronous AI sentiment analysis outputs (sentiment classification, sentiment score `-1.0 to +1.0`, topic identification, severity classification, summary, and automated recommendation).

### 7. Safety, Notifications & Audit Module
21. **`notifications`**: User push/in-app notifications with unread tracking.
22. **`emergency_contacts`**: User safety contacts.
23. **`sos_events`**: High-priority Emergency SOS trigger records containing GPS coordinates, status, and resolution details.
24. **`admin_actions`**: Operational audit log capturing administrative actions (suspensions, refunds, config updates) with IP addresses and JSON detail payloads.

---

## 🔍 Verification Queries

After executing `schema.sql` and `seed_data.sql`, run these verification queries in MySQL Workbench:

```sql
USE lexor_db;

-- 1. Verify exact count of tables (Should return 24)
SELECT COUNT(*) AS table_count 
FROM information_schema.tables 
WHERE table_schema = 'lexor_db';

-- 2. List all 24 tables
SHOW TABLES;

-- 3. Verify users & role profiles
SELECT id, uuid, first_name, last_name, email, role, status FROM users;
SELECT * FROM rider_profiles;
SELECT * FROM driver_profiles;
SELECT * FROM admin_profiles;

-- 4. Verify vehicles & ride types
SELECT * FROM vehicles;
SELECT id, name, code, base_fare, per_km_rate, minimum_fare FROM ride_types;

-- 5. Verify rides, fare breakdown, payments & AI analysis
SELECT id, uuid, status, pickup_address, drop_address FROM rides;
SELECT * FROM fare_breakdowns;
SELECT * FROM payments;
SELECT * FROM payment_transactions;
SELECT * FROM feedback;
SELECT * FROM ai_feedback_analysis;
```
