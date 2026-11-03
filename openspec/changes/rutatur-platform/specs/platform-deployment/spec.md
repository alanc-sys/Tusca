# Platform Deployment Specification

**Capability**: `platform-deployment`

## Purpose

Defines the runnable platform: reactor build, Compose stack, Keycloak realm, schemas, health-gated startup, and the smoke path; satisfied only when it starts healthy from one command.

## Requirements

### Requirement: Reactor build is the canonical build

`mvn -B verify` from the repository root MUST build and test every module through the reactor. Build instructions MUST NOT require a manual shared-module `mvn install` step.

#### Scenario: Clean reactor build

- GIVEN a clean checkout with the target JDK
- WHEN `mvn -B verify` runs
- THEN every module MUST compile and its tests MUST pass

### Requirement: Single-command stack startup

`docker compose up -d` MUST start PostgreSQL, Keycloak, the gateway, and all three functional services, each reaching healthy state. pgAdmin MAY be an optional profile.

#### Scenario: All containers healthy

- GIVEN a running Docker daemon
- WHEN `docker compose up -d` completes
- THEN every required container MUST report healthy
- AND `route-match-service` MUST exist as its own container

### Requirement: Published ports

The stack MUST expose gateway 8080, `poi-service` 8081, `itinerary-service` 8082, `route-match-service` 8083, PostgreSQL 5432, and Keycloak on host 8180 mapping container 8080.

#### Scenario: Port mapping

- GIVEN the running stack
- WHEN ports are inspected
- THEN Keycloak MUST answer on host 8180 and the gateway MUST own host 8080

### Requirement: Realm, clients, roles, and users

The imported realm `rutatur-realm` MUST define clients `rutatur-gateway` (confidential) and `rutatur-api`, roles `ROLE_USER` and `ROLE_ADMIN`, and development users `user1` and `admin1`. Tokens MUST validate against the in-network issuer.

#### Scenario: Role-bearing tokens issued

- GIVEN `user1` and `admin1` credentials
- WHEN each requests a token
- THEN the tokens MUST carry `ROLE_USER` and `ROLE_ADMIN` respectively
- AND the gateway MUST accept them

### Requirement: Schema-per-service initialization

Startup MUST create `poi_schema`, `itinerary_schema`, and `match_schema`, each owned by its service, and no service MUST read or write another service's schema.

#### Scenario: Schemas exist after startup

- GIVEN a fresh stack with an empty volume
- WHEN initialization completes
- THEN all three schemas MUST exist with only their owner's tables
- AND any service write MUST target only its own schema

### Requirement: Health-gated startup ordering

Every service MUST expose a health endpoint, and Compose MUST gate dependents on database and identity-provider health so no service starts against an unready dependency.

#### Scenario: Dependency ordering

- GIVEN a cold start
- WHEN PostgreSQL and Keycloak are not yet healthy
- THEN dependent services MUST wait for them

### Requirement: Restart safety and persistence

Restarting MUST NOT duplicate realm data or schema initialization, and MUST preserve persisted itineraries, shares, and matches through named volumes.

#### Scenario: Second startup is clean

- GIVEN a stack started once before
- WHEN `docker compose up -d` runs again
- THEN the stack MUST reach healthy without duplicate initialization errors
- AND previously saved data MUST remain readable

### Requirement: External configuration

Service URLs, database credentials, and issuer settings MUST come from environment variables, and images MUST NOT embed secrets.

#### Scenario: Overridden configuration

- GIVEN a changed upstream URL variable
- WHEN the service starts
- THEN it MUST use the supplied value
- AND no credential constant MAY be baked into the image

### Requirement: End-to-end smoke path

The stack MUST support the documented flow: obtain a token, generate an itinerary, save it, share it, receive a suggestion at or above the threshold, request a match, accept mutually, and reveal contact — with 403 before acceptance.

#### Scenario: Full flow succeeds

- GIVEN a healthy stack and two users with similar shared routes
- WHEN the flow runs end to end
- THEN a suggestion with `match_score >= 0.35` MUST appear
- AND contact MUST be returned only after both accept

#### Scenario: Pre-acceptance contact blocked

- GIVEN a `PENDING` match
- WHEN a participant requests contact
- THEN the response MUST be 403
