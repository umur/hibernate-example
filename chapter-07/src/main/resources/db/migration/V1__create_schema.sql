-- Sequence with INCREMENT BY 50 to match @SequenceGenerator(allocationSize = 50)
-- on MediaItem. Hibernate reserves 50 IDs per round-trip to reduce contention.
CREATE SEQUENCE media_items_id_seq INCREMENT BY 50;
CREATE TABLE media_items (
    id BIGINT PRIMARY KEY DEFAULT nextval('media_items_id_seq'),
    dtype VARCHAR(31) NOT NULL,
    title VARCHAR(255) NOT NULL,
    release_year INT,
    genre VARCHAR(50),
    -- Movie-specific
    runtime_minutes INT,
    -- Series-specific
    total_seasons INT,
    -- Episode-specific
    season_number INT,
    episode_number INT,
    series_id BIGINT REFERENCES media_items(id),
    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100)
);
ALTER SEQUENCE media_items_id_seq OWNED BY media_items.id;

CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
);
