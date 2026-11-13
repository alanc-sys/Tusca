#!/bin/sh
set -e

create_isolated_db() {
  db_name="$1"
  db_user="$2"
  db_pass="$3"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER ${db_user} WITH PASSWORD '${db_pass}';
    CREATE DATABASE ${db_name} OWNER ${db_user};
    REVOKE ALL ON DATABASE ${db_name} FROM PUBLIC;
    GRANT ALL PRIVILEGES ON DATABASE ${db_name} TO ${db_user};
EOSQL
}

create_isolated_db "keycloak_db" "${KEYCLOAK_DB_USER}" "${KEYCLOAK_DB_PASSWORD}"
create_isolated_db "poi_db" "${POI_DB_USER}" "${POI_DB_PASSWORD}"
create_isolated_db "itinerary_db" "${ITINERARY_DB_USER}" "${ITINERARY_DB_PASSWORD}"
create_isolated_db "match_db" "${MATCH_DB_USER}" "${MATCH_DB_PASSWORD}"
