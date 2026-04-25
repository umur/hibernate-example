-- Chapter 10: JPQL, Native Queries, and @Query
-- Schema for the CineTrack application

CREATE TABLE app_users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(100) NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE movies (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    genre        VARCHAR(50)  NOT NULL,
    release_year INT          NOT NULL,
    rating       NUMERIC(3, 1),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_movies_genre ON movies (genre);

CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    movie_id    BIGINT       NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    reviewer_id BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    rating      INT          NOT NULL CHECK (rating BETWEEN 1 AND 10),
    content     TEXT,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (movie_id, reviewer_id)
);

CREATE INDEX idx_reviews_movie_id    ON reviews (movie_id);
CREATE INDEX idx_reviews_reviewer_id ON reviews (reviewer_id);

CREATE TABLE watch_logs (
    id               BIGSERIAL PRIMARY KEY,
    movie_id         BIGINT      NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    watched_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_seconds INT         NOT NULL CHECK (duration_seconds > 0)
);

CREATE INDEX idx_watch_logs_user_id  ON watch_logs (user_id);
CREATE INDEX idx_watch_logs_movie_id ON watch_logs (movie_id);

CREATE TABLE watchlists (
    id         BIGSERIAL PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE watchlist_entries (
    id           BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT      NOT NULL REFERENCES watchlists (id) ON DELETE CASCADE,
    movie_id     BIGINT      NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (watchlist_id, movie_id)
);
