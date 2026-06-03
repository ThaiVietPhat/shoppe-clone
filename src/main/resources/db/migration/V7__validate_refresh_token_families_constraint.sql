-- V7__validate_refresh_token_families_constraint.sql

-- 1. Validate the composite FK constraint
ALTER TABLE refresh_tokens VALIDATE CONSTRAINT fk_refresh_tokens_family_id_user_id;

-- 2. Clean up rolling-deploy trigger & trigger function
DROP TRIGGER IF EXISTS trigger_auto_create_token_family ON refresh_tokens;
DROP FUNCTION IF EXISTS auto_create_token_family_fn();
