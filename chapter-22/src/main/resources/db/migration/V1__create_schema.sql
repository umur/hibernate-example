CREATE TABLE app_users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email    VARCHAR(255)
);

CREATE TABLE movies (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    genre          VARCHAR(50),
    content_rating VARCHAR(10) DEFAULT 'PG',
    deleted        BOOLEAN     DEFAULT FALSE,
    deleted_at     TIMESTAMPTZ
);

CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    movie_id    BIGINT REFERENCES movies(id),
    reviewer_id BIGINT REFERENCES app_users(id),
    content     TEXT,
    rating      INT,
    deleted     BOOLEAN DEFAULT FALSE
);
