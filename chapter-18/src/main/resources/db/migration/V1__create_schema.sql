CREATE TABLE genres (
    id    BIGSERIAL PRIMARY KEY,
    code  VARCHAR(50)  UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL
);

CREATE TABLE app_users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email    VARCHAR(255)
);

CREATE TABLE movies (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    genre        VARCHAR(50),
    release_year INT,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Seed genre reference data
INSERT INTO genres (code, label) VALUES
    ('ACTION',          'Action'),
    ('COMEDY',          'Comedy'),
    ('DRAMA',           'Drama'),
    ('HORROR',          'Horror'),
    ('SCIENCE_FICTION', 'Science Fiction'),
    ('THRILLER',        'Thriller'),
    ('DOCUMENTARY',     'Documentary');
