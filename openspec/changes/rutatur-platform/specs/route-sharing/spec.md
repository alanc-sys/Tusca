# Route Sharing Specification

**Capability**: `route-sharing`

## Purpose

Publishes an owned itinerary into the matching pool only when the user explicitly opts in. Sharing is per-itinerary, active-only, and never exposes contact data.

## Requirements

### Requirement: Sharing is opt-in (RN-09)

`POST /api/routes/share` MUST require `ROLE_USER` and accept `{"itineraryId": "<id>"}`. Only itineraries explicitly shared through this endpoint MAY participate in matching; generating, saving, or listing an itinerary MUST NOT publish it.

#### Scenario: Explicit share publishes

- GIVEN an authenticated user and a saved, never-shared itinerary
- WHEN the share endpoint is called
- THEN an active shared route MUST be created
- AND the response MUST identify the share and its active state

#### Scenario: Unshared itinerary stays invisible

- GIVEN a saved itinerary with no share record
- WHEN another user requests suggestions
- THEN that itinerary MUST NOT be a candidate

### Requirement: Share requires ownership

The caller MUST own the itinerary. An unknown itinerary or one owned by another user MUST produce 404 with no share created.

#### Scenario: Foreign itinerary

- GIVEN an itinerary owned by another user
- WHEN the caller attempts to share it
- THEN the response MUST be 404
- AND no shared route MUST be persisted

#### Scenario: Unauthenticated share

- GIVEN a request without a token
- WHEN the share endpoint is called
- THEN the response MUST be 401

### Requirement: Share builds on itinerary detail

`route-match-service` MUST read the itinerary through `GET /internal/itineraries/{id}`. A 404 from that call MUST surface as 404; unavailability MUST surface as 503 without leaving an active share behind.

#### Scenario: Snapshot retrieved

- GIVEN an existing itinerary
- WHEN a share is created
- THEN POI identifiers, start point, preferences, and available time MUST be persisted

#### Scenario: Itinerary service unavailable

- GIVEN the itinerary service times out during share
- WHEN the share endpoint is called
- THEN the response MUST be 503
- AND no partial share MUST be left active

### Requirement: Share is idempotent per itinerary

Sharing an itinerary that already has an active share MUST NOT create a second active record; the existing share MUST be returned.

#### Scenario: Repeated share

- GIVEN an itinerary already actively shared by the caller
- WHEN it is shared again
- THEN exactly one active share MUST exist for that itinerary

### Requirement: Only active shares are matchable

A shared route MUST be excluded from matching once inactive or past its expiry instant.

#### Scenario: Expired share excluded

- GIVEN a share whose expiry has passed
- WHEN similarity is computed
- THEN that route MUST NOT appear in any suggestion

### Requirement: Shareable listing

`GET /api/routes/shareable` MUST require `ROLE_USER` and MUST return only the caller's itineraries with their share state, so published routes are distinguishable.

#### Scenario: Share state listed

- GIVEN one shared and one unshared itinerary owned by the caller
- WHEN the endpoint is called
- THEN both MUST be listed with distinguishable share state

### Requirement: No contact data in shares

A share record and its response MUST NOT contain email, phone, or any contact identifier. Contact data is reachable only through an accepted match.

#### Scenario: No leakage at share time

- GIVEN a successful share
- WHEN the response and stored record are inspected
- THEN no contact field MUST be present
- AND only the match schema MUST have been written
