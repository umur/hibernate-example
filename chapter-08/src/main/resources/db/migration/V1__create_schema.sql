CREATE SEQUENCE movie_seq START 1 INCREMENT 50;

CREATE TABLE movies (
    id BIGINT DEFAULT NEXTVAL('movie_seq') PRIMARY KEY,
    imdb_id VARCHAR(20) UNIQUE,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(50)
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
);

CREATE TABLE watchlists (
    id BIGSERIAL PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    name VARCHAR(255) NOT NULL
);

CREATE TABLE watchlist_entries (
    watchlist_id BIGINT NOT NULL REFERENCES watchlists(id),
    movie_id BIGINT NOT NULL REFERENCES movies(id),
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (watchlist_id, movie_id)
);
