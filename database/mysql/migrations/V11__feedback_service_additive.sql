-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Feedback & Ratings Service Additive Migration (V11)
-- Creates dedicated feedback tables for two-sided post-trip ratings (Rider -> Driver,
-- Driver -> Rider), structured feedback tags, tag assignments, review abuse reports,
-- auditable moderation history, and materialized rating summaries with breakdown.
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Table: ride_feedback (Authoritative Two-Sided Trip Reviews & Ratings)
CREATE TABLE IF NOT EXISTS ride_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feedback_uuid VARCHAR(36) NOT NULL UNIQUE,
    ride_uuid VARCHAR(36) NOT NULL,
    booking_uuid VARCHAR(36) NULL DEFAULT NULL,
    reviewer_uuid VARCHAR(36) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    reviewer_role ENUM('RIDER', 'DRIVER') NOT NULL,
    target_role ENUM('RIDER', 'DRIVER') NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    review_text VARCHAR(1000) NULL DEFAULT NULL,
    moderation_status ENUM('VISIBLE', 'UNDER_REVIEW', 'HIDDEN', 'REMOVED') NOT NULL DEFAULT 'VISIBLE',
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    editable_until TIMESTAMP NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ride_reviewer_direction UNIQUE (ride_uuid, reviewer_uuid, reviewer_role),
    INDEX idx_feedback_uuid (feedback_uuid),
    INDEX idx_feedback_ride (ride_uuid),
    INDEX idx_feedback_booking (booking_uuid),
    INDEX idx_feedback_reviewer (reviewer_uuid),
    INDEX idx_feedback_target_status (target_uuid, moderation_status),
    INDEX idx_feedback_status (moderation_status),
    INDEX idx_feedback_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table: feedback_tags (Standardized Review Feedback Tags Catalog)
CREATE TABLE IF NOT EXISTS feedback_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_code VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    target_role ENUM('DRIVER', 'RIDER', 'BOTH') NOT NULL DEFAULT 'BOTH',
    category ENUM('POSITIVE', 'CONCERN', 'GENERAL') NOT NULL DEFAULT 'GENERAL',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tag_code (tag_code),
    INDEX idx_tag_target_role (target_role, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Table: feedback_tag_assignments (Junction Table for Feedback & Selected Tags)
CREATE TABLE IF NOT EXISTS feedback_tag_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feedback_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feedback_tag UNIQUE (feedback_id, tag_id),
    CONSTRAINT fk_fta_feedback FOREIGN KEY (feedback_id) REFERENCES ride_feedback (id) ON DELETE CASCADE,
    CONSTRAINT fk_fta_tag FOREIGN KEY (tag_id) REFERENCES feedback_tags (id) ON DELETE RESTRICT,
    INDEX idx_fta_feedback (feedback_id),
    INDEX idx_fta_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Table: feedback_reports (User Content Abuse & Inappropriate Conduct Reports)
CREATE TABLE IF NOT EXISTS feedback_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_uuid VARCHAR(36) NOT NULL UNIQUE,
    feedback_id BIGINT NOT NULL,
    reporter_uuid VARCHAR(36) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    comments VARCHAR(500) NULL DEFAULT NULL,
    status ENUM('PENDING', 'INVESTIGATING', 'RESOLVED', 'DISMISSED') NOT NULL DEFAULT 'PENDING',
    resolution_notes VARCHAR(500) NULL DEFAULT NULL,
    resolved_by VARCHAR(36) NULL DEFAULT NULL,
    resolved_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_report_feedback_reporter UNIQUE (feedback_id, reporter_uuid),
    CONSTRAINT fk_report_feedback FOREIGN KEY (feedback_id) REFERENCES ride_feedback (id) ON DELETE CASCADE,
    INDEX idx_report_uuid (report_uuid),
    INDEX idx_report_feedback (feedback_id),
    INDEX idx_report_reporter (reporter_uuid),
    INDEX idx_report_status (status),
    INDEX idx_report_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Table: feedback_moderation_history (Immutable Administrative Moderation Audit Trail)
CREATE TABLE IF NOT EXISTS feedback_moderation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feedback_id BIGINT NOT NULL,
    moderator_uuid VARCHAR(36) NOT NULL,
    previous_status ENUM('VISIBLE', 'UNDER_REVIEW', 'HIDDEN', 'REMOVED') NOT NULL,
    new_status ENUM('VISIBLE', 'UNDER_REVIEW', 'HIDDEN', 'REMOVED') NOT NULL,
    reason VARCHAR(500) NOT NULL,
    moderated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mod_hist_feedback FOREIGN KEY (feedback_id) REFERENCES ride_feedback (id) ON DELETE CASCADE,
    INDEX idx_mod_feedback (feedback_id),
    INDEX idx_mod_moderator (moderator_uuid),
    INDEX idx_mod_time (moderated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Table: driver_rating_summaries (Materialized Fast Aggregate Driver Ratings)
CREATE TABLE IF NOT EXISTS driver_rating_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_uuid VARCHAR(36) NOT NULL UNIQUE,
    total_ratings INT NOT NULL DEFAULT 0,
    average_rating DECIMAL(3, 2) NOT NULL DEFAULT 5.00,
    five_star_count INT NOT NULL DEFAULT 0,
    four_star_count INT NOT NULL DEFAULT 0,
    three_star_count INT NOT NULL DEFAULT 0,
    two_star_count INT NOT NULL DEFAULT 0,
    one_star_count INT NOT NULL DEFAULT 0,
    excluded_ratings_count INT NOT NULL DEFAULT 0,
    total_completed_rides INT NOT NULL DEFAULT 0,
    last_recalculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_driver_summary_uuid (driver_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Table: rider_rating_summaries (Materialized Fast Aggregate Rider Ratings)
CREATE TABLE IF NOT EXISTS rider_rating_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rider_uuid VARCHAR(36) NOT NULL UNIQUE,
    total_ratings INT NOT NULL DEFAULT 0,
    average_rating DECIMAL(3, 2) NOT NULL DEFAULT 5.00,
    five_star_count INT NOT NULL DEFAULT 0,
    four_star_count INT NOT NULL DEFAULT 0,
    three_star_count INT NOT NULL DEFAULT 0,
    two_star_count INT NOT NULL DEFAULT 0,
    one_star_count INT NOT NULL DEFAULT 0,
    excluded_ratings_count INT NOT NULL DEFAULT 0,
    total_completed_rides INT NOT NULL DEFAULT 0,
    last_recalculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rider_summary_uuid (rider_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Seed Initial Feedback Tags Catalog (Rider & Driver Tags)
INSERT IGNORE INTO feedback_tags (tag_code, label, target_role, category, display_order) VALUES
-- Driver evaluation tags (submitted by riders)
('FRIENDLY_DRIVER', 'Friendly driver', 'DRIVER', 'POSITIVE', 1),
('PROFESSIONAL', 'Professional', 'DRIVER', 'POSITIVE', 2),
('SAFE_DRIVING', 'Safe driving', 'DRIVER', 'POSITIVE', 3),
('CLEAN_VEHICLE', 'Clean vehicle', 'DRIVER', 'POSITIVE', 4),
('SMOOTH_RIDE', 'Smooth ride', 'DRIVER', 'POSITIVE', 5),
('GOOD_COMMUNICATION', 'Good communication', 'BOTH', 'POSITIVE', 6),
('PUNCTUAL', 'Punctual', 'BOTH', 'POSITIVE', 7),
('ROUTE_CONCERN', 'Route concern', 'DRIVER', 'CONCERN', 8),
('DRIVING_CONCERN', 'Driving concern', 'DRIVER', 'CONCERN', 9),
('VEHICLE_CLEANLINESS_CONCERN', 'Vehicle cleanliness concern', 'DRIVER', 'CONCERN', 10),
('UNPROFESSIONAL_BEHAVIOR', 'Unprofessional behavior', 'BOTH', 'CONCERN', 11),
('OTHER', 'Other', 'BOTH', 'GENERAL', 12),

-- Rider evaluation tags (submitted by drivers)
('RESPECTFUL', 'Respectful', 'RIDER', 'POSITIVE', 13),
('PUNCTUAL_PICKUP', 'Punctual at pickup', 'RIDER', 'POSITIVE', 14),
('CLEAR_PICKUP_INSTRUCTIONS', 'Clear pickup instructions', 'RIDER', 'POSITIVE', 15),
('COMMUNICATION_ISSUE', 'Communication issue', 'RIDER', 'CONCERN', 16),
('PICKUP_DELAY', 'Pickup delay', 'RIDER', 'CONCERN', 17),
('INAPPROPRIATE_BEHAVIOR', 'Inappropriate behavior', 'RIDER', 'CONCERN', 18),
('SAFETY_CONCERN', 'Safety concern', 'BOTH', 'CONCERN', 19);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Verification & Execution Instructions:
-- 1. Prerequisites: Ensure migrations V1 through V10 have been applied to lexor_db.
-- 2. Execute migration manually:
--    mysql -u root -p lexor_db < database/mysql/migrations/V11__feedback_service_additive.sql
-- 3. Verify tables created:
--    SHOW TABLES LIKE '%feedback%';
--    SHOW TABLES LIKE '%rating_summaries%';
-- 4. Rollback (if necessary):
--    DROP TABLE IF EXISTS feedback_moderation_history;
--    DROP TABLE IF EXISTS feedback_reports;
--    DROP TABLE IF EXISTS feedback_tag_assignments;
--    DROP TABLE IF EXISTS feedback_tags;
--    DROP TABLE IF EXISTS ride_feedback;
--    DROP TABLE IF EXISTS driver_rating_summaries;
--    DROP TABLE IF EXISTS rider_rating_summaries;
-- =============================================================================
