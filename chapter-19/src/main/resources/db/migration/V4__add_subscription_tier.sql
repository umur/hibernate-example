-- Expand-contract pattern: add nullable column first, backfill, then constrain
ALTER TABLE app_users ADD COLUMN subscription_tier VARCHAR(20);
UPDATE app_users SET subscription_tier = 'FREE' WHERE subscription_tier IS NULL;
ALTER TABLE app_users ALTER COLUMN subscription_tier SET NOT NULL;
ALTER TABLE app_users ALTER COLUMN subscription_tier SET DEFAULT 'FREE';
