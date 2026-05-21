-- Envers revision information table (custom @RevisionEntity).
-- CineTrackRevision overrides RevisionMapping's `id`/`timestamp` fields to the
-- legacy `rev` / `revtstmp` column names via @AttributeOverrides, so the DDL
-- must match.
CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo (
    rev        INTEGER PRIMARY KEY,
    revtstmp   BIGINT NOT NULL,
    username   VARCHAR(255),
    ip_address VARCHAR(100)
);

-- Envers audit tables for @Audited entities
CREATE TABLE movies_aud (
    id           BIGINT  NOT NULL,
    rev          INTEGER NOT NULL REFERENCES revinfo(rev),
    revtype      SMALLINT,
    title        VARCHAR(255),
    genre        VARCHAR(50),
    rating       NUMERIC(3,1),
    created_at   TIMESTAMPTZ,
    created_by   VARCHAR(100),
    updated_at   TIMESTAMPTZ,
    updated_by   VARCHAR(100),
    PRIMARY KEY (id, rev)
);

CREATE TABLE reviews_aud (
    id          BIGINT  NOT NULL,
    rev         INTEGER NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    movie_id    BIGINT,
    reviewer_id BIGINT,
    content     TEXT,
    rating      INT,
    version     BIGINT,
    created_at  TIMESTAMPTZ,
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(100),
    PRIMARY KEY (id, rev)
);
