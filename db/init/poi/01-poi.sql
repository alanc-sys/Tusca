-- poi_db — dedicated database for poi-service (container postgres-poi).
--
-- There is exactly one service per database, so the tables live in the default `public`
-- schema: no schema indirection, no cross-service grants. The container user `poi_user`
-- owns the database and therefore the tables.
--
-- Executed once by the postgres entrypoint when the volume is empty.

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

-- One row per query key: records how many POIs a query resolved to, including zero,
-- so an empty result is cached instead of calling Overpass on every request.
CREATE TABLE IF NOT EXISTS poi_query_cache (
  query_key    varchar(64) PRIMARY KEY,
  result_count integer     NOT NULL CHECK (result_count >= 0),
  cached_at    timestamptz NOT NULL DEFAULT now(),
  expires_at   timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_poi_query_cache_expiry ON poi_query_cache (expires_at);
