CREATE TABLE app_users (
    id        BIGSERIAL PRIMARY KEY,
    username  VARCHAR(100) UNIQUE NOT NULL,
    email     VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE movies (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    genre        VARCHAR(50),
    release_year INT,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    movie_id    BIGINT NOT NULL REFERENCES movies(id),
    reviewer_id BIGINT NOT NULL REFERENCES app_users(id),
    rating      INT    NOT NULL CHECK (rating BETWEEN 1 AND 10),
    body        TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE watchlists (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    name    VARCHAR(255) NOT NULL
);

CREATE TABLE watchlist_entries (
    id           BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL REFERENCES watchlists(id),
    movie_id     BIGINT NOT NULL REFERENCES movies(id),
    added_at     TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (watchlist_id, movie_id)
);
