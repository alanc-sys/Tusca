-- itinerary_db — dedicated database for itinerary-service (container postgres-itinerary).
--
-- One service per database, so the tables live in the default `public` schema. The container
-- user `itinerary_user` owns the database and therefore the tables.
--
-- Executed once by the postgres entrypoint when the volume is empty.

CREATE TABLE IF NOT EXISTS itinerary_draft (
  id            uuid        PRIMARY KEY,
  request_data  jsonb       NOT NULL,
  response_data jsonb       NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_itinerary_draft_created ON itinerary_draft (created_at);

CREATE TABLE IF NOT EXISTS saved_itinerary (
  id              uuid         PRIMARY KEY,
  itinerary_id    uuid         NOT NULL,
  user_id         varchar(255) NOT NULL,
  start_lat       numeric(9,6) NOT NULL,
  start_lng       numeric(9,6) NOT NULL,
  available_time  integer      NOT NULL CHECK (available_time BETWEEN 30 AND 480),
  transport_mode  varchar(16)  NOT NULL CHECK (transport_mode IN ('WALKING','DRIVING')),
  total_time      integer      NOT NULL CHECK (total_time >= 0),
  route_summary   text         NOT NULL,
  request_data    jsonb        NOT NULL,
  response_data   jsonb        NOT NULL,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  updated_at      timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uk_saved_itinerary_itinerary UNIQUE (itinerary_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_itinerary_owner ON saved_itinerary (user_id, created_at DESC);
