-- demo-reset.sql — Task 7: restores the demo database to a clean post-migration state.
-- WARNING: deletes ALL transactional and catalog data. For demo environments only.
--   psql "$DATABASE_URL" -f scripts/demo-reset.sql
-- Re-seed afterwards with scripts/demo-seed.sql.

BEGIN;

TRUNCATE TABLE
    chat_messages,
    chat_rooms,
    notifications,
    reviews,
    payment_webhook_events,
    payment_attempts,
    inventory_movements,
    inventory_reservations,
    order_items,
    orders,
    checkout_sessions,
    idempotency_keys,
    product_embeddings,
    inventories,
    product_variants,
    products,
    categories,
    media_assets,
    product_media,
    addresses,
    shops,
    verification_tokens,
    refresh_tokens,
    refresh_token_families,
    oauth_identities,
    user_profiles,
    users,
    event_publication
    RESTART IDENTITY CASCADE;

COMMIT;
