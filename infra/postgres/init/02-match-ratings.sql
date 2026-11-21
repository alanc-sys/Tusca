-- W1: tablas de perfiles y calificaciones de match_db.
-- Se ejecuta después de 01-init-databases.sh, que ya creó match_db y su usuario owner.
\connect match_db

SET ROLE match_user;

CREATE TABLE IF NOT EXISTS user_profile (
    user_id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED')),
    average_rating NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    ratings_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS match_rating (
    id UUID PRIMARY KEY,
    -- route_matches la crea Hibernate (ddl-auto=update) al arrancar route-match-service,
    -- por lo que la FK se declara en MatchRatingEntity y Hibernate la agrega después.
    match_id UUID NOT NULL,
    rater_user_id UUID NOT NULL REFERENCES user_profile(user_id),
    rated_user_id UUID NOT NULL REFERENCES user_profile(user_id),
    score INT NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_match_rating_rater UNIQUE (match_id, rater_user_id)
);

CREATE INDEX IF NOT EXISTS idx_match_rating_match_id ON match_rating(match_id);
CREATE INDEX IF NOT EXISTS idx_match_rating_rated_user_id ON match_rating(rated_user_id);

RESET ROLE;
