-- V10__create_order_checkout_reserve_schema.sql

-- 1. Create checkout_sessions table (new table)
CREATE TABLE IF NOT EXISTS checkout_sessions (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
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

-- 3. Alter orders (existing table from V1)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS checkout_session_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_street VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_city VARCHAR(255);

-- Create dummy checkout session if any orders exist with null checkout_session_id
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM orders WHERE checkout_session_id IS NULL) THEN
        INSERT INTO checkout_sessions (id, buyer_id, status, total_amount, shipping_street, shipping_city, expires_at)
        VALUES ('00000000-0000-0000-0000-000000000000', (SELECT id FROM users LIMIT 1), 'EXPIRED', 0.00, 'Unknown', 'Unknown', NOW())
        ON CONFLICT DO NOTHING;
        
        UPDATE orders SET checkout_session_id = '00000000-0000-0000-0000-000000000000' WHERE checkout_session_id IS NULL;
    END IF;
END $$;

UPDATE orders SET shipping_street = 'Unknown' WHERE shipping_street IS NULL;
UPDATE orders SET shipping_city = 'Unknown' WHERE shipping_city IS NULL;

ALTER TABLE orders ALTER COLUMN checkout_session_id SET NOT NULL;
ALTER TABLE orders ALTER COLUMN shipping_street SET NOT NULL;
ALTER TABLE orders ALTER COLUMN shipping_city SET NOT NULL;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS fk_orders_checkout_session;
ALTER TABLE orders ADD CONSTRAINT fk_orders_checkout_session FOREIGN KEY (checkout_session_id) REFERENCES checkout_sessions(id);

CREATE INDEX IF NOT EXISTS idx_orders_buyer_id ON orders(buyer_id);
CREATE INDEX IF NOT EXISTS idx_orders_shop_id ON orders(shop_id);
CREATE INDEX IF NOT EXISTS idx_orders_checkout_session_id ON orders(checkout_session_id);

-- 4. Alter order_items (existing table from V1)
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS order_items_order_id_order_created_at_fkey;
ALTER TABLE order_items DROP COLUMN IF EXISTS order_created_at;

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS product_name VARCHAR(255);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS variant_name VARCHAR(255);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS sku VARCHAR(100);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS subtotal DECIMAL(15,2);

UPDATE order_items SET product_name = 'Unknown' WHERE product_name IS NULL;
UPDATE order_items SET variant_name = 'Unknown' WHERE variant_name IS NULL;
UPDATE order_items SET subtotal = price * quantity WHERE subtotal IS NULL;

ALTER TABLE order_items ALTER COLUMN product_name SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN variant_name SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN subtotal SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);

-- 5. Create inventory_reservations (new table)
CREATE TABLE IF NOT EXISTS inventory_reservations (
    id UUID PRIMARY KEY,
    checkout_session_id UUID NOT NULL REFERENCES checkout_sessions(id) ON DELETE CASCADE,
    order_id UUID NOT NULL,
    variant_id UUID NOT NULL REFERENCES product_variants(id),
    quantity INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_checkout_session_id ON inventory_reservations(checkout_session_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_order_id ON inventory_reservations(order_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_variant_id ON inventory_reservations(variant_id);
