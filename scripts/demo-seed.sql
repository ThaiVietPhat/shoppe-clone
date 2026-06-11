-- demo-seed.sql — Task 7: deterministic demo data for local/deployed demo environments.
-- Run AFTER Flyway migrations (app started at least once):
--   psql "$DATABASE_URL" -f scripts/demo-seed.sql
-- Idempotent: fixed UUIDs + ON CONFLICT DO NOTHING.
--
-- Demo accounts (password for both: "password"):
--   buyer:  demo-buyer@shopee.local
--   seller: demo-seller@shopee.local
-- BCrypt hash below is the standard test hash for "password".

BEGIN;

-- ==================== Users ====================
INSERT INTO users (id, email, normalized_email, password_hash, role, status)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'demo-buyer@shopee.local', 'demo-buyer@shopee.local',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'BUYER', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222222', 'demo-seller@shopee.local', 'demo-seller@shopee.local',
     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'SELLER', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- ==================== Seller shop ====================
INSERT INTO shops (id, owner_id, name, description, rating)
VALUES ('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222',
        'Demo Official Store', 'Seeded demo shop', 4.80)
ON CONFLICT (id) DO NOTHING;

-- ==================== Category ====================
INSERT INTO categories (id, parent_id, name, path)
VALUES ('44444444-4444-4444-4444-444444444444', NULL, 'Electronics', 'Electronics')
ON CONFLICT (id) DO NOTHING;

-- ==================== Products (ACTIVE) ====================
INSERT INTO products (id, shop_id, category_id, name, description, status, brand, min_price, max_price)
VALUES
    ('55555555-5555-5555-5555-555555555551', '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444', 'Demo Wireless Headphones',
     'Over-ear wireless headphones with noise cancelling.', 'ACTIVE', 'DemoAudio', 590000, 590000),
    ('55555555-5555-5555-5555-555555555552', '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444', 'Demo Mechanical Keyboard',
     '87-key hot-swappable mechanical keyboard.', 'ACTIVE', 'DemoKeys', 890000, 1090000)
ON CONFLICT (id) DO NOTHING;

-- ==================== Variants ====================
INSERT INTO product_variants (id, product_id, sku, name, price, active)
VALUES
    ('66666666-6666-6666-6666-666666666661', '55555555-5555-5555-5555-555555555551',
     'DEMO-HP-BLACK', 'Black', 590000, TRUE),
    ('66666666-6666-6666-6666-666666666662', '55555555-5555-5555-5555-555555555552',
     'DEMO-KB-RED', 'Red Switch', 890000, TRUE),
    ('66666666-6666-6666-6666-666666666663', '55555555-5555-5555-5555-555555555552',
     'DEMO-KB-BROWN', 'Brown Switch', 1090000, TRUE)
ON CONFLICT (id) DO NOTHING;

-- ==================== Inventory ====================
INSERT INTO inventories (id, variant_id, available_stock, reserved_stock)
VALUES
    ('77777777-7777-7777-7777-777777777771', '66666666-6666-6666-6666-666666666661', 100, 0),
    ('77777777-7777-7777-7777-777777777772', '66666666-6666-6666-6666-666666666662', 50, 0),
    ('77777777-7777-7777-7777-777777777773', '66666666-6666-6666-6666-666666666663', 25, 0)
ON CONFLICT (id) DO NOTHING;

-- ==================== Buyer default address ====================
INSERT INTO addresses (id, user_id, recipient_name, phone, address_line,
                       ward_code, ward_name, district_code, district_name,
                       province_code, province_name, is_default)
VALUES ('88888888-8888-8888-8888-888888888888', '11111111-1111-1111-1111-111111111111',
        'Demo Buyer', '0900000000', '123 Demo Street',
        'W001', 'Demo Ward', 'D001', 'Demo District', 'P001', 'Demo Province', TRUE)
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- Embeddings/search index: trigger by re-publishing products via the seller API,
-- or call the seller publish endpoint once per product (see README demo script).
