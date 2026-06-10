-- Add items_subtotal and shipping_fee columns to orders and checkout_sessions.
-- Backfill: items_subtotal = total_amount, shipping_fee = 0 for existing rows.
-- Meaning: total_amount = items_subtotal + shipping_fee.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS items_subtotal DECIMAL(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shipping_fee   DECIMAL(15,2) NOT NULL DEFAULT 0;

UPDATE orders SET items_subtotal = total_amount, shipping_fee = 0 WHERE items_subtotal = 0;

ALTER TABLE checkout_sessions
    ADD COLUMN IF NOT EXISTS items_subtotal DECIMAL(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shipping_fee   DECIMAL(15,2) NOT NULL DEFAULT 0;

UPDATE checkout_sessions SET items_subtotal = total_amount, shipping_fee = 0 WHERE items_subtotal = 0;
