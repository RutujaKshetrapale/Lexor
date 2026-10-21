-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Notification Service Additive Migration (V10)
-- Creates dedicated notification tables for multi-channel notification dispatch
-- (In-App, Push/FCM, SMS, Email), user device registrations, user notification
-- preferences, durable event inbox for idempotent deduplication, delivery audit
-- logs, and multilingual template catalog (English, Hindi, Marathi).
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Table: notifications (Persistent In-App User Notifications)
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_uuid VARCHAR(36) NOT NULL UNIQUE,
    recipient_uuid VARCHAR(36) NOT NULL,
    recipient_role ENUM('RIDER', 'DRIVER', 'ADMIN') NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    category ENUM('RIDE', 'PAYMENT', 'SECURITY', 'SYSTEM', 'PROMOTION') NOT NULL DEFAULT 'RIDE',
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    deep_link VARCHAR(500) NULL DEFAULT NULL,
    related_entity_type VARCHAR(50) NULL DEFAULT NULL,
    related_entity_uuid VARCHAR(36) NULL DEFAULT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL DEFAULT NULL,
    status ENUM(
        'CREATED',
        'QUEUED',
        'PROCESSING',
        'COMPLETED',
        'PARTIALLY_FAILED',
        'FAILED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'CREATED',
    source_event_id VARCHAR(100) NULL DEFAULT NULL,
    idempotency_key VARCHAR(100) NULL DEFAULT NULL,
    expires_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notif_uuid (notification_uuid),
    INDEX idx_notif_recipient (recipient_uuid, is_read, created_at),
    INDEX idx_notif_category (category),
    INDEX idx_notif_type (notification_type),
    INDEX idx_notif_status (status),
    INDEX idx_notif_source_event (source_event_id),
    INDEX idx_notif_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table: notification_deliveries (Per-Channel Provider Dispatch Logs)
CREATE TABLE IF NOT EXISTS notification_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_uuid VARCHAR(36) NOT NULL UNIQUE,
    notification_uuid VARCHAR(36) NOT NULL,
    channel ENUM('PUSH', 'SMS', 'EMAIL', 'IN_APP') NOT NULL,
    provider VARCHAR(50) NOT NULL,
    recipient_destination VARCHAR(255) NOT NULL,
    attempt_number INT NOT NULL DEFAULT 1,
    provider_message_id VARCHAR(100) NULL DEFAULT NULL,
    status ENUM(
        'PENDING',
        'SUBMITTED',
        'ACCEPTED',
        'DELIVERED',
        'FAILED',
        'RETRY_SCHEDULED',
        'PERMANENTLY_FAILED'
    ) NOT NULL DEFAULT 'PENDING',
    error_code VARCHAR(50) NULL DEFAULT NULL,
    error_message VARCHAR(500) NULL DEFAULT NULL,
    next_retry_at TIMESTAMP NULL DEFAULT NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_deliv_uuid (delivery_uuid),
    INDEX idx_deliv_notif (notification_uuid),
    INDEX idx_deliv_channel (channel),
    INDEX idx_deliv_status (status),
    INDEX idx_deliv_provider_msg (provider_message_id),
    INDEX idx_deliv_attempted (attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Table: notification_devices (Rider & Driver FCM/APNs Device Tokens)
CREATE TABLE IF NOT EXISTS notification_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_uuid VARCHAR(36) NOT NULL,
    platform ENUM('ANDROID', 'IOS', 'WEB') NOT NULL,
    fcm_token VARCHAR(500) NOT NULL,
    device_model VARCHAR(100) NULL DEFAULT NULL,
    os_version VARCHAR(50) NULL DEFAULT NULL,
    app_version VARCHAR(50) NULL DEFAULT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dev_uuid (device_uuid),
    INDEX idx_dev_user (user_uuid, is_active),
    INDEX idx_dev_token (fcm_token(191)),
    INDEX idx_dev_platform (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Table: notification_preferences (User-Scoped Notification Controls)
CREATE TABLE IF NOT EXISTS notification_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preference_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_uuid VARCHAR(36) NOT NULL UNIQUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ride_updates_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    payment_updates_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    promotional_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    security_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE, -- Mandatory, cannot be disabled
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start TIME NULL DEFAULT '22:00:00',
    quiet_hours_end TIME NULL DEFAULT '07:00:00',
    preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pref_uuid (preference_uuid),
    INDEX idx_pref_user (user_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Table: notification_event_inbox (Durable Business Event Ingestion & Deduplication)
CREATE TABLE IF NOT EXISTS notification_event_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_uuid VARCHAR(36) NOT NULL UNIQUE,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    source_service VARCHAR(50) NOT NULL,
    payload JSON NOT NULL,
    status ENUM('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED', 'DEAD_LETTER') NOT NULL DEFAULT 'RECEIVED',
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL DEFAULT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_inbox_event_id (event_id),
    INDEX idx_inbox_type (event_type),
    INDEX idx_inbox_status (status),
    INDEX idx_inbox_received (received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Table: notification_templates (Multilingual Notification Templates)
CREATE TABLE IF NOT EXISTS notification_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL,
    channel ENUM('PUSH', 'SMS', 'EMAIL', 'IN_APP') NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    title_template VARCHAR(255) NOT NULL,
    body_template TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_template_channel_locale (template_code, channel, locale),
    INDEX idx_tpl_code (template_code),
    INDEX idx_tpl_locale (locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Seed Initial Core Templates (English, Hindi, Marathi)
INSERT IGNORE INTO notification_templates (template_code, channel, locale, title_template, body_template) VALUES
-- Driver Assigned (English)
('DRIVER_ASSIGNED', 'PUSH', 'en', 'Driver Assigned!', 'Your driver {driverName} ({vehicleModel} - {plateNumber}) is on the way.'),
('DRIVER_ASSIGNED', 'IN_APP', 'en', 'Driver Assigned', 'Your driver {driverName} has accepted your ride request.'),
('DRIVER_ASSIGNED', 'SMS', 'en', 'LEXOR: Driver {driverName} ({plateNumber}) assigned. OTP for ride: {otp}. Track in app.'),
-- Driver Assigned (Hindi)
('DRIVER_ASSIGNED', 'PUSH', 'hi', 'चालक नियुक्त किया गया!', 'आपके चालक {driverName} ({vehicleModel} - {plateNumber}) रास्ते में हैं।'),
('DRIVER_ASSIGNED', 'IN_APP', 'hi', 'चालक नियुक्त किया गया', 'आपके चालक {driverName} ने आपकी सवारी स्वीकार कर ली है।'),
-- Driver Assigned (Marathi)
('DRIVER_ASSIGNED', 'PUSH', 'mr', 'चालक नियुक्त केला!', 'तुमचे चालक {driverName} ({vehicleModel} - {plateNumber}) मार्गावर आहेत.'),
('DRIVER_ASSIGNED', 'IN_APP', 'mr', 'चालक नियुक्त केला', 'तुमचे चालक {driverName} यांनी तुमची राइड स्वीकारली आहे.'),

-- Driver Arrived
('DRIVER_ARRIVED', 'PUSH', 'en', 'Driver Arrived', 'Your driver {driverName} has arrived at the pickup location.'),
('DRIVER_ARRIVED', 'IN_APP', 'en', 'Driver Arrived', '{driverName} is waiting at your pickup point.'),
('DRIVER_ARRIVED', 'SMS', 'en', 'LEXOR: Driver {driverName} has arrived at pickup. Please board within 5 mins.'),

-- Ride Started
('RIDE_STARTED', 'PUSH', 'en', 'Ride Started', 'You are on your way to {destination}. Enjoy your ride with LEXOR!'),
('RIDE_STARTED', 'IN_APP', 'en', 'Trip in Progress', 'Your ride to {destination} is underway.'),

-- Ride Completed
('RIDE_COMPLETED', 'PUSH', 'en', 'Ride Completed', 'You have arrived! Final fare: INR {fare}. Thank you for riding with LEXOR.'),
('RIDE_COMPLETED', 'IN_APP', 'en', 'Trip Completed', 'Your ride has concluded. Fare: INR {fare}.'),
('RIDE_COMPLETED', 'EMAIL', 'en', 'Your LEXOR Ride Receipt', 'Dear Rider, thank you for riding with LEXOR. Total fare: INR {fare}. Booking UUID: {bookingUuid}.'),

-- Payment Succeeded
('PAYMENT_SUCCEEDED', 'PUSH', 'en', 'Payment Successful', 'Payment of INR {amount} for ride {bookingUuid} succeeded.'),
('PAYMENT_SUCCEEDED', 'IN_APP', 'en', 'Payment Confirmed', 'INR {amount} collected successfully via {method}.'),
('PAYMENT_SUCCEEDED', 'EMAIL', 'en', 'Payment Confirmation - LEXOR', 'Payment of INR {amount} has been successfully settled for booking {bookingUuid}.'),

-- Payment Failed
('PAYMENT_FAILED', 'PUSH', 'en', 'Payment Failed', 'Payment of INR {amount} failed. Please choose an alternate payment method.'),
('PAYMENT_FAILED', 'IN_APP', 'en', 'Payment Action Required', 'Payment failed for booking {bookingUuid}. Retry via card or cash.'),

-- Refund Completed
('REFUND_COMPLETED', 'PUSH', 'en', 'Refund Processed', 'Refund of INR {amount} has been initiated to your source account.'),
('REFUND_COMPLETED', 'IN_APP', 'en', 'Refund Completed', 'INR {amount} refunded for ride {bookingUuid}. Reference: {refundId}.'),

-- Security Alert
('SECURITY_ALERT', 'SMS', 'en', 'LEXOR Security: New login detected for your account at {timestamp}. If not you, secure account immediately.'),
('SECURITY_ALERT', 'EMAIL', 'en', 'LEXOR Security Alert', 'A critical security action occurred on your account: {action}. Time: {timestamp}.'),
('SECURITY_ALERT', 'IN_APP', 'en', 'Security Alert', '{action} occurred on your account at {timestamp}.'),

-- Driver Ride Offer
('DRIVER_OFFER', 'PUSH', 'en', 'New Ride Offer!', 'Pickup: {pickupLocation} ({distance} km away). Fare: INR {estimatedFare}. Tap to accept.'),
('DRIVER_OFFER', 'IN_APP', 'en', 'New Ride Offer', 'Ride request available from {pickupLocation}.');

SET FOREIGN_KEY_CHECKS = 1;
