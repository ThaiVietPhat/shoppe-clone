-- V8__add_normalized_email_and_verification_tokens.sql

-- 1. Preflight check for duplicate normalized emails
DO $$
DECLARE
    duplicate_count INT;
BEGIN
    SELECT COUNT(*)
    INTO duplicate_count
    FROM (
        SELECT LOWER(TRIM(email))
        FROM users
        GROUP BY LOWER(TRIM(email))
        HAVING COUNT(*) > 1
    ) t;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION 'Preflight check failed: Found % duplicate normalized email(s) in users table. Please merge or delete duplicates before running the migration.', duplicate_count;
    END IF;
END $$;

-- 2. Remove old unique constraint on email
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

-- 3. Add normalized_email column
ALTER TABLE users ADD COLUMN normalized_email VARCHAR(255);

-- 4. Backfill existing emails
UPDATE users SET normalized_email = LOWER(TRIM(email));

-- 5. Set normalized_email as NOT NULL and add unique constraint
ALTER TABLE users ALTER COLUMN normalized_email SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_normalized_email UNIQUE (normalized_email);

-- 6. Create verification_tokens table
CREATE TABLE verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. Indexes for verification queries (removed redundant unique token_hash index)
CREATE INDEX idx_verification_tokens_user_id_consumed ON verification_tokens(user_id, consumed_at);
