-- CinéTrack schema – Chapter 2
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE app_users (
    id            BIGSERIAL     PRIMARY KEY,
    username      VARCHAR(100)  NOT NULL UNIQUE,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE movies (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(500) NOT NULL,
    genre               VARCHAR(50)  NOT NULL,
    release_year        SMALLINT     NOT NULL,
    rating              NUMERIC(3,1),
    metadata            JSONB,
    streaming_platforms TEXT[],
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_movies_genre        ON movies (genre);
CREATE INDEX idx_movies_release_year ON movies (release_year);
CREATE INDEX idx_movies_metadata     ON movies USING GIN (metadata);

CREATE TABLE reviews (
    id          BIGSERIAL   PRIMARY KEY,
    movie_id    UUID        NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    reviewer_id BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    content     TEXT        NOT NULL,
    rating      SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 10),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_reviews_movie    ON reviews (movie_id);
CREATE INDEX idx_reviews_reviewer ON reviews (reviewer_id);

CREATE TABLE watchlists (
    id         BIGSERIAL    PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE watchlist_entries (
    id           BIGSERIAL   PRIMARY KEY,
    watchlist_id BIGINT      NOT NULL REFERENCES watchlists (id) ON DELETE CASCADE,
    movie_id     UUID        NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (watchlist_id, movie_id)
);

CREATE TABLE watch_logs (
    id                     BIGSERIAL   PRIMARY KEY,
    user_id                BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    movie_id               UUID        NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    watched_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    watch_duration_minutes INT
);

CREATE INDEX idx_watch_logs_user  ON watch_logs (user_id);
CREATE INDEX idx_watch_logs_movie ON watch_logs (movie_id);

CREATE TABLE subscriptions (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    tier       VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_subscriptions_user ON subscriptions (user_id);
