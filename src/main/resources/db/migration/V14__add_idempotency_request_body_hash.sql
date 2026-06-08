ALTER TABLE idempotency_keys
    ADD COLUMN IF NOT EXISTS request_body_hash VARCHAR(64);

UPDATE idempotency_keys
SET request_body_hash = request_hash
WHERE request_body_hash IS NULL;

ALTER TABLE idempotency_keys
    ALTER COLUMN request_body_hash SET NOT NULL;
