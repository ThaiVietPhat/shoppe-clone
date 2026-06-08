-- V13__expand_catalog_and_media_schema.sql
-- Expand-only migration: adds columns/tables, no drops, no constraint changes on existing columns.

-- ==================== 1. products: new catalog fields ====================
ALTER TABLE products
    ADD COLUMN status       VARCHAR(50)   NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN brand        VARCHAR(255),
    ADD COLUMN seller_sku   VARCHAR(100),
    ADD COLUMN attributes   JSONB,
    ADD COLUMN min_price    DECIMAL(15, 2),
    ADD COLUMN max_price    DECIMAL(15, 2);

ALTER TABLE products
    ALTER COLUMN status SET DEFAULT 'DRAFT';

-- ==================== 2. categories: materialized path ====================
ALTER TABLE categories
    ADD COLUMN path VARCHAR(2000);

UPDATE categories
SET path = name
WHERE path IS NULL;

ALTER TABLE categories
    ALTER COLUMN path SET NOT NULL;

-- ==================== 3. product_variants: option labels + active flag ====================
ALTER TABLE product_variants
    ADD COLUMN option_labels JSONB,
    ADD COLUMN active        BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE products p
SET min_price = price_range.min_price,
    max_price = price_range.max_price
FROM (
    SELECT product_id, MIN(price) AS min_price, MAX(price) AS max_price
    FROM product_variants
    WHERE active = TRUE
    GROUP BY product_id
) price_range
WHERE p.id = price_range.product_id;

-- ==================== 4. media_assets: upload metadata ====================
CREATE TABLE media_assets (
    id                UUID         PRIMARY KEY,
    owner_id          UUID         NOT NULL,
    owner_type        VARCHAR(50)  NOT NULL,
    purpose           VARCHAR(50)  NOT NULL,
    object_key        VARCHAR(500) NOT NULL UNIQUE,
    original_filename VARCHAR(255),
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),
    checksum_sha256   VARCHAR(64),
    width             INT,
    height            INT,
    status            VARCHAR(50)  NOT NULL DEFAULT 'READY',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ==================== 5. product_media: product ↔ media join ====================
CREATE TABLE product_media (
    product_id UUID    NOT NULL REFERENCES products(id),
    media_id   UUID    NOT NULL REFERENCES media_assets(id),
    sort_order INT     NOT NULL DEFAULT 0,
    is_cover   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (product_id, media_id)
);

-- ==================== 6. Indexes ====================
CREATE INDEX idx_products_status_created  ON products (status, created_at DESC);
CREATE INDEX idx_products_shop_status     ON products (shop_id, status);
CREATE INDEX idx_product_media_sort       ON product_media (product_id, sort_order);
CREATE UNIQUE INDEX uq_product_media_cover ON product_media (product_id) WHERE is_cover = TRUE;
CREATE INDEX idx_media_assets_owner       ON media_assets (owner_id, purpose);
CREATE INDEX idx_product_variants_product ON product_variants (product_id, active);
