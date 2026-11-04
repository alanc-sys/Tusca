-- 00-roles.sql — runs once on an empty volume as POSTGRES_USER=rutatur (superuser).
--
-- Idempotent on purpose: PostgreSQL has no `CREATE ROLE IF NOT EXISTS`, so each statement is
-- generated only when the role is missing. `\gexec` executes the generated SQL. `CREATE DATABASE`
-- cannot run inside a transaction or a DO block, which is why it also uses `\gexec`.
--
-- The role passwords are development fixtures and match the datasource settings in
-- docker-compose.yml. They are not production secrets.

SELECT 'CREATE ROLE rutatur_poi LOGIN PASSWORD ''rutatur_poi'''
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rutatur_poi')\gexec

SELECT 'CREATE ROLE rutatur_itinerary LOGIN PASSWORD ''rutatur_itinerary'''
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rutatur_itinerary')\gexec

SELECT 'CREATE ROLE rutatur_match LOGIN PASSWORD ''rutatur_match'''
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rutatur_match')\gexec

SELECT 'CREATE ROLE rutatur_kc LOGIN PASSWORD ''rutatur_kc'''
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rutatur_kc')\gexec

SELECT 'CREATE DATABASE keycloak OWNER rutatur_kc'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'keycloak')\gexec
