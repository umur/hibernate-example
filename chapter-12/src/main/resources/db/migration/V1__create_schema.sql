-- Chapter 12: Projections, DTOs, and Result Mapping

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
    rating       NUMERIC(3, 1)
);

CREATE INDEX idx_movies_genre ON movies (genre);

CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    movie_id    BIGINT NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    reviewer_id BIGINT NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    content     TEXT,
    rating      INT    NOT NULL CHECK (rating BETWEEN 1 AND 10),
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (movie_id, reviewer_id)
);

CREATE INDEX idx_reviews_movie_id ON reviews (movie_id);

CREATE TABLE watch_logs (
    id               BIGSERIAL PRIMARY KEY,
    movie_id         BIGINT      NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    watched_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_seconds INT         NOT NULL CHECK (duration_seconds > 0)
);
