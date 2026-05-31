-- V2__refresh_token_family_id_uuid.sql
ALTER TABLE refresh_tokens
    ALTER COLUMN family_id TYPE UUID
    USING family_id::UUID;
