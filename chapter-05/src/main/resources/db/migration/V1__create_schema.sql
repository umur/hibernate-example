-- Chapter 5: Entity Mapping Deep Dive — schema

-- ── movies ───────────────────────────────────────────────────────────────────
-- created_at is set by the DB default; insertable=false,updatable=false in entity.
-- review_count and average_rating are derived via @Formula — no stored column needed.
CREATE TABLE movies (
    id           BIGSERIAL    NOT NULL,
    title        VARCHAR(255) NOT NULL,
    genre        VARCHAR(50)  NOT NULL,
    release_year INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_movies PRIMARY KEY (id)
);

-- ── app_users ────────────────────────────────────────────────────────────────
-- email stored as plain VARCHAR; EmailAddressConverter converts EmailAddress<->String.
-- subscription_tier kept for completeness (used in subscriptions FK).
CREATE TABLE app_users (
    id              BIGSERIAL    NOT NULL,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_app_users PRIMARY KEY (id)
);

-- ── reviews ──────────────────────────────────────────────────────────────────
CREATE TABLE reviews (
    id          BIGSERIAL    NOT NULL,
    movie_id    BIGINT       NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    reviewer_id BIGINT       NOT NULL REFERENCES app_users(id),
    content     TEXT,
    rating      NUMERIC(3,1) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_reviews       PRIMARY KEY (id),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 10)
);

-- ── subscriptions ────────────────────────────────────────────────────────────
-- Money @Embeddable maps to amount_cents + currency on this table directly.
CREATE TABLE subscriptions (
    id           BIGSERIAL    NOT NULL,
    user_id      BIGINT       NOT NULL REFERENCES app_users(id),
    tier         VARCHAR(50)  NOT NULL,
    amount_cents INT          NOT NULL,
    currency     VARCHAR(3)   NOT NULL,
    start_date   TIMESTAMPTZ  NOT NULL,
    end_date     TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id)
);

-- ── watch_logs ───────────────────────────────────────────────────────────────
-- Immutable: rows are never updated after insert.
CREATE TABLE watch_logs (
    id               BIGSERIAL NOT NULL,
    user_id          BIGINT    NOT NULL REFERENCES app_users(id),
    movie_id         BIGINT    NOT NULL REFERENCES movies(id),
    watched_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_minutes INT       NOT NULL,
    CONSTRAINT pk_watch_logs PRIMARY KEY (id)
);
