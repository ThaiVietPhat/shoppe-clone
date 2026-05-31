-- Run once with the local postgres admin account:
-- psql -U postgres -f scripts/create-database.sql
--
-- Application tables are created by Flyway when Spring Boot starts.

CREATE USER shoppe WITH PASSWORD 'shoppe';
CREATE DATABASE shopee_db OWNER shoppe;
