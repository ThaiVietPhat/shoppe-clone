-- V5__create_refresh_token_families.sql

-- 1. Create table refresh_token_families
CREATE TABLE IF NOT EXISTS refresh_token_families (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Backfill existing families
INSERT INTO refresh_token_families (id, user_id, created_at)
SELECT DISTINCT family_id, user_id, MIN(created_at)
FROM refresh_tokens
GROUP BY family_id, user_id
ON CONFLICT (id) DO NOTHING;

-- 3. Create trigger function and trigger for rolling-deploy compatibility (auto-create family if missing)
CREATE OR REPLACE FUNCTION auto_create_token_family_fn()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO refresh_token_families (id, user_id, created_at, updated_at)
    VALUES (NEW.family_id, NEW.user_id, COALESCE(NEW.created_at, NOW()), NOW())
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_auto_create_token_family ON refresh_tokens;
CREATE TRIGGER trigger_auto_create_token_family
BEFORE INSERT ON refresh_tokens
FOR EACH ROW
EXECUTE FUNCTION auto_create_token_family_fn();

-- 4. Add foreign key constraint with NOT VALID (rolling-deploy safe)
ALTER TABLE refresh_tokens
    DROP CONSTRAINT IF EXISTS fk_refresh_tokens_family_id;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_family_id
    FOREIGN KEY (family_id) REFERENCES refresh_token_families(id)
    ON DELETE CASCADE NOT VALID;
