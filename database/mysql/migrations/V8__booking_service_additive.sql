-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Booking Service Additive Migration (V8)
-- Extends bookings table to support complete booking and journey lifecycle:
-- enhanced booking_status ENUM, idempotency keys, journey audit timestamps,
-- and denormalized pickup/drop coordinate and fare audit fields.
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Extend booking_status ENUM and add operational/audit columns to bookings table
ALTER TABLE bookings
    MODIFY COLUMN booking_status ENUM(
        'OFFERED',
        'DRIVER_ASSIGNED',
        'CONFIRMED',
        'ACCEPTED',
        'ARRIVED',
        'IN_PROGRESS',
        'COMPLETED',
        'CANCELLED_BY_RIDER',
        'CANCELLED_BY_DRIVER',
        'CANCELLED_BY_ADMIN',
        'DECLINED_BY_DRIVER',
        'EXPIRED'
    ) NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN idempotency_key VARCHAR(100) NULL AFTER cancellation_reason,
    ADD COLUMN accepted_at TIMESTAMP NULL DEFAULT NULL AFTER idempotency_key,
    ADD COLUMN arrived_at TIMESTAMP NULL DEFAULT NULL AFTER accepted_at,
    ADD COLUMN started_at TIMESTAMP NULL DEFAULT NULL AFTER arrived_at,
    ADD COLUMN completed_at TIMESTAMP NULL DEFAULT NULL AFTER started_at,
    ADD COLUMN cancelled_at TIMESTAMP NULL DEFAULT NULL AFTER completed_at,
    ADD COLUMN cancelled_by VARCHAR(50) NULL DEFAULT NULL AFTER cancelled_at,
    ADD COLUMN pickup_address VARCHAR(255) NULL AFTER cancelled_by,
    ADD COLUMN pickup_latitude DECIMAL(10, 8) NULL AFTER pickup_address,
    ADD COLUMN pickup_longitude DECIMAL(11, 8) NULL AFTER pickup_latitude,
    ADD COLUMN drop_address VARCHAR(255) NULL AFTER pickup_longitude,
    ADD COLUMN drop_latitude DECIMAL(10, 8) NULL AFTER drop_address,
    ADD COLUMN drop_longitude DECIMAL(11, 8) NULL AFTER drop_latitude,
    ADD COLUMN estimated_distance_km DECIMAL(8, 2) NULL AFTER drop_longitude,
    ADD COLUMN estimated_duration_minutes INT NULL AFTER estimated_distance_km,
    ADD COLUMN total_fare DECIMAL(10, 2) NULL AFTER estimated_duration_minutes,
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR' AFTER total_fare,
    ADD INDEX idx_bookings_idempotency (idempotency_key),
    ADD INDEX idx_bookings_rider_status (rider_id, booking_status),
    ADD INDEX idx_bookings_driver_status (driver_id, booking_status);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Verification Instructions:
-- 1. Prerequisites: Ensure V1 through V7 have been applied to lexor_db.
-- 2. Execute migration manually:
--    mysql -u root -p lexor_db < database/mysql/migrations/V8__booking_service_additive.sql
-- 3. Verify columns:
--    DESCRIBE lexor_db.bookings;
-- 4. Rollback (if necessary):
--    ALTER TABLE bookings
--        DROP COLUMN idempotency_key,
--        DROP COLUMN accepted_at,
--        DROP COLUMN arrived_at,
--        DROP COLUMN started_at,
--        DROP COLUMN completed_at,
--        DROP COLUMN cancelled_at,
--        DROP COLUMN cancelled_by,
--        DROP COLUMN pickup_address,
--        DROP COLUMN pickup_latitude,
--        DROP COLUMN pickup_longitude,
--        DROP COLUMN drop_address,
--        DROP COLUMN drop_latitude,
--        DROP COLUMN drop_longitude,
--        DROP COLUMN estimated_distance_km,
--        DROP COLUMN estimated_duration_minutes,
--        DROP COLUMN total_fare,
--        DROP COLUMN currency,
--        DROP INDEX idx_bookings_idempotency,
--        DROP INDEX idx_bookings_rider_status,
--        DROP INDEX idx_bookings_driver_status,
--        MODIFY COLUMN booking_status ENUM('CONFIRMED', 'DRIVER_ASSIGNED', 'CANCELLED_BY_RIDER', 'CANCELLED_BY_DRIVER', 'EXPIRED') NOT NULL DEFAULT 'CONFIRMED';
-- =============================================================================
