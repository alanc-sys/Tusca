-- 01-poi-schema.sql — poi_schema (owner rutatur, runtime role rutatur_poi).

CREATE SCHEMA IF NOT EXISTS poi_schema AUTHORIZATION rutatur;
SET search_path TO poi_schema;

CREATE TABLE IF NOT EXISTS poi_cache (
  query_key   varchar(64)  NOT NULL,
  external_id varchar(64)  NOT NULL,
  name        text         NOT NULL,
  category    varchar(32)  NOT NULL,
  lat         numeric(9,6) NOT NULL,
  lng         numeric(9,6) NOT NULL,
  visit_time  integer      NOT NULL CHECK (visit_time > 0),
  rating      numeric(2,1) NOT NULL DEFAULT 0,
  address     text,
  tags        jsonb        NOT NULL DEFAULT '[]'::jsonb,
  cached_at   timestamptz  NOT NULL DEFAULT now(),
  expires_at  timestamptz  NOT NULL,
  PRIMARY KEY (query_key, external_id)
);

CREATE INDEX IF NOT EXISTS idx_poi_cache_lookup ON poi_cache (query_key, expires_at);

GRANT USAGE ON SCHEMA poi_schema TO rutatur_poi;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA poi_schema TO rutatur_poi;
