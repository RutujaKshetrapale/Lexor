-- =============================================================================
-- LEXOR — AI-Powered Smart Mobility Platform
-- Ride Type Service Additive Migration (V5)
-- Extends ride_types table to add public UUID identifier and optimization indexes
-- =============================================================================

USE lexor_db;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Modify ride_types table to support UUID
ALTER TABLE ride_types
    ADD COLUMN uuid VARCHAR(36) NULL AFTER id;

-- Populate deterministic UUIDs for existing seed ride types
UPDATE ride_types
SET uuid = 'a1b2c3d4-0001-4000-8000-000000000001'
WHERE id = 1; -- LEXOR Go

UPDATE ride_types
SET uuid = 'a1b2c3d4-0002-4000-8000-000000000002'
WHERE id = 2; -- LEXOR XL

UPDATE ride_types
SET uuid = 'a1b2c3d4-0003-4000-8000-000000000003'
WHERE id = 3; -- LEXOR Premier

UPDATE ride_types
SET uuid = 'a1b2c3d4-0004-4000-8000-000000000004'
WHERE id = 4; -- LEXOR Green

-- Populate UUID for any other records that may exist without UUID
UPDATE ride_types
SET uuid = UUID()
WHERE uuid IS NULL;

-- Enforce NOT NULL and UNIQUE on ride_types.uuid
ALTER TABLE ride_types
    MODIFY COLUMN uuid VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uq_ride_types_uuid (uuid),
    ADD INDEX idx_ride_types_uuid (uuid),
    ADD INDEX idx_ride_types_active (is_active);

SET FOREIGN_KEY_CHECKS = 1;
