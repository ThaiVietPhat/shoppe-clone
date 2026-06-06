-- V10__create_order_checkout_reserve_schema.sql

-- 1. Create checkout_sessions table (new table)
CREATE TABLE IF NOT EXISTS checkout_sessions (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL CHECK (total_amount >= 0),
    shipping_street VARCHAR(255) NOT NULL,
    shipping_city VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Alter idempotency_keys (existing table from V1)
ALTER TABLE idempotency_keys DROP CONSTRAINT IF EXISTS idempotency_keys_idempotency_key_key;

ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS actor_id UUID;
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS operation VARCHAR(255);
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS status VARCHAR(50);
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- Populate default values for existing rows to enforce NOT NULL
UPDATE idempotency_keys SET actor_id = '00000000-0000-0000-0000-000000000000' WHERE actor_id IS NULL;
UPDATE idempotency_keys SET operation = 'CHECKOUT' WHERE operation IS NULL;
UPDATE idempotency_keys SET request_hash = '' WHERE request_hash IS NULL;
UPDATE idempotency_keys SET status = 'COMPLETED' WHERE status IS NULL;
UPDATE idempotency_keys SET expires_at = NOW() WHERE expires_at IS NULL;

ALTER TABLE idempotency_keys ALTER COLUMN actor_id SET NOT NULL;
ALTER TABLE idempotency_keys ALTER COLUMN operation SET NOT NULL;
ALTER TABLE idempotency_keys ALTER COLUMN request_hash SET NOT NULL;
ALTER TABLE idempotency_keys ALTER COLUMN status SET NOT NULL;
ALTER TABLE idempotency_keys ALTER COLUMN expires_at SET NOT NULL;

-- Add new unique constraint
ALTER TABLE idempotency_keys DROP CONSTRAINT IF EXISTS uq_idempotency_keys_actor_operation_key;
ALTER TABLE idempotency_keys ADD CONSTRAINT uq_idempotency_keys_actor_operation_key UNIQUE (actor_id, operation, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);


-- 3. De-partition orders, order_items, payments, returns, and voucher_usages tables
-- Rename partitioned/dependent tables
ALTER TABLE order_items RENAME TO order_items_old;
ALTER TABLE payments RENAME TO payments_old;
ALTER TABLE voucher_usages RENAME TO voucher_usages_old;
ALTER TABLE returns RENAME TO returns_old;
ALTER TABLE orders RENAME TO orders_old;

-- Create non-partitioned orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    shop_id UUID NOT NULL REFERENCES shops(id),
    checkout_session_id UUID REFERENCES checkout_sessions(id),
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL CHECK (total_amount >= 0),
    shipping_street VARCHAR(255),
    shipping_city VARCHAR(255),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Backfill: Create expired checkout sessions for existing orders (per buyer)
-- Backfill: Create expired checkout sessions for existing orders (1:1 with old orders to prevent join multiplication)
INSERT INTO checkout_sessions (id, buyer_id, status, total_amount, shipping_street, shipping_city, expires_at, created_at, updated_at)
SELECT 
    id, -- Use old order ID as checkout session ID to map 1:1 deterministically
    buyer_id,
    'EXPIRED',
    total_amount, -- Use old order's total_amount instead of 0.00
    'Unknown',
    'Unknown',
    NOW(),
    NOW(),
    NOW()
FROM orders_old;

-- Populate new orders table from old partitioned orders table, joining on backfilled checkout sessions 1:1
INSERT INTO orders (id, buyer_id, shop_id, checkout_session_id, status, total_amount, shipping_street, shipping_city, version, created_at, updated_at)
SELECT 
    o.id,
    o.buyer_id,
    o.shop_id,
    o.id, -- matches checkout_session_id 1:1
    o.status,
    o.total_amount,
    'Unknown',
    'Unknown',
    o.version,
    o.created_at,
    o.updated_at
FROM orders_old o;

-- Ensure new columns are NOT NULL and add foreign key
ALTER TABLE orders ALTER COLUMN checkout_session_id SET NOT NULL;
ALTER TABLE orders ALTER COLUMN shipping_street SET NOT NULL;
ALTER TABLE orders ALTER COLUMN shipping_city SET NOT NULL;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS fk_orders_checkout_session;
ALTER TABLE orders ADD CONSTRAINT fk_orders_checkout_session FOREIGN KEY (checkout_session_id) REFERENCES checkout_sessions(id);

CREATE INDEX IF NOT EXISTS idx_orders_buyer_id ON orders(buyer_id);
CREATE INDEX IF NOT EXISTS idx_orders_shop_id ON orders(shop_id);
CREATE INDEX IF NOT EXISTS idx_orders_checkout_session_id ON orders(checkout_session_id);


-- Create new non-partitioned order_items table
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants(id),
    product_name VARCHAR(255) NOT NULL,
    variant_name VARCHAR(255) NOT NULL,
    sku VARCHAR(100),
    price DECIMAL(15,2) NOT NULL CHECK (price >= 0),
    quantity INT NOT NULL CHECK (quantity > 0),
    subtotal DECIMAL(15,2) NOT NULL CHECK (subtotal >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Populate new order_items table
INSERT INTO order_items (id, order_id, variant_id, product_name, variant_name, sku, price, quantity, subtotal, created_at, updated_at)
SELECT 
    id, 
    order_id, 
    variant_id, 
    'Unknown', 
    'Unknown', 
    NULL, -- sku was not present in V1
    price, 
    quantity, 
    price * quantity, 
    created_at, 
    updated_at
FROM order_items_old;

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);


-- Create new non-partitioned payments table
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    external_tx_id VARCHAR(255) UNIQUE,
    amount DECIMAL(15,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Populate payments
INSERT INTO payments (id, order_id, external_tx_id, amount, status, provider, version, created_at, updated_at)
SELECT id, order_id, external_tx_id, amount, status, provider, version, created_at, updated_at FROM payments_old;


-- Create new non-partitioned voucher_usages table
CREATE TABLE voucher_usages (
    id UUID PRIMARY KEY,
    voucher_id UUID NOT NULL REFERENCES vouchers(id),
    user_id UUID NOT NULL REFERENCES users(id),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Populate voucher_usages
INSERT INTO voucher_usages (id, voucher_id, user_id, order_id, created_at, updated_at)
SELECT id, voucher_id, user_id, order_id, created_at, updated_at FROM voucher_usages_old;


-- Create new non-partitioned returns table
CREATE TABLE returns (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Populate returns
INSERT INTO returns (id, order_id, reason, status, created_at, updated_at)
SELECT id, order_id, reason, status, created_at, updated_at FROM returns_old;


-- Re-establish reviews and return_evidence constraints
ALTER TABLE return_evidence DROP CONSTRAINT IF EXISTS return_evidence_return_id_fkey;
ALTER TABLE return_evidence ADD CONSTRAINT fk_return_evidence_return FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE CASCADE;

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_order_item_id_fkey;
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE;


-- Keep old partitioned tables for safety (non-destructive expand phase)
-- DROP TABLE IF EXISTS order_items_old CASCADE;
-- DROP TABLE IF EXISTS payments_old CASCADE;
-- DROP TABLE IF EXISTS voucher_usages_old CASCADE;
-- DROP TABLE IF EXISTS returns_old CASCADE;
-- DROP TABLE IF EXISTS orders_old CASCADE;


-- 4. Create inventory_reservations table (new table)
CREATE TABLE IF NOT EXISTS inventory_reservations (
    id UUID PRIMARY KEY,
    checkout_session_id UUID NOT NULL REFERENCES checkout_sessions(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_checkout_session_id ON inventory_reservations(checkout_session_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_order_id ON inventory_reservations(order_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_variant_id ON inventory_reservations(variant_id);
