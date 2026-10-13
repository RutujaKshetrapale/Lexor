-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Vehicle Service Additive Migration (V4)
-- Extends vehicles table and creates vehicle_documents, vehicle_verification_history,
-- and driver_vehicle_assignments tables.
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Modify vehicles table to support UUID, fuel type, verification lifecycle,
-- audit fields, and admin reviewer reference
ALTER TABLE vehicles
    ADD COLUMN uuid VARCHAR(36) NULL AFTER id,
    ADD COLUMN fuel_type ENUM('PETROL', 'DIESEL', 'ELECTRIC', 'HYBRID', 'CNG', 'OTHER') NOT NULL DEFAULT 'PETROL' AFTER seat_capacity,
    ADD COLUMN verification_status ENUM('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'SUSPENDED') NOT NULL DEFAULT 'PENDING_VERIFICATION' AFTER is_inspected,
    ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER verification_status,
    ADD COLUMN reviewed_by_admin_id BIGINT NULL AFTER rejection_reason,
    ADD COLUMN verified_at TIMESTAMP NULL DEFAULT NULL AFTER reviewed_by_admin_id;

-- Populate UUID and verified status for existing seed vehicle (Toyota Urban Cruiser Hyryder)
UPDATE vehicles
SET uuid = 'e7b1a2c3-4d5e-6f7a-8b9c-0d1e2f3a4b5c',
    fuel_type = 'HYBRID',
    verification_status = 'VERIFIED',
    verified_at = '2026-01-02 10:45:00',
    reviewed_by_admin_id = 1
WHERE id = 1;

-- For any other existing vehicles that may exist without UUID, assign UUID
UPDATE vehicles
SET uuid = UUID()
WHERE uuid IS NULL;

-- Enforce NOT NULL and UNIQUE on vehicles.uuid
ALTER TABLE vehicles
    MODIFY COLUMN uuid VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uq_vehicles_uuid (uuid),
    ADD CONSTRAINT fk_vehicles_admin FOREIGN KEY (reviewed_by_admin_id) REFERENCES users (id) ON DELETE SET NULL,
    ADD INDEX idx_vehicles_status (verification_status),
    ADD INDEX idx_vehicles_fuel (fuel_type);

-- 2. Create Table: vehicle_documents (Document Metadata and Verification Workflow)
CREATE TABLE IF NOT EXISTS vehicle_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    document_type ENUM('REGISTRATION_CERTIFICATE', 'VEHICLE_INSURANCE', 'POLLUTION_UNDER_CONTROL', 'VEHICLE_PERMIT', 'FITNESS_CERTIFICATE', 'OTHER') NOT NULL,
    document_number VARCHAR(100) DEFAULT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    issue_date DATE DEFAULT NULL,
    expiry_date DATE DEFAULT NULL,
    issuing_authority VARCHAR(100) DEFAULT NULL,
    verification_status ENUM('PENDING_REVIEW', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'EXPIRED', 'RESUBMISSION_REQUIRED') NOT NULL DEFAULT 'PENDING_REVIEW',
    rejection_reason VARCHAR(500) DEFAULT NULL,
    review_notes VARCHAR(500) DEFAULT NULL,
    reviewed_by_admin_id BIGINT DEFAULT NULL,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_documents_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_documents_admin FOREIGN KEY (reviewed_by_admin_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_vehicle_docs_vehicle (vehicle_id),
    INDEX idx_vehicle_docs_status (verification_status),
    INDEX idx_vehicle_docs_type (document_type),
    INDEX idx_vehicle_docs_current (vehicle_id, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Create Table: vehicle_verification_history (Audit Trail for Vehicle Review Decisions)
CREATE TABLE IF NOT EXISTS vehicle_verification_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    document_id BIGINT DEFAULT NULL,
    action_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50) NOT NULL,
    new_status VARCHAR(50) NOT NULL,
    reviewed_by_admin_id BIGINT NOT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_verif_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_verif_doc FOREIGN KEY (document_id) REFERENCES vehicle_documents (id) ON DELETE SET NULL,
    CONSTRAINT fk_vehicle_verif_admin FOREIGN KEY (reviewed_by_admin_id) REFERENCES users (id),
    INDEX idx_vvh_vehicle (vehicle_id),
    INDEX idx_vvh_document (document_id),
    INDEX idx_vvh_admin (reviewed_by_admin_id),
    INDEX idx_vvh_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Create Table: driver_vehicle_assignments (Active & Historical Driver-Vehicle Linkages)
CREATE TABLE IF NOT EXISTS driver_vehicle_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    assigned_by_admin_id BIGINT DEFAULT NULL,
    status ENUM('ACTIVE', 'INACTIVE', 'TERMINATED') NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_at TIMESTAMP NULL DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dva_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_dva_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_dva_admin FOREIGN KEY (assigned_by_admin_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_dva_vehicle (vehicle_id),
    INDEX idx_dva_driver (driver_id),
    INDEX idx_dva_status (status),
    INDEX idx_dva_current (vehicle_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Seed Sample Documents for Seed Vehicle (Toyota Hyryder)
INSERT INTO vehicle_documents (id, vehicle_id, document_type, document_number, file_name, file_path, content_type, file_size, issue_date, expiry_date, issuing_authority, verification_status, reviewed_by_admin_id, reviewed_at, version, is_current)
VALUES
(1, 1, 'REGISTRATION_CERTIFICATE', 'RC-KA-01-EQ-4829', 'rc_hyryder.pdf', 'uploads/vehicle-documents/rc_hyryder.pdf', 'application/pdf', 1048576, '2023-03-10', '2038-03-09', 'RTO Indiranagar Bangalore', 'VERIFIED', 1, '2026-01-02 10:30:00', 1, TRUE),
(2, 1, 'VEHICLE_INSURANCE', 'POL-HDFC-98765432', 'insurance_hyryder.pdf', 'uploads/vehicle-documents/insurance_hyryder.pdf', 'application/pdf', 838860, '2025-03-01', '2026-02-28', 'HDFC ERGO General Insurance', 'VERIFIED', 1, '2026-01-02 10:35:00', 1, TRUE),
(3, 1, 'POLLUTION_UNDER_CONTROL', 'PUC-KA01-2025-1102', 'puc_hyryder.pdf', 'uploads/vehicle-documents/puc_hyryder.pdf', 'application/pdf', 419430, '2025-10-01', '2026-04-01', 'Karnataka Transport Emission Centre', 'VERIFIED', 1, '2026-01-02 10:40:00', 1, TRUE)
ON DUPLICATE KEY UPDATE verification_status=VALUES(verification_status);

-- 6. Seed Sample Verification History for Seed Vehicle
INSERT INTO vehicle_verification_history (id, vehicle_id, document_id, action_type, previous_status, new_status, reviewed_by_admin_id, reason, created_at)
VALUES
(1, 1, 1, 'DOCUMENT_REVIEW', 'PENDING_REVIEW', 'VERIFIED', 1, 'Registration Certificate validated against Vahan portal', '2026-01-02 10:30:00'),
(2, 1, 2, 'DOCUMENT_REVIEW', 'PENDING_REVIEW', 'VERIFIED', 1, 'Comprehensive motor insurance active and verified', '2026-01-02 10:35:00'),
(3, 1, 3, 'DOCUMENT_REVIEW', 'PENDING_REVIEW', 'VERIFIED', 1, 'Emission certificate valid', '2026-01-02 10:40:00'),
(4, 1, NULL, 'VEHICLE_VERIFICATION', 'PENDING_VERIFICATION', 'VERIFIED', 1, 'All mandatory vehicle compliance documents verified and approved', '2026-01-02 10:45:00')
ON DUPLICATE KEY UPDATE new_status=VALUES(new_status);

-- 7. Seed Initial Driver-Vehicle Assignment for Seed Vehicle
INSERT INTO driver_vehicle_assignments (id, vehicle_id, driver_id, assigned_by_admin_id, status, assigned_at, reason)
VALUES
(1, 1, 1, 1, 'ACTIVE', '2026-01-02 11:00:00', 'Initial vehicle registration assignment to owning driver Vikram Singh')
ON DUPLICATE KEY UPDATE status=VALUES(status);

SET FOREIGN_KEY_CHECKS = 1;
