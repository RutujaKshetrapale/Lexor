-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Driver Matching Service Additive Migration (V7)
-- Creates matching_attempts and driver_reservations tables for tracking
-- candidate evaluations, deterministic scoring, and atomic reservation concurrency.
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Create Table: matching_attempts (Historical and Active Matching Attempts)
CREATE TABLE IF NOT EXISTS matching_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_uuid VARCHAR(36) NOT NULL UNIQUE,
    ride_request_id BIGINT NOT NULL,
    ride_request_uuid VARCHAR(36) NOT NULL,
    selected_driver_id BIGINT NULL,
    selected_vehicle_id BIGINT NULL,
    status ENUM('PENDING', 'SEARCHING', 'MATCHED', 'NO_DRIVERS_FOUND', 'CANCELLED', 'FAILED', 'EXPIRED') NOT NULL DEFAULT 'PENDING',
    attempt_number INT UNSIGNED NOT NULL DEFAULT 1,
    candidates_evaluated INT UNSIGNED NOT NULL DEFAULT 0,
    distance_km DECIMAL(8, 2) NULL,
    match_score DECIMAL(5, 2) NULL,
    failure_reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_matching_attempts_ride_req FOREIGN KEY (ride_request_id) REFERENCES ride_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_matching_attempts_driver FOREIGN KEY (selected_driver_id) REFERENCES driver_profiles (id) ON DELETE SET NULL,
    CONSTRAINT fk_matching_attempts_vehicle FOREIGN KEY (selected_vehicle_id) REFERENCES vehicles (id) ON DELETE SET NULL,
    INDEX idx_match_attempt_req_uuid (ride_request_uuid),
    INDEX idx_match_attempt_driver (selected_driver_id),
    INDEX idx_match_attempt_status (status),
    INDEX idx_match_attempt_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Create Table: driver_reservations (Atomic Concurrency Locks for Matched Drivers)
CREATE TABLE IF NOT EXISTS driver_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_uuid VARCHAR(36) NOT NULL UNIQUE,
    driver_id BIGINT NOT NULL,
    ride_request_id BIGINT NOT NULL,
    matching_attempt_id BIGINT NOT NULL,
    status ENUM('RESERVED', 'CONFIRMED', 'RELEASED', 'EXPIRED') NOT NULL DEFAULT 'RESERVED',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_reservations_ride_req FOREIGN KEY (ride_request_id) REFERENCES ride_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_reservations_attempt FOREIGN KEY (matching_attempt_id) REFERENCES matching_attempts (id) ON DELETE CASCADE,
    INDEX idx_reservations_driver_status (driver_id, status, expires_at),
    INDEX idx_reservations_ride_req (ride_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Verification Instructions:
-- 1. Prerequisites: Ensure V1 through V6 have been applied to lexor_db.
-- 2. Execute migration manually:
--    mysql -u root -p lexor_db < database/mysql/migrations/V7__driver_matching_service_additive.sql
-- 3. Verify tables:
--    DESCRIBE lexor_db.matching_attempts;
--    DESCRIBE lexor_db.driver_reservations;
-- 4. Rollback (if necessary):
--    DROP TABLE IF EXISTS driver_reservations;
--    DROP TABLE IF EXISTS matching_attempts;
-- =============================================================================
