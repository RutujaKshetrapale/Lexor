-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Migration: V1__init_schema.sql
-- Description: Core Schema Initialization (24 Tables)
-- Database Engine: InnoDB, Charset: utf8mb4
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. IDENTITY & PROFILES MODULE
-- -----------------------------------------------------------------------------

-- Table 1: users (Central Identity Table)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('RIDER', 'DRIVER', 'ADMIN') NOT NULL,
    status ENUM('PENDING', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED') NOT NULL DEFAULT 'ACTIVE',
    profile_picture_url VARCHAR(500) DEFAULT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_phone (phone_number),
    INDEX idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 2: rider_profiles
CREATE TABLE IF NOT EXISTS rider_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    rating DECIMAL(3, 2) NOT NULL DEFAULT 5.00,
    total_rides INT UNSIGNED NOT NULL DEFAULT 0,
    preferred_payment_method ENUM('CARD', 'CASH', 'WALLET', 'UPI') DEFAULT 'UPI',
    home_address VARCHAR(255) DEFAULT NULL,
    work_address VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rider_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_rider_rating (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 3: driver_profiles
CREATE TABLE IF NOT EXISTS driver_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    license_number VARCHAR(50) NOT NULL UNIQUE,
    license_expiry_date DATE NOT NULL,
    status ENUM('OFFLINE', 'ONLINE', 'IN_TRIP', 'SUSPENDED') NOT NULL DEFAULT 'OFFLINE',
    rating DECIMAL(3, 2) NOT NULL DEFAULT 5.00,
    total_trips INT UNSIGNED NOT NULL DEFAULT 0,
    acceptance_rate DECIMAL(5, 2) NOT NULL DEFAULT 100.00,
    cancellation_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP NULL DEFAULT NULL,
    background_check_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_driver_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_driver_status (status),
    INDEX idx_driver_rating (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 4: admin_profiles
CREATE TABLE IF NOT EXISTS admin_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    department VARCHAR(50) NOT NULL,
    access_level ENUM('SUPER_ADMIN', 'OPERATIONS', 'SUPPORT', 'FINANCE') NOT NULL DEFAULT 'OPERATIONS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. VEHICLE & RIDE CATEGORY MODULE
-- -----------------------------------------------------------------------------

-- Table 5: vehicles
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    make VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    year INT NOT NULL,
    color VARCHAR(30) NOT NULL,
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    vin VARCHAR(17) DEFAULT NULL UNIQUE,
    ride_type_category VARCHAR(30) NOT NULL,
    seat_capacity TINYINT UNSIGNED NOT NULL DEFAULT 4,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_inspected BOOLEAN NOT NULL DEFAULT TRUE,
    inspection_expires_at DATE DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicles_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id) ON DELETE CASCADE,
    INDEX idx_vehicles_driver (driver_id),
    INDEX idx_vehicles_license_plate (license_plate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 6: ride_types
CREATE TABLE IF NOT EXISTS ride_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(30) NOT NULL UNIQUE,
    description TEXT DEFAULT NULL,
    base_fare DECIMAL(10, 2) NOT NULL,
    per_km_rate DECIMAL(10, 2) NOT NULL,
    per_minute_rate DECIMAL(10, 2) NOT NULL,
    minimum_fare DECIMAL(10, 2) NOT NULL,
    cancellation_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    capacity TINYINT UNSIGNED NOT NULL DEFAULT 4,
    icon_url VARCHAR(500) DEFAULT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. PROMOTIONS & DISCOUNTS MODULE
-- -----------------------------------------------------------------------------

-- Table 7: coupons
CREATE TABLE IF NOT EXISTS coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(255) DEFAULT NULL,
    discount_type ENUM('PERCENTAGE', 'FIXED_AMOUNT') NOT NULL,
    discount_value DECIMAL(10, 2) NOT NULL,
    min_ride_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    max_discount_amount DECIMAL(10, 2) DEFAULT NULL,
    usage_limit INT UNSIGNED DEFAULT NULL,
    usage_count INT UNSIGNED NOT NULL DEFAULT 0,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_coupons_code (code),
    INDEX idx_coupons_validity (valid_from, valid_until, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 4. RIDE LIFECYCLE & BOOKINGS MODULE
-- -----------------------------------------------------------------------------

-- Table 8: ride_requests
CREATE TABLE IF NOT EXISTS ride_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    rider_id BIGINT NOT NULL,
    ride_type_id BIGINT NOT NULL,
    pickup_address VARCHAR(255) NOT NULL,
    pickup_latitude DECIMAL(10, 8) NOT NULL,
    pickup_longitude DECIMAL(11, 8) NOT NULL,
    drop_address VARCHAR(255) NOT NULL,
    drop_latitude DECIMAL(10, 8) NOT NULL,
    drop_longitude DECIMAL(11, 8) NOT NULL,
    estimated_distance_km DECIMAL(8, 2) NOT NULL,
    estimated_duration_minutes INT NOT NULL,
    estimated_fare DECIMAL(10, 2) NOT NULL,
    coupon_id BIGINT DEFAULT NULL,
    status ENUM('REQUESTED', 'SEARCHING', 'MATCHED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ride_requests_rider FOREIGN KEY (rider_id) REFERENCES rider_profiles (id),
    CONSTRAINT fk_ride_requests_ride_type FOREIGN KEY (ride_type_id) REFERENCES ride_types (id),
    CONSTRAINT fk_ride_requests_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE SET NULL,
    INDEX idx_ride_req_rider (rider_id),
    INDEX idx_ride_req_status (status),
    INDEX idx_ride_req_created (requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 9: bookings
CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    ride_request_id BIGINT NOT NULL UNIQUE,
    rider_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    ride_type_id BIGINT NOT NULL,
    booking_status ENUM('CONFIRMED', 'DRIVER_ASSIGNED', 'CANCELLED_BY_RIDER', 'CANCELLED_BY_DRIVER', 'EXPIRED') NOT NULL DEFAULT 'CONFIRMED',
    otp_hash VARCHAR(255) NOT NULL,
    otp_expires_at TIMESTAMP NULL DEFAULT NULL,
    otp_verified_at TIMESTAMP NULL DEFAULT NULL,
    scheduled_time TIMESTAMP NULL DEFAULT NULL,
    cancellation_reason VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_ride_request FOREIGN KEY (ride_request_id) REFERENCES ride_requests (id),
    CONSTRAINT fk_bookings_rider FOREIGN KEY (rider_id) REFERENCES rider_profiles (id),
    CONSTRAINT fk_bookings_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id),
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_bookings_ride_type FOREIGN KEY (ride_type_id) REFERENCES ride_types (id),
    INDEX idx_bookings_rider (rider_id),
    INDEX idx_bookings_driver (driver_id),
    INDEX idx_bookings_status (booking_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 10: rides
CREATE TABLE IF NOT EXISTS rides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    booking_id BIGINT NOT NULL UNIQUE,
    rider_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    ride_type_id BIGINT NOT NULL,
    pickup_address VARCHAR(255) NOT NULL,
    pickup_latitude DECIMAL(10, 8) NOT NULL,
    pickup_longitude DECIMAL(11, 8) NOT NULL,
    drop_address VARCHAR(255) NOT NULL,
    drop_latitude DECIMAL(10, 8) NOT NULL,
    drop_longitude DECIMAL(11, 8) NOT NULL,
    status ENUM('REQUESTED', 'SEARCHING_DRIVER', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'TRIP_STARTED', 'TRIP_COMPLETED', 'PAYMENT_COMPLETED', 'FEEDBACK', 'CANCELLED') NOT NULL DEFAULT 'REQUESTED',
    actual_pickup_time TIMESTAMP NULL DEFAULT NULL,
    actual_drop_time TIMESTAMP NULL DEFAULT NULL,
    actual_distance_km DECIMAL(8, 2) DEFAULT NULL,
    actual_duration_minutes INT DEFAULT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL DEFAULT NULL,
    completed_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rides_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_rides_rider FOREIGN KEY (rider_id) REFERENCES rider_profiles (id),
    CONSTRAINT fk_rides_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id),
    CONSTRAINT fk_rides_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_rides_ride_type FOREIGN KEY (ride_type_id) REFERENCES ride_types (id),
    INDEX idx_rides_status (status),
    INDEX idx_rides_rider (rider_id),
    INDEX idx_rides_driver (driver_id),
    INDEX idx_rides_dates (requested_at, completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 11: ride_status_history
CREATE TABLE IF NOT EXISTS ride_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    status ENUM('REQUESTED', 'SEARCHING_DRIVER', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'TRIP_STARTED', 'TRIP_COMPLETED', 'PAYMENT_COMPLETED', 'FEEDBACK', 'CANCELLED') NOT NULL,
    changed_by_user_id BIGINT DEFAULT NULL,
    notes VARCHAR(255) DEFAULT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_status_history_ride FOREIGN KEY (ride_id) REFERENCES rides (id) ON DELETE CASCADE,
    CONSTRAINT fk_status_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_status_hist_ride (ride_id),
    INDEX idx_status_hist_time (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 12: ride_stops
CREATE TABLE IF NOT EXISTS ride_stops (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    stop_order INT NOT NULL,
    stop_address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    arrived_at TIMESTAMP NULL DEFAULT NULL,
    departed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ride_stops_ride FOREIGN KEY (ride_id) REFERENCES rides (id) ON DELETE CASCADE,
    INDEX idx_ride_stops_ride (ride_id, stop_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 13: driver_locations
CREATE TABLE IF NOT EXISTS driver_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    ride_id BIGINT DEFAULT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    heading DECIMAL(5, 2) DEFAULT NULL,
    speed_kmh DECIMAL(5, 2) DEFAULT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_driver_loc_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_driver_loc_ride FOREIGN KEY (ride_id) REFERENCES rides (id) ON DELETE SET NULL,
    INDEX idx_driver_loc_driver_time (driver_id, recorded_at),
    INDEX idx_driver_loc_ride (ride_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 5. FINANCIAL, PAYMENTS & REFUNDS MODULE
-- -----------------------------------------------------------------------------

-- Table 14: fare_breakdowns
CREATE TABLE IF NOT EXISTS fare_breakdowns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL UNIQUE,
    base_fare DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    distance_fare DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    time_fare DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    surge_multiplier DECIMAL(4, 2) NOT NULL DEFAULT 1.00,
    surge_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    cancellation_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_fare DECIMAL(10, 2) NOT NULL,
    driver_earning DECIMAL(10, 2) NOT NULL,
    platform_commission DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_fare_breakdowns_ride FOREIGN KEY (ride_id) REFERENCES rides (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 15: payments
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    ride_id BIGINT NOT NULL UNIQUE,
    rider_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_method ENUM('CARD', 'CASH', 'WALLET', 'UPI') NOT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_ride FOREIGN KEY (ride_id) REFERENCES rides (id),
    CONSTRAINT fk_payments_rider FOREIGN KEY (rider_id) REFERENCES rider_profiles (id),
    INDEX idx_payments_rider (rider_id),
    INDEX idx_payments_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 16: payment_transactions
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_reference VARCHAR(100) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    gateway_provider VARCHAR(50) NOT NULL,
    gateway_transaction_id VARCHAR(100) DEFAULT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status ENUM('INITIATED', 'SUCCESS', 'FAILED') NOT NULL,
    error_message TEXT DEFAULT NULL,
    response_payload JSON DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pay_trans_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    INDEX idx_pay_trans_ref (transaction_reference),
    INDEX idx_pay_trans_payment (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 17: refunds
CREATE TABLE IF NOT EXISTS refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status ENUM('REQUESTED', 'APPROVED', 'PROCESSED', 'REJECTED') NOT NULL DEFAULT 'REQUESTED',
    processed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 6. FEEDBACK, RATINGS & AI ANALYTICS MODULE
-- -----------------------------------------------------------------------------

-- Table 18: favorite_locations
CREATE TABLE IF NOT EXISTS favorite_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rider_id BIGINT NOT NULL,
    label VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fav_loc_rider FOREIGN KEY (rider_id) REFERENCES rider_profiles (id) ON DELETE CASCADE,
    INDEX idx_fav_loc_rider (rider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 19: feedback
CREATE TABLE IF NOT EXISTS feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL UNIQUE,
    rider_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    driver_rating TINYINT UNSIGNED DEFAULT NULL,
    rider_rating TINYINT UNSIGNED DEFAULT NULL,
    cleanliness_rating TINYINT UNSIGNED DEFAULT NULL,
    safety_rating TINYINT UNSIGNED DEFAULT NULL,
    comments TEXT DEFAULT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_ride FOREIGN KEY (ride_id) REFERENCES rides (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_rider FOREIGN KEY (rider_id) REFERENCES rider_profiles (id),
    CONSTRAINT fk_feedback_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id),
    INDEX idx_feedback_ride (ride_id),
    INDEX idx_feedback_driver (driver_id),
    INDEX idx_feedback_rider (rider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 20: ai_feedback_analysis
CREATE TABLE IF NOT EXISTS ai_feedback_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feedback_id BIGINT NOT NULL UNIQUE,
    sentiment ENUM('POSITIVE', 'NEUTRAL', 'NEGATIVE') NOT NULL,
    sentiment_score DECIMAL(5, 4) NOT NULL,
    topic VARCHAR(100) DEFAULT NULL,
    severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'LOW',
    summary TEXT DEFAULT NULL,
    action_recommended VARCHAR(255) DEFAULT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_analysis_feedback FOREIGN KEY (feedback_id) REFERENCES feedback (id) ON DELETE CASCADE,
    INDEX idx_ai_sentiment (sentiment),
    INDEX idx_ai_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 7. NOTIFICATIONS, SAFETY & AUDIT LOGS MODULE
-- -----------------------------------------------------------------------------

-- Table 21: notifications
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    type ENUM('RIDE_UPDATE', 'PAYMENT', 'PROMOTION', 'SAFETY', 'SYSTEM') NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_notifications_user (user_id),
    INDEX idx_notifications_unread (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 22: emergency_contacts
CREATE TABLE IF NOT EXISTS emergency_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contact_name VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    relationship VARCHAR(50) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_emerg_contacts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_emerg_contacts_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 23: sos_events
CREATE TABLE IF NOT EXISTS sos_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    triggered_by_user_id BIGINT NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    status ENUM('ACTIVE', 'INVESTIGATING', 'RESOLVED', 'FALSE_ALARM') NOT NULL DEFAULT 'ACTIVE',
    notes TEXT DEFAULT NULL,
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_sos_ride FOREIGN KEY (ride_id) REFERENCES rides (id),
    CONSTRAINT fk_sos_user FOREIGN KEY (triggered_by_user_id) REFERENCES users (id),
    INDEX idx_sos_ride (ride_id),
    INDEX idx_sos_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table 24: admin_actions
CREATE TABLE IF NOT EXISTS admin_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    details JSON DEFAULT NULL,
    ip_address VARCHAR(45) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_actions_user FOREIGN KEY (admin_user_id) REFERENCES users (id),
    INDEX idx_admin_actions_user (admin_user_id),
    INDEX idx_admin_actions_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
