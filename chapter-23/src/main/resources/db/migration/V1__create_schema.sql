CREATE TABLE app_users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE movies (
    id     BIGSERIAL PRIMARY KEY,
    title  VARCHAR(255) NOT NULL,
    genre  VARCHAR(50),
    rating DOUBLE PRECISION
);

CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    movie_id    BIGINT REFERENCES movies(id),
    reviewer_id BIGINT REFERENCES app_users(id),
    rating      INT,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE watch_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES app_users(id),
    movie_id   BIGINT REFERENCES movies(id),
    watched_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable trigram extension for similarity() function used in chapter examples
CREATE EXTENSION IF NOT EXISTS pg_trgm;
