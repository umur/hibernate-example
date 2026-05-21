CREATE TABLE IF NOT EXISTS movies (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    release_year INT,
    genre        VARCHAR(100) NOT NULL,
    rating       NUMERIC(3, 1),
    overview     TEXT
);
