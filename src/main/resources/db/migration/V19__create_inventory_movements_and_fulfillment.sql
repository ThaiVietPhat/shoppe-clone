-- V19__create_inventory_movements_and_fulfillment.sql
-- Task 5b: Seller Core — inventory movement ledger + order fulfillment state machine.

CREATE TABLE IF NOT EXISTS inventory_movements (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    available_stock_after INT NOT NULL CHECK (available_stock_after >= 0),
    reserved_stock_after INT NOT NULL CHECK (reserved_stock_after >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_variant_created
    ON inventory_movements(variant_id, created_at DESC);

-- Order fulfillment state: NULL until paid, then READY_TO_SHIP -> SHIPPED -> DELIVERED
ALTER TABLE orders ADD COLUMN IF NOT EXISTS fulfillment_status VARCHAR(30);

-- Backfill legacy rows from existing order status
UPDATE orders SET fulfillment_status = 'READY_TO_SHIP'
    WHERE fulfillment_status IS NULL AND status = 'PAID' AND payment_status = 'PAID';
UPDATE orders SET fulfillment_status = 'SHIPPED'
    WHERE fulfillment_status IS NULL AND status = 'FULFILLED';
UPDATE orders SET fulfillment_status = 'DELIVERED'
    WHERE fulfillment_status IS NULL AND status IN ('DELIVERED', 'COMPLETED');

-- Seller order dashboard queries
CREATE INDEX IF NOT EXISTS idx_orders_shop_id_created_at ON orders(shop_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_shop_id_fulfillment_status ON orders(shop_id, fulfillment_status);
