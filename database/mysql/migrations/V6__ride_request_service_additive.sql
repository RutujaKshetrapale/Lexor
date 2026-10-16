-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Ride Request Service Additive Migration (V6)
-- Extends ride_requests table to add cancellation audit fields, scheduled ride support,
-- and query optimization indexes.
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Modify ride_requests table with cancellation tracking and scheduled ride mode
ALTER TABLE ride_requests
    ADD COLUMN cancellation_reason VARCHAR(500) NULL AFTER status,
    ADD COLUMN cancelled_at TIMESTAMP NULL DEFAULT NULL AFTER cancellation_reason,
    ADD COLUMN ride_mode ENUM('IMMEDIATE', 'SCHEDULED') NOT NULL DEFAULT 'IMMEDIATE' AFTER cancelled_at,
    ADD COLUMN scheduled_pickup_time TIMESTAMP NULL DEFAULT NULL AFTER ride_mode,
    ADD INDEX idx_ride_req_rider_status (rider_id, status),
    ADD INDEX idx_ride_req_mode (ride_mode);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Verification Instructions:
-- 1. Prerequisites: Ensure V1__init_schema.sql has been applied to lexor_db.
-- 2. Execute migration:
--    mysql -u root -p lexor_db < database/mysql/migrations/V6__ride_request_service_additive.sql
-- 3. Verify columns:
--    DESCRIBE lexor_db.ride_requests;
-- 4. Rollback (if necessary):
--    ALTER TABLE ride_requests
--        DROP COLUMN cancellation_reason,
--        DROP COLUMN cancelled_at,
--        DROP COLUMN ride_mode,
--        DROP COLUMN scheduled_pickup_time,
--        DROP INDEX idx_ride_req_rider_status,
--        DROP INDEX idx_ride_req_mode;
-- =============================================================================
