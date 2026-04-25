CREATE TABLE app_users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email    VARCHAR(255)
);

CREATE TABLE movies (
    id       BIGSERIAL PRIMARY KEY,
    title    VARCHAR(255) NOT NULL,
    tags     TEXT[],
    metadata JSONB
);

CREATE TABLE subscriptions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT REFERENCES app_users(id),
    tier         VARCHAR(20),
    -- Money stored as a JSONB blob: {"cents":1999,"currency":"USD"}
    price        JSONB        NOT NULL,
    metadata     JSONB,
    start_date   DATE,
    end_date     DATE
);
