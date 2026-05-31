-- V3__refresh_token_revocation_history_and_indexes.sql

-- Add columns to support refresh token reuse detection (revocation history)
ALTER TABLE refresh_tokens
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN replaced_by_token_hash VARCHAR(255);

-- Create indexes for performance tuning
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
