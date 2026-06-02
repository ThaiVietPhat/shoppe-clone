-- V6__harden_refresh_token_families.sql

-- 1. Validate the NOT VALID foreign key constraint from V5
ALTER TABLE refresh_tokens VALIDATE CONSTRAINT fk_refresh_tokens_family_id;

-- 2. Drop compatibility trigger and trigger function
DROP TRIGGER IF EXISTS trigger_auto_create_token_family ON refresh_tokens;
DROP FUNCTION IF EXISTS auto_create_token_family_fn();

-- 3. Create index for logout-all queries
CREATE INDEX IF NOT EXISTS idx_refresh_token_families_user_id_id ON refresh_token_families (user_id, id);

-- 4. Establish composite foreign key to enforce ownership invariance
-- First, add unique constraint to parent table to allow it to be referenced
ALTER TABLE refresh_token_families
    ADD CONSTRAINT uq_refresh_token_families_id_user_id UNIQUE (id, user_id);

-- Next, drop the old single-column foreign key constraint
ALTER TABLE refresh_tokens
    DROP CONSTRAINT IF EXISTS fk_refresh_tokens_family_id;

-- Finally, add the composite foreign key constraint
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_family_id_user_id
    FOREIGN KEY (family_id, user_id) REFERENCES refresh_token_families(id, user_id)
    ON DELETE CASCADE;
