-- V11__add_checkout_timeout_release_indexes.sql

-- 1. Index for polling pending checkout sessions by status and expiration time
CREATE INDEX IF NOT EXISTS idx_checkout_sessions_status_expires_at ON checkout_sessions(status, expires_at);

-- 2. Index for retrieving inventory reservations by checkout session and reservation status
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_session_status ON inventory_reservations(checkout_session_id, status);
