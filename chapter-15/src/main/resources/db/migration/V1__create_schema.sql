CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255)
);

CREATE TABLE movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(50),
    rating NUMERIC(3,1)
);

CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT REFERENCES movies(id),
    reviewer_id BIGINT REFERENCES app_users(id),
    content TEXT,
    rating INT CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE watchlists (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT REFERENCES app_users(id),
    name VARCHAR(255) NOT NULL
);

CREATE TABLE watchlist_entries (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT REFERENCES watchlists(id),
    movie_id BIGINT REFERENCES movies(id),
    added_at TIMESTAMPTZ DEFAULT NOW()
);
