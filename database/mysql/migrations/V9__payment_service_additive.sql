-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Payment Service Additive Migration (V9)
-- Creates dedicated payment tables for ride payment lifecycle, Razorpay provider
-- integration, transaction logging, refunds, idempotency, immutable financial
-- ledger, and driver payout reconciliation boundaries.
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Table: payments (Authoritative Payment Records)
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_uuid VARCHAR(36) NOT NULL UNIQUE,
    booking_uuid VARCHAR(36) NOT NULL,
    rider_uuid VARCHAR(36) NOT NULL,
    driver_uuid VARCHAR(36) NULL DEFAULT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    payment_method ENUM('UPI', 'CARD', 'NET_BANKING', 'CASH', 'WALLET') NOT NULL,
    status ENUM(
        'CREATED',
        'PENDING',
        'REQUIRES_ACTION',
        'AUTHORIZED',
        'CAPTURE_PENDING',
        'SUCCEEDED',
        'FAILED',
        'CANCELLED',
        'EXPIRED',
        'REFUND_PENDING',
        'PARTIALLY_REFUNDED',
        'REFUNDED',
        'RECONCILIATION_REQUIRED'
    ) NOT NULL DEFAULT 'CREATED',
    provider VARCHAR(50) NOT NULL DEFAULT 'RAZORPAY',
    provider_order_id VARCHAR(100) NULL DEFAULT NULL,
    provider_payment_id VARCHAR(100) NULL DEFAULT NULL,
    provider_signature VARCHAR(255) NULL DEFAULT NULL,
    failure_reason VARCHAR(500) NULL DEFAULT NULL,
    idempotency_key VARCHAR(100) NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    authorized_at TIMESTAMP NULL DEFAULT NULL,
    captured_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_payments_uuid (payment_uuid),
    INDEX idx_payments_booking (booking_uuid),
    INDEX idx_payments_rider (rider_uuid),
    INDEX idx_payments_driver (driver_uuid),
    INDEX idx_payments_status (status),
    INDEX idx_payments_method (payment_method),
    INDEX idx_payments_provider_order (provider_order_id),
    INDEX idx_payments_provider_pay (provider_payment_id),
    INDEX idx_payments_idempotency (idempotency_key),
    INDEX idx_payments_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table: payment_transactions (Auditable Payment Attempts & Gateway Payloads)
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_uuid VARCHAR(36) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    payment_uuid VARCHAR(36) NOT NULL,
    attempt_type ENUM('AUTHORIZATION', 'CAPTURE', 'SALE', 'CASH_COLLECTION', 'REFUND', 'VERIFICATION') NOT NULL,
    gateway_provider VARCHAR(50) NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_id VARCHAR(100) NULL DEFAULT NULL,
    gateway_payment_id VARCHAR(100) NULL DEFAULT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status ENUM('INITIATED', 'SUCCESS', 'FAILED', 'PENDING') NOT NULL,
    error_code VARCHAR(100) NULL DEFAULT NULL,
    error_message TEXT NULL DEFAULT NULL,
    raw_payload TEXT NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_tx_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    INDEX idx_trans_uuid (transaction_uuid),
    INDEX idx_trans_payment_id (payment_id),
    INDEX idx_trans_payment_uuid (payment_uuid),
    INDEX idx_trans_gateway_order (gateway_order_id),
    INDEX idx_trans_gateway_pay (gateway_payment_id),
    INDEX idx_trans_status (status),
    INDEX idx_trans_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Table: payment_refunds (Authoritative Refund Records)
CREATE TABLE IF NOT EXISTS payment_refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_uuid VARCHAR(36) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    payment_uuid VARCHAR(36) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    reason VARCHAR(255) NOT NULL,
    status ENUM('REQUESTED', 'PENDING', 'PROCESSED', 'FAILED', 'REJECTED') NOT NULL DEFAULT 'REQUESTED',
    provider_refund_id VARCHAR(100) NULL DEFAULT NULL,
    initiated_by VARCHAR(50) NOT NULL,
    admin_notes VARCHAR(500) NULL DEFAULT NULL,
    processed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_refunds_pay FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    INDEX idx_refund_uuid (refund_uuid),
    INDEX idx_refund_payment_id (payment_id),
    INDEX idx_refund_payment_uuid (payment_uuid),
    INDEX idx_refund_provider (provider_refund_id),
    INDEX idx_refund_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Table: payment_webhook_events (Idempotent Webhook Log and Deduplication)
CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_uuid VARCHAR(36) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    signature VARCHAR(255) NULL DEFAULT NULL,
    status ENUM('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED') NOT NULL DEFAULT 'RECEIVED',
    processing_error TEXT NULL DEFAULT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT uk_webhook_event UNIQUE (provider, provider_event_id),
    INDEX idx_webhook_status (status),
    INDEX idx_webhook_type (event_type),
    INDEX idx_webhook_received (received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Table: payment_idempotency_records (Anti-Double Charge Concurrency Protection)
CREATE TABLE IF NOT EXISTS payment_idempotency_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    caller_uuid VARCHAR(36) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    response_status_code INT NOT NULL,
    response_payload TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_idempotency UNIQUE (caller_uuid, idempotency_key),
    INDEX idx_idempotency_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Table: financial_ledger_entries (Immutable Double-Entry Accounting Ledger)
CREATE TABLE IF NOT EXISTS financial_ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entry_uuid VARCHAR(36) NOT NULL UNIQUE,
    payment_uuid VARCHAR(36) NOT NULL,
    booking_uuid VARCHAR(36) NOT NULL,
    driver_uuid VARCHAR(36) NULL DEFAULT NULL,
    entry_type ENUM(
        'FARE_COLLECTED',
        'CASH_COLLECTED_BY_DRIVER',
        'PLATFORM_COMMISSION',
        'DRIVER_EARNINGS',
        'DRIVER_PAYABLE',
        'DRIVER_CASH_LIABILITY',
        'REFUND_DEDUCTION',
        'ADJUSTMENT'
    ) NOT NULL,
    direction ENUM('DEBIT', 'CREDIT') NOT NULL,
    account ENUM(
        'RIDER_COLLECTION',
        'CASH_CLEARING',
        'PLATFORM_REVENUE',
        'DRIVER_PAYABLE',
        'GATEWAY_CLEARING',
        'REFUND_RESERVE'
    ) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    description VARCHAR(255) NOT NULL,
    reference_id VARCHAR(100) NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ledger_uuid (entry_uuid),
    INDEX idx_ledger_payment (payment_uuid),
    INDEX idx_ledger_booking (booking_uuid),
    INDEX idx_ledger_driver (driver_uuid),
    INDEX idx_ledger_entry_type (entry_type),
    INDEX idx_ledger_account (account),
    INDEX idx_ledger_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Table: driver_payout_records (Driver Payout Boundaries & Settlement Reconciliation)
CREATE TABLE IF NOT EXISTS driver_payout_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payout_uuid VARCHAR(36) NOT NULL UNIQUE,
    driver_uuid VARCHAR(36) NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    gross_earnings DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    platform_commission DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    cash_collected DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    net_payout_due DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status ENUM('CALCULATED', 'PENDING_APPROVAL', 'INITIATED', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'CALCULATED',
    provider_payout_reference VARCHAR(100) NULL DEFAULT NULL,
    notes VARCHAR(500) NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_payout_uuid (payout_uuid),
    INDEX idx_payout_driver (driver_uuid),
    INDEX idx_payout_status (status),
    INDEX idx_payout_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Verification Instructions:
-- 1. Prerequisites: Ensure V1 through V8 have been applied to lexor_db.
-- 2. Execute migration manually:
--    mysql -u root -p lexor_db < database/mysql/migrations/V9__payment_service_additive.sql
-- 3. Verify tables:
--    SHOW TABLES LIKE 'payment%';
--    SHOW TABLES LIKE 'financial_ledger_entries';
--    SHOW TABLES LIKE 'driver_payout_records';
-- 4. Rollback (if necessary):
--    DROP TABLE IF EXISTS driver_payout_records;
--    DROP TABLE IF EXISTS financial_ledger_entries;
--    DROP TABLE IF EXISTS payment_idempotency_records;
--    DROP TABLE IF EXISTS payment_webhook_events;
--    DROP TABLE IF EXISTS payment_refunds;
--    DROP TABLE IF EXISTS payment_transactions;
--    DROP TABLE IF EXISTS payments;
-- =============================================================================
