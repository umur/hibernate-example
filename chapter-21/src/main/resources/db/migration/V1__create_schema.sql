CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255)
);

CREATE TABLE movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(50),
    rating NUMERIC(3,1),
    created_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100)
);

CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT REFERENCES movies(id),
    reviewer_id BIGINT REFERENCES app_users(id),
    content TEXT,
    rating INT,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100)
);
