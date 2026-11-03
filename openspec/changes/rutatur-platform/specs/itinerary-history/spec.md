# Itinerary History Specification

**Capability**: `itinerary-history`

## Purpose

Provides authenticated persistence and retrieval of generated itineraries: save, list, fetch, and delete, scoped to the caller's identity and confined to the itinerary service's own schema.

## Requirements

### Requirement: Save a generated itinerary

`POST /api/itineraries` MUST require `ROLE_USER` and accept `{"itineraryId": "<id>"}`, responding 201 with `id`, `userId`, `itineraryId`, and `createdAt`. It MUST store the generation request and response so later reads do not depend on external services.

#### Scenario: Successful save

- GIVEN an authenticated user and an existing generated itinerary
- WHEN the save endpoint is called
- THEN the response MUST be 201 with `userId` equal to the JWT subject

#### Scenario: Unknown itinerary

- GIVEN an `itineraryId` that does not exist
- WHEN the user attempts to save it
- THEN the response MUST be 404
- AND no record MUST be persisted

#### Scenario: Missing token

- GIVEN a request without `Authorization`
- WHEN the save endpoint is called
- THEN the response MUST be 401

### Requirement: List the caller's itineraries

`GET /api/itineraries` MUST require `ROLE_USER` and MUST return only the caller's records, each with `id`, `itineraryId`, `startPoint`, `totalTime`, `routeSummary`, and `createdAt`, ordered by `createdAt` descending.

#### Scenario: Only own records

- GIVEN user A owns two records and user B owns one
- WHEN A lists itineraries
- THEN exactly two records MUST be returned and none MAY belong to B

#### Scenario: Empty history

- GIVEN no saved itineraries
- WHEN the list endpoint is called
- THEN the response MUST be 200 with an empty array

### Requirement: Fetch and delete are owner-scoped

`GET /api/itineraries/{id}` and `DELETE /api/itineraries/{id}` MUST require `ROLE_USER`. Fetch MUST return the stored route and totals; delete MUST respond 204 and remove the record. An unknown or non-owned identifier MUST produce 404.

#### Scenario: Owner reads and deletes

- GIVEN a saved itinerary owned by the caller
- WHEN it is fetched and then deleted
- THEN fetch MUST return 200 with the stored route
- AND delete MUST return 204 and the record MUST leave the list

#### Scenario: Foreign record not disclosed

- GIVEN a record owned by another user
- WHEN the caller fetches or deletes it
- THEN the response MUST be 404

#### Scenario: Repeated delete

- GIVEN an already deleted record
- WHEN the same delete is retried
- THEN the response MUST be 404

### Requirement: Identity is derived from the token

The owner MUST be taken from the JWT subject. A `userId` supplied in the body or query MUST NOT be trusted for ownership.

#### Scenario: Body identity ignored

- GIVEN a body naming a different `userId`
- WHEN the itinerary is saved
- THEN the stored owner MUST be the JWT subject

### Requirement: Internal detail endpoint for matching

`GET /internal/itineraries/{id}` MUST return the fields required by `route-match-service` — POI identifiers, start point, preferences, available time, totals — without an end-user token. Unknown identifiers MUST produce 404, and the path MUST NOT be reachable through the gateway.

#### Scenario: Matching lookup

- GIVEN a saved itinerary
- WHEN the matching service calls the internal endpoint
- THEN the response MUST be 200 with the matching-relevant fields

#### Scenario: Internal path not public

- GIVEN a client requests the internal path through the gateway
- WHEN it is routed
- THEN it MUST NOT reach the itinerary service

### Requirement: Schema ownership and read availability

The service MUST read and write only its own schema, and listing or fetching MUST succeed while `poi-service` or the routing engine is unavailable.

#### Scenario: Offline read

- GIVEN saved itineraries and an unreachable POI provider
- WHEN the user lists saved itineraries
- THEN the response MUST be 200
- AND only the itinerary schema MUST be read
