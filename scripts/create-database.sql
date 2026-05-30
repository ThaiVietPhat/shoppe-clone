-- Script khởi tạo database cho môi trường local / staging / production.
-- Chạy một lần duy nhất bởi DBA hoặc trong docker-entrypoint.
-- Bảng và schema do Flyway quản lý — KHÔNG tạo thủ công ở đây.

-- Tạo database
CREATE DATABASE shopee_db
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TEMPLATE = template0;

-- Tạo application user (không dùng superuser postgres trong production)
-- Thay 'your_app_password' bằng password thực trước khi chạy
CREATE USER shopee_app WITH PASSWORD 'your_app_password';
GRANT CONNECT ON DATABASE shopee_db TO shopee_app;

-- Sau khi kết nối vào shopee_db, chạy tiếp:
-- GRANT USAGE ON SCHEMA public TO shopee_app;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO shopee_app;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO shopee_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO shopee_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO shopee_app;
