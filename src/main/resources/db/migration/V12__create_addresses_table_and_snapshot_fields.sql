-- V12__create_addresses_table_and_snapshot_fields.sql

-- 1. Expand legacy addresses table from V1 without dropping user data.
ALTER TABLE addresses
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS address_line VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ward_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ward_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS district_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS district_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS province_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS province_name VARCHAR(100);

UPDATE addresses SET
    recipient_name = COALESCE(recipient_name, 'Unknown'),
    phone = COALESCE(phone, '0000000000'),
    address_line = COALESCE(address_line, street, 'Unknown'),
    ward_code = COALESCE(ward_code, 'WARD-UNKNOWN'),
    ward_name = COALESCE(ward_name, 'Unknown'),
    district_code = COALESCE(district_code, 'DIST-UNKNOWN'),
    district_name = COALESCE(district_name, 'Unknown'),
    province_code = COALESCE(province_code, 'PROV-UNKNOWN'),
    province_name = COALESCE(province_name, city, 'Unknown'),
    is_default = COALESCE(is_default, FALSE);

-- Keep at most one default address per user before adding the partial unique index.
WITH ranked_defaults AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY updated_at DESC, created_at DESC, id) AS rn
    FROM addresses
    WHERE is_default = TRUE
)
UPDATE addresses a
SET is_default = FALSE
FROM ranked_defaults r
WHERE a.id = r.id AND r.rn > 1;

-- If a user has addresses but no default, promote the most recently updated address.
WITH ranked_addresses AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY updated_at DESC, created_at DESC, id) AS rn,
           BOOL_OR(is_default) OVER (PARTITION BY user_id) AS has_default
    FROM addresses
)
UPDATE addresses a
SET is_default = TRUE
FROM ranked_addresses r
WHERE a.id = r.id AND r.rn = 1 AND r.has_default = FALSE;

ALTER TABLE addresses
    ALTER COLUMN recipient_name SET NOT NULL,
    ALTER COLUMN phone SET NOT NULL,
    ALTER COLUMN address_line SET NOT NULL,
    ALTER COLUMN ward_code SET NOT NULL,
    ALTER COLUMN ward_name SET NOT NULL,
    ALTER COLUMN district_code SET NOT NULL,
    ALTER COLUMN district_name SET NOT NULL,
    ALTER COLUMN province_code SET NOT NULL,
    ALTER COLUMN province_name SET NOT NULL,
    ALTER COLUMN is_default SET NOT NULL;

-- Partial unique index to enforce at most one default address per user
CREATE UNIQUE INDEX IF NOT EXISTS uq_addresses_default_user ON addresses(user_id) WHERE is_default = TRUE;
CREATE INDEX IF NOT EXISTS idx_addresses_user_id ON addresses(user_id);

ALTER TABLE addresses DROP COLUMN IF EXISTS street;
ALTER TABLE addresses DROP COLUMN IF EXISTS city;

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
