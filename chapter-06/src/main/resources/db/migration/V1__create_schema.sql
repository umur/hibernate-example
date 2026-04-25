-- Chapter 6: Associations — Every Variant

-- ── movies ───────────────────────────────────────────────────────────────────
CREATE SEQUENCE movies_id_seq INCREMENT BY 50;
CREATE TABLE movies (
    id           BIGINT       NOT NULL DEFAULT nextval('movies_id_seq'),
    title        VARCHAR(255) NOT NULL,
    genre        VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_movies PRIMARY KEY (id)
);
ALTER SEQUENCE movies_id_seq OWNED BY movies.id;

-- @ElementCollection: stored in a separate collection table, no entity lifecycle.
CREATE TABLE movie_genres (
    movie_id BIGINT      NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    tags     VARCHAR(100) NOT NULL,
    CONSTRAINT pk_movie_genres PRIMARY KEY (movie_id, tags)
);

-- ── app_users ────────────────────────────────────────────────────────────────
CREATE TABLE app_users (
    id       BIGSERIAL    NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    email    VARCHAR(255) NOT NULL UNIQUE,
    CONSTRAINT pk_app_users PRIMARY KEY (id)
);

-- ── user_profiles ─────────────────────────────────────────────────────────────
-- Shared-PK OneToOne: user_profiles.id is both PK and FK to app_users.id.
-- No surrogate key — the profile row lives and dies with the user row.
CREATE TABLE user_profiles (
    id         BIGINT       NOT NULL,
    bio        TEXT,
    avatar_url VARCHAR(512),
    CONSTRAINT pk_user_profiles  PRIMARY KEY (id),
    CONSTRAINT fk_profile_user   FOREIGN KEY (id) REFERENCES app_users(id) ON DELETE CASCADE
);

-- ── reviews ──────────────────────────────────────────────────────────────────
-- Bidirectional @ManyToOne / @OneToMany with orphanRemoval on Movie side.
CREATE TABLE reviews (
    id          BIGSERIAL    NOT NULL,
    movie_id    BIGINT       NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    reviewer_id BIGINT       NOT NULL REFERENCES app_users(id),
    content     TEXT,
    rating      INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_reviews        PRIMARY KEY (id),
    CONSTRAINT chk_rating        CHECK (rating BETWEEN 1 AND 5)
);

-- ── watchlists ───────────────────────────────────────────────────────────────
CREATE TABLE watchlists (
    id       BIGSERIAL    NOT NULL,
    owner_id BIGINT       NOT NULL REFERENCES app_users(id),
    name     VARCHAR(255) NOT NULL,
    CONSTRAINT pk_watchlists PRIMARY KEY (id)
);

-- ── watchlist_entries ─────────────────────────────────────────────────────────
-- Intermediate entity for @ManyToMany with extra columns (added_at, notes).
-- Composite PK: (watchlist_id, movie_id).
CREATE TABLE watchlist_entries (
    watchlist_id BIGINT       NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    movie_id     BIGINT       NOT NULL REFERENCES movies(id)     ON DELETE CASCADE,
    added_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    notes        VARCHAR(500),
    CONSTRAINT pk_watchlist_entries PRIMARY KEY (watchlist_id, movie_id)
);

-- ── watch_logs ───────────────────────────────────────────────────────────────
CREATE TABLE watch_logs (
    id               BIGSERIAL   NOT NULL,
    user_id          BIGINT      NOT NULL REFERENCES app_users(id),
    movie_id         BIGINT      NOT NULL REFERENCES movies(id),
    watched_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_minutes INT         NOT NULL,
    CONSTRAINT pk_watch_logs PRIMARY KEY (id)
);
