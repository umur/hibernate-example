CREATE TABLE app_users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email    VARCHAR(255)
);

CREATE TABLE movies (
    id    BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(50)
);

-- Sequence with an INCREMENT matching hibernate.jdbc.batch_size so that
-- Hibernate can pre-allocate IDs in the same batch size without hitting the
-- sequence once per row.
CREATE SEQUENCE watch_log_seq START 1 INCREMENT 50;

CREATE TABLE watch_logs (
    id               BIGINT DEFAULT NEXTVAL('watch_log_seq') PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES app_users(id),
    movie_id         BIGINT      NOT NULL REFERENCES movies(id),
    watched_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    duration_seconds INT
);
