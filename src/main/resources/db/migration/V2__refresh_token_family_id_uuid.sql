-- V2__refresh_token_family_id_uuid.sql
-- Assumption: Since no production writer has been introduced yet,
-- this migration assumes any existing family_id rows (if any exist) are in valid UUID string format.
ALTER TABLE refresh_tokens
    ALTER COLUMN family_id TYPE UUID
    USING family_id::UUID;
