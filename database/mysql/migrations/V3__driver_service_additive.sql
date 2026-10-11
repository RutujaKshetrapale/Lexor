-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Driver Service Additive Migration (V3)
-- Extends driver_profiles and creates driver_documents and driver_verification_history
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Modify driver_profiles to allow draft state during initial onboarding
-- and add detailed personal, address, license, and onboarding status columns
ALTER TABLE driver_profiles
    MODIFY COLUMN license_number VARCHAR(50) NULL,
    MODIFY COLUMN license_expiry_date DATE NULL,
    ADD COLUMN license_issue_date DATE NULL AFTER license_expiry_date,
    ADD COLUMN license_issuing_authority VARCHAR(100) NULL AFTER license_issue_date,
    ADD COLUMN license_issuing_state VARCHAR(100) NULL AFTER license_issuing_authority,
    ADD COLUMN license_issuing_country VARCHAR(100) NULL DEFAULT 'India' AFTER license_issuing_state,
    ADD COLUMN license_class VARCHAR(50) NULL DEFAULT 'LMV' AFTER license_issuing_country,
    ADD COLUMN date_of_birth DATE NULL AFTER license_class,
    ADD COLUMN residential_address VARCHAR(255) NULL AFTER date_of_birth,
    ADD COLUMN city VARCHAR(100) NULL AFTER residential_address,
    ADD COLUMN state VARCHAR(100) NULL AFTER city,
    ADD COLUMN postal_code VARCHAR(20) NULL AFTER state,
    ADD COLUMN country VARCHAR(100) NULL DEFAULT 'India' AFTER postal_code,
    ADD COLUMN preferred_language VARCHAR(50) NULL DEFAULT 'English' AFTER country,
    ADD COLUMN onboarding_status ENUM('INCOMPLETE', 'PENDING_REVIEW', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'SUSPENDED') NOT NULL DEFAULT 'INCOMPLETE' AFTER background_check_status,
    ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER onboarding_status,
    ADD INDEX idx_driver_onboarding (onboarding_status);

-- 2. Create Table: driver_documents (Document Metadata and Verification Workflow)
CREATE TABLE IF NOT EXISTS driver_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    document_type ENUM('DRIVING_LICENSE', 'GOVERNMENT_ID', 'ADDRESS_PROOF', 'PROFILE_PHOTO', 'BACKGROUND_CHECK', 'OTHER') NOT NULL,
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
    CONSTRAINT fk_driver_documents_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_driver_documents_admin FOREIGN KEY (reviewed_by_admin_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_driver_docs_driver (driver_id),
    INDEX idx_driver_docs_status (verification_status),
    INDEX idx_driver_docs_type (document_type),
    INDEX idx_driver_docs_current (driver_id, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Create Table: driver_verification_history (Audit Trail for Administrative Review Decisions)
CREATE TABLE IF NOT EXISTS driver_verification_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    document_id BIGINT DEFAULT NULL,
    action_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50) NOT NULL,
    new_status VARCHAR(50) NOT NULL,
    reviewed_by_admin_id BIGINT NOT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_driver_verif_driver FOREIGN KEY (driver_id) REFERENCES driver_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_driver_verif_doc FOREIGN KEY (document_id) REFERENCES driver_documents (id) ON DELETE SET NULL,
    CONSTRAINT fk_driver_verif_admin FOREIGN KEY (reviewed_by_admin_id) REFERENCES users (id),
    INDEX idx_dvh_driver (driver_id),
    INDEX idx_dvh_document (document_id),
    INDEX idx_dvh_admin (reviewed_by_admin_id),
    INDEX idx_dvh_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Update Existing Seed Driver (Vikram Singh) with Verified Onboarding Status and Sample Records
UPDATE driver_profiles 
SET onboarding_status = 'VERIFIED',
    license_issue_date = '2023-01-15',
    license_issuing_authority = 'Karnataka Transport Department',
    license_issuing_state = 'Karnataka',
    license_issuing_country = 'India',
    license_class = 'LMV',
    date_of_birth = '1990-05-15',
    residential_address = 'Indiranagar 100ft Road',
    city = 'Bengaluru',
    state = 'Karnataka',
    postal_code = '560038',
    country = 'India',
    preferred_language = 'English'
WHERE id = 1;

-- Seed sample documents for Vikram Singh
INSERT INTO driver_documents (id, driver_id, document_type, document_number, file_name, file_path, content_type, file_size, issue_date, expiry_date, issuing_authority, verification_status, reviewed_by_admin_id, reviewed_at, version, is_current)
VALUES
(1, 1, 'DRIVING_LICENSE', 'KA-01-20230009876', 'dl_vikram_singh.pdf', 'uploads/documents/dl_vikram_singh.pdf', 'application/pdf', 1048576, '2023-01-15', '2028-12-31', 'Karnataka Transport Department', 'VERIFIED', 1, '2026-01-02 10:30:00', 1, TRUE),
(2, 1, 'GOVERNMENT_ID', 'AADHAAR-XXXX-XXXX-9876', 'aadhaar_vikram.pdf', 'uploads/documents/aadhaar_vikram.pdf', 'application/pdf', 786432, '2018-06-10', NULL, 'UIDAI', 'VERIFIED', 1, '2026-01-02 10:35:00', 1, TRUE),
(3, 1, 'ADDRESS_PROOF', 'ELEC-BILL-2026-01', 'address_proof_vikram.pdf', 'uploads/documents/address_proof_vikram.pdf', 'application/pdf', 524288, '2026-01-01', NULL, 'BESCOM', 'VERIFIED', 1, '2026-01-02 10:40:00', 1, TRUE)
ON DUPLICATE KEY UPDATE verification_status=VALUES(verification_status);

-- Seed sample verification history for Vikram Singh
INSERT INTO driver_verification_history (id, driver_id, document_id, action_type, previous_status, new_status, reviewed_by_admin_id, reason, created_at)
VALUES
(1, 1, 1, 'DOCUMENT_REVIEW', 'PENDING_REVIEW', 'VERIFIED', 1, 'Driving license verified against state RTO database', '2026-01-02 10:30:00'),
(2, 1, 2, 'DOCUMENT_REVIEW', 'PENDING_REVIEW', 'VERIFIED', 1, 'Government ID proof verified', '2026-01-02 10:35:00'),
(3, 1, 3, 'DOCUMENT_REVIEW', 'PENDING_REVIEW', 'VERIFIED', 1, 'Address proof verified', '2026-01-02 10:40:00'),
(4, 1, NULL, 'DRIVER_VERIFICATION', 'UNDER_REVIEW', 'VERIFIED', 1, 'Driver profile and all mandatory documents approved', '2026-01-02 10:45:00')
ON DUPLICATE KEY UPDATE new_status=VALUES(new_status);

SET FOREIGN_KEY_CHECKS = 1;
