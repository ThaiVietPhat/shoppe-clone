-- V5__create_refresh_token_families.sql

CREATE TABLE refresh_token_families (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insert existing families if any exist
INSERT INTO refresh_token_families (id, user_id, created_at)
SELECT DISTINCT family_id, user_id, MIN(created_at)
FROM refresh_tokens
GROUP BY family_id, user_id
ON CONFLICT (id) DO NOTHING;

-- Add foreign key constraint to refresh_tokens
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_family_id
    FOREIGN KEY (family_id) REFERENCES refresh_token_families(id)
    ON DELETE CASCADE;
