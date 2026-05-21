-- Applied once per tenant schema via TenantSchemaInitializer
-- (not run by Flyway automatically — Flyway is disabled in application.yml)
CREATE TABLE IF NOT EXISTS movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(50)
);
CREATE TABLE IF NOT EXISTS app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    tenant_id VARCHAR(50) NOT NULL
);
