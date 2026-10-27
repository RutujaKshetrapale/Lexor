-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Phase 17: External Provider Integrations Additive Migration (V13)
-- Adds provider tracking, OTP verification attempt audits, and performance
-- indexes for real SMS, Push, and Payment Provider reconciliation.
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Extend bookings table: OTP attempt auditing
-- Adds otp_attempts counter to enforce maximum 3 verification attempts
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS otp_attempts INT NOT NULL DEFAULT 0 AFTER otp_verified_at,
    ADD INDEX IF NOT EXISTS idx_bookings_otp_verif (otp_verified_at, otp_expires_at);

-- 2. Extend notification_deliveries table: external provider correlation
ALTER TABLE notification_deliveries
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(100) NULL DEFAULT NULL AFTER provider_message_id,
    ADD INDEX IF NOT EXISTS idx_deliv_prov_status (provider, status);

-- 3. Extend payment_webhook_events table: provider event indexing
ALTER TABLE payment_webhook_events
    ADD INDEX IF NOT EXISTS idx_webhook_prov_received (provider, status, received_at);

-- 4. Extend notification_devices table: active user index
ALTER TABLE notification_devices
    ADD INDEX IF NOT EXISTS idx_dev_user_platform (user_uuid, platform, is_active);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Verification Instructions:
-- 1. Prerequisites: Ensure migrations V1 through V12 have been applied to lexor_db.
-- 2. Execute migration manually:
--    mysql -u root -p lexor_db < database/mysql/migrations/V13__phase17_external_integrations_additive.sql
-- 3. Verify columns:
--    DESCRIBE lexor_db.bookings;
--    DESCRIBE lexor_db.notification_deliveries;
-- =============================================================================
