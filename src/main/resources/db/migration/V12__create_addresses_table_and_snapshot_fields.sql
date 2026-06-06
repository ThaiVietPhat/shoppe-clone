-- V12__create_addresses_table_and_snapshot_fields.sql

-- 1. Create addresses table
DROP TABLE IF EXISTS addresses CASCADE;

CREATE TABLE addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    recipient_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    ward_code VARCHAR(100) NOT NULL,
    ward_name VARCHAR(100) NOT NULL,
    district_code VARCHAR(100) NOT NULL,
    district_name VARCHAR(100) NOT NULL,
    province_code VARCHAR(100) NOT NULL,
    province_name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Partial unique index to enforce at most one default address per user
CREATE UNIQUE INDEX IF NOT EXISTS uq_addresses_default_user ON addresses(user_id) WHERE is_default = TRUE;
CREATE INDEX IF NOT EXISTS idx_addresses_user_id ON addresses(user_id);

-- 2. Alter checkout_sessions and orders to add snapshot fields
ALTER TABLE checkout_sessions 
    ADD COLUMN IF NOT EXISTS shipping_recipient_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS shipping_phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS shipping_address_line VARCHAR(255),
    ADD COLUMN IF NOT EXISTS shipping_ward_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_ward_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_district_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_district_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_province_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_province_name VARCHAR(100);

ALTER TABLE orders 
    ADD COLUMN IF NOT EXISTS shipping_recipient_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS shipping_phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS shipping_address_line VARCHAR(255),
    ADD COLUMN IF NOT EXISTS shipping_ward_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_ward_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_district_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_district_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_province_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_province_name VARCHAR(100);

-- Backfill data from shipping_street/shipping_city
UPDATE checkout_sessions SET 
    shipping_recipient_name = 'Unknown',
    shipping_phone = '0000000000',
    shipping_address_line = shipping_street,
    shipping_ward_code = 'WARD-UNKNOWN',
    shipping_ward_name = 'Unknown',
    shipping_district_code = 'DIST-UNKNOWN',
    shipping_district_name = 'Unknown',
    shipping_province_code = 'PROV-UNKNOWN',
    shipping_province_name = shipping_city
WHERE shipping_recipient_name IS NULL;

UPDATE orders SET 
    shipping_recipient_name = 'Unknown',
    shipping_phone = '0000000000',
    shipping_address_line = shipping_street,
    shipping_ward_code = 'WARD-UNKNOWN',
    shipping_ward_name = 'Unknown',
    shipping_district_code = 'DIST-UNKNOWN',
    shipping_district_name = 'Unknown',
    shipping_province_code = 'PROV-UNKNOWN',
    shipping_province_name = shipping_city
WHERE shipping_recipient_name IS NULL;

-- Enforce NOT NULL constraints
ALTER TABLE checkout_sessions 
    ALTER COLUMN shipping_recipient_name SET NOT NULL,
    ALTER COLUMN shipping_phone SET NOT NULL,
    ALTER COLUMN shipping_address_line SET NOT NULL,
    ALTER COLUMN shipping_ward_code SET NOT NULL,
    ALTER COLUMN shipping_ward_name SET NOT NULL,
    ALTER COLUMN shipping_district_code SET NOT NULL,
    ALTER COLUMN shipping_district_name SET NOT NULL,
    ALTER COLUMN shipping_province_code SET NOT NULL,
    ALTER COLUMN shipping_province_name SET NOT NULL;

ALTER TABLE orders 
    ALTER COLUMN shipping_recipient_name SET NOT NULL,
    ALTER COLUMN shipping_phone SET NOT NULL,
    ALTER COLUMN shipping_address_line SET NOT NULL,
    ALTER COLUMN shipping_ward_code SET NOT NULL,
    ALTER COLUMN shipping_ward_name SET NOT NULL,
    ALTER COLUMN shipping_district_code SET NOT NULL,
    ALTER COLUMN shipping_district_name SET NOT NULL,
    ALTER COLUMN shipping_province_code SET NOT NULL,
    ALTER COLUMN shipping_province_name SET NOT NULL;

-- Drop old columns
ALTER TABLE checkout_sessions DROP COLUMN IF EXISTS shipping_street;
ALTER TABLE checkout_sessions DROP COLUMN IF EXISTS shipping_city;

ALTER TABLE orders DROP COLUMN IF EXISTS shipping_street;
ALTER TABLE orders DROP COLUMN IF EXISTS shipping_city;
