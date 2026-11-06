# Route Matching Specification

**Capability**: `route-matching`

## Purpose

Suggests similar routes, manages the match lifecycle, and reveals minimal contact only after mutual acceptance, auditing every reveal.

## Requirements

### Requirement: Weighted similarity and suggestions (RN-11)

`match_score` MUST be 0..1 and equal `0.40*Jaccard + 0.25*startProximity + 0.20*categoryOverlap + 0.15*timeSimilarity`, components normalized to 0..1; Jaccard MUST be `|A∩B| / |A∪B|` over POI identifiers, 0 when both routes have no POIs, and identical inputs MUST score 1.0. Only active, non-expired opt-in shares of other users MAY be considered. Only scores `>= 0.35` MAY appear, ordered descending with deterministic ties; `GET /api/routes/matches/suggestions` MUST require `ROLE_USER` and expose no contact data.

#### Scenario: Identical routes

- GIVEN two shared routes with identical POIs, start point, categories, and available time
- WHEN similarity is computed
- THEN `match_score` MUST be 1.0

#### Scenario: Threshold and order

- GIVEN candidates scoring 0.90, 0.42, and 0.349
- WHEN suggestions are computed
- THEN the first two MUST be returned in descending order and the third MUST NOT appear

#### Scenario: Own and expired routes excluded

- GIVEN the caller has an active share and another user has an expired share
- WHEN suggestions are requested
- THEN neither MUST appear and no contact field MUST be present

### Requirement: Requests and inbox

`POST /api/routes/matches/request` MUST require `ROLE_USER`, accept `{"targetRouteId": "<id>"}`, and create a `PENDING` match with both participants, routes, and score; a target that is not an active share MUST produce 404, and an open request MUST NOT duplicate. `GET /api/routes/matches` MUST return only the caller's sent and received matches.

#### Scenario: Request created

- GIVEN two users with active shared routes
- WHEN A requests a match against B's route
- THEN the response MUST identify a `PENDING` match with responder B

#### Scenario: Repeated request

- GIVEN A already has a `PENDING` request against B's route
- WHEN A requests the same target again
- THEN exactly one `PENDING` match MUST exist for that pair

### Requirement: Mutual acceptance (RN-10)

`POST /api/routes/matches/{matchId}/accept` MUST require `ROLE_USER` and a `PENDING` match, becoming `ACCEPTED` only after both participants accept; one acceptance MUST leave it `PENDING`, and non-participants MUST receive 403.

#### Scenario: First acceptance keeps it pending

- GIVEN a `PENDING` match with no acceptance
- WHEN the responder accepts
- THEN the status MUST remain `PENDING` with that acceptance recorded

#### Scenario: Second acceptance completes the match

- GIVEN a `PENDING` match already accepted by the responder
- WHEN the requester accepts
- THEN the status MUST become `ACCEPTED` with an acceptance instant

### Requirement: Rejection and illegal transitions

`POST /api/routes/matches/{matchId}/reject` MUST require a participant, set `REJECTED`, and be terminal. `ACCEPTED → REJECTED`, `ACCEPTED → PENDING`, and `REJECTED → PENDING` MUST be refused with 409; unknown identifiers MUST produce 404.

#### Scenario: Terminal and illegal transitions

- GIVEN a `REJECTED` match and an `ACCEPTED` match
- WHEN accept or reject is called on them
- THEN each MUST return 409 and keep its current status

### Requirement: Contact reveal and audit (RN-10, RN-12)

`GET /api/routes/matches/{matchId}/contact` MUST require `ROLE_USER` and return minimal contact data only for an `ACCEPTED` match and a participant; `PENDING`, `REJECTED`, and non-participant access MUST return 403 without contact data, unknown matches 404. Every successful reveal MUST append an audit record (match, viewer, viewed user, instant, payload); a denied reveal MUST write none. Match data MUST persist only in the match schema.

#### Scenario: Accepted participant receives contact

- GIVEN an `ACCEPTED` match and a participant
- WHEN the contact endpoint is called
- THEN the response MUST be 200 with the counterpart's minimal fields and one audit record

#### Scenario: Any other access is blocked

- GIVEN a `PENDING` match, a `REJECTED` match, or a non-participant of an `ACCEPTED` match
- WHEN the contact endpoint is called
- THEN every case MUST return 403 with no contact field and no audit record
