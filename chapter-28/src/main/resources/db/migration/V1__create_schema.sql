CREATE SEQUENCE IF NOT EXISTS movie_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS app_user_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS review_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS movies (
    id           BIGINT PRIMARY KEY DEFAULT nextval('movie_seq'),
    title        VARCHAR(255) NOT NULL,
    release_year INT,
    genre        VARCHAR(100) NOT NULL,
    rating       NUMERIC(3, 1),
    overview     TEXT,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGINT PRIMARY KEY DEFAULT nextval('app_user_seq'),
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS reviews (
    id         BIGINT PRIMARY KEY DEFAULT nextval('review_seq'),
    movie_id   BIGINT    NOT NULL REFERENCES movies (id),
    content    TEXT,
    stars      INT       NOT NULL CHECK (stars BETWEEN 1 AND 5),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    version    BIGINT    NOT NULL DEFAULT 0
);

-- Hibernate Envers audit tables. Hibernate 7's default RevisionMapping uses a
-- sequence-based generator (revinfo_seq, allocationSize 50) rather than the
-- legacy SERIAL/IDENTITY column.
CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS revinfo (
    rev      INTEGER PRIMARY KEY,
    revtstmp BIGINT
);

CREATE TABLE IF NOT EXISTS movies_aud (
    id           BIGINT,
    rev          INT    NOT NULL REFERENCES revinfo (rev),
    revtype      SMALLINT,
    title        VARCHAR(255),
    release_year INT,
    genre        VARCHAR(100),
    rating       NUMERIC(3, 1),
    overview     TEXT,
    deleted      BOOLEAN,
    PRIMARY KEY (id, rev)
);

CREATE TABLE IF NOT EXISTS reviews_aud (
    id         BIGINT,
    rev        INT NOT NULL REFERENCES revinfo (rev),
    revtype    SMALLINT,
    movie_id   BIGINT,
    content    TEXT,
    stars      INT,
    created_at TIMESTAMP,
    version    BIGINT,
    PRIMARY KEY (id, rev)
);
