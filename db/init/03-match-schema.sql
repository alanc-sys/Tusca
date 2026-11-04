-- 03-match-schema.sql — match_schema (owner rutatur, runtime role rutatur_match).

CREATE SCHEMA IF NOT EXISTS match_schema AUTHORIZATION rutatur;
SET search_path TO match_schema;

CREATE TABLE IF NOT EXISTS shared_route (
  id              uuid         PRIMARY KEY,
  itinerary_id    uuid         NOT NULL,
  owner_user_id   varchar(255) NOT NULL,
  start_lat       numeric(9,6) NOT NULL,
  start_lng       numeric(9,6) NOT NULL,
  available_time  integer      NOT NULL CHECK (available_time BETWEEN 30 AND 480),
  transport_mode  varchar(16)  NOT NULL,
  total_time      integer      NOT NULL CHECK (total_time >= 0),
  poi_ids         jsonb        NOT NULL,
  categories      jsonb        NOT NULL,
  is_active       boolean      NOT NULL DEFAULT true,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  expires_at      timestamptz  NOT NULL,
  CONSTRAINT uk_shared_route_itinerary UNIQUE (itinerary_id)
);

CREATE INDEX IF NOT EXISTS idx_shared_route_pool ON shared_route (is_active, expires_at);
CREATE INDEX IF NOT EXISTS idx_shared_route_owner ON shared_route (owner_user_id);

CREATE TABLE IF NOT EXISTS route_match (
  id                      uuid         PRIMARY KEY,
  requester_user_id       varchar(255) NOT NULL,
  responder_user_id       varchar(255) NOT NULL,
  shared_route_a_id       uuid         NOT NULL REFERENCES shared_route (id),
  shared_route_b_id       uuid         NOT NULL REFERENCES shared_route (id),
  match_score             numeric(5,4) NOT NULL CHECK (match_score BETWEEN 0 AND 1),
  matched_poi_count       integer      NOT NULL CHECK (matched_poi_count >= 0),
  matched_pois            jsonb        NOT NULL,
  status                  varchar(16)  NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
  requester_accepted_at   timestamptz,
  responder_accepted_at   timestamptz,
  requested_at            timestamptz  NOT NULL DEFAULT now(),
  responded_at            timestamptz,
  accepted_at             timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_route_match_open
  ON route_match (requester_user_id, shared_route_b_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_route_match_requester ON route_match (requester_user_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_route_match_responder ON route_match (responder_user_id, requested_at DESC);

CREATE TABLE IF NOT EXISTS user_contact_view (
  id             uuid         PRIMARY KEY,
  route_match_id uuid         NOT NULL REFERENCES route_match (id),
  viewer_user_id varchar(255) NOT NULL,
  viewed_user_id varchar(255) NOT NULL,
  revealed_at    timestamptz  NOT NULL DEFAULT now(),
  contact_info   jsonb        NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_contact_view_match ON user_contact_view (route_match_id);

GRANT USAGE ON SCHEMA match_schema TO rutatur_match;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA match_schema TO rutatur_match;
