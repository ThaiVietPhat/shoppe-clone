-- V18__create_payment_module_schema.sql
-- Task 5.1: PaymentModule schema — payment attempts, webhook event ledger, order payment snapshot.

CREATE TABLE IF NOT EXISTS payment_attempts (
    id UUID PRIMARY KEY,
    checkout_session_id UUID NOT NULL REFERENCES checkout_sessions(id),
    method VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    external_tx_id VARCHAR(255),
    provider_payload_hash VARCHAR(64),
    reconciliation_reason VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_checkout_session_id ON payment_attempts(checkout_session_id);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_status_expires_at ON payment_attempts(status, expires_at);

-- At most one non-terminal attempt per checkout session (enforced at DB level against concurrent initiates)
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_attempts_non_terminal
    ON payment_attempts(checkout_session_id)
    WHERE status IN ('CREATED', 'INITIATING', 'PENDING');

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    payment_attempt_id UUID REFERENCES payment_attempts(id),
    raw_payload_hash VARCHAR(64),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payment_webhook_events_provider_event UNIQUE (provider, provider_event_id)
);

-- Order payment snapshot columns
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30);

UPDATE orders SET payment_status = 'UNPAID' WHERE payment_status IS NULL;
ALTER TABLE orders ALTER COLUMN payment_status SET NOT NULL;
ALTER TABLE orders ALTER COLUMN payment_status SET DEFAULT 'UNPAID';
