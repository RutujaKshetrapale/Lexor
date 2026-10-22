-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Migration: V12__admin_operations_service_additive.sql
-- Description: Phase 14 Enterprise Admin & Operations Dashboard Schema
-- Tables Added: admin_audit_logs, admin_dashboard_settings
-- Database Engine: InnoDB, Charset: utf8mb4
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. Table: admin_audit_logs
-- -----------------------------------------------------------------------------
-- Records all privileged administrative actions, state overrides, cancellations,
-- document approvals, refunds, and moderation decisions across the platform.
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    audit_uuid VARCHAR(36) NOT NULL UNIQUE,
    admin_user_uuid VARCHAR(36) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_uuid VARCHAR(36) DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    details JSON DEFAULT NULL,
    ip_address VARCHAR(45) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_admin_uuid (admin_user_uuid),
    INDEX idx_audit_action (action),
    INDEX idx_audit_resource (resource_type, resource_uuid),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Table: admin_dashboard_settings
-- -----------------------------------------------------------------------------
-- Dynamic platform operational parameters and dashboard configuration.
CREATE TABLE IF NOT EXISTS admin_dashboard_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(64) NOT NULL UNIQUE,
    setting_value VARCHAR(255) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    updated_by_uuid VARCHAR(36) DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_setting_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Seed Default Dashboard Operational Settings
-- -----------------------------------------------------------------------------
INSERT INTO admin_dashboard_settings (setting_key, setting_value, description)
VALUES
    ('LIVE_OPS_POLL_INTERVAL_SECONDS', '10', 'Default polling interval for live operations dashboard console'),
    ('MATCHING_ALERT_THRESHOLD_SECONDS', '120', 'Time in seconds after which an unassigned ride request triggers a matching delay alert'),
    ('HIGH_CANCELLATION_THRESHOLD_PERCENT', '15', 'Percentage threshold of cancellations in a 1-hour window triggering operational warning'),
    ('PLATFORM_MAINTENANCE_MODE', 'false', 'Flag indicating whether public ride-hailing services are paused for maintenance'),
    ('MAX_EXPORT_ROWS_LIMIT', '10000', 'Maximum number of records permitted per CSV analytics export request')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
