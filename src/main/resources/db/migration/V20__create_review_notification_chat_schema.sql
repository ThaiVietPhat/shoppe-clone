-- V20__create_review_notification_chat_schema.sql
-- Task 7: Complete The Loop — reviews, notification inbox, buyer-seller chat.

-- Drop unused legacy V1 placeholder tables (never written by application code)
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS chat_room_participants;
DROP TABLE IF EXISTS chat_rooms;
DROP TABLE IF EXISTS reviews;

-- ==================== Reviews ====================
-- One review per order item, completed/delivered orders only (enforced in service).
CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL UNIQUE,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    buyer_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reviews_product_created
    ON reviews(product_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reviews_buyer_created
    ON reviews(buyer_id, created_at DESC);

-- Product rating read-model counters (refreshed asynchronously after review commit)
ALTER TABLE products ADD COLUMN IF NOT EXISTS rating_avg DECIMAL(3, 2) NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS rating_count BIGINT NOT NULL DEFAULT 0;

-- ==================== Notification Inbox ====================
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(2000),
    ref_type VARCHAR(40),
    ref_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications(user_id) WHERE read_at IS NULL;

-- ==================== Buyer-Seller Chat ====================
CREATE TABLE IF NOT EXISTS chat_rooms (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    buyer_last_read_at TIMESTAMPTZ,
    seller_last_read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chat_rooms_buyer_shop UNIQUE (buyer_id, shop_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_shop ON chat_rooms(shop_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id),
    sender_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_room_created
    ON chat_messages(room_id, created_at DESC);
