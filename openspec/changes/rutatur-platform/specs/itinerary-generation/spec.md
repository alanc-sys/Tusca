# Itinerary Generation Specification

**Capability**: `itinerary-generation`

## Purpose

Produces a feasible, ordered itinerary from a start point, available time, preferences, radius, and transport mode, using `poi-service` candidates and routing-engine times.

## Requirements

### Requirement: Request contract and validation

`POST /api/itinerary/generate` MUST accept `startPoint`, `availableTime`, `preferences`, `radius`, `transportMode`, and optional `maxPois` (default 6), working unauthenticated, and MUST reject with 400 when `availableTime` is outside 30–480, `radius` outside 100–3000, coordinates out of range, `preferences` is empty, `transportMode` is not `WALKING` or `DRIVING`, or `maxPois` is below 1. A 200 response MUST return `itineraryId`, `startPoint`, `route[]` — per stop `stopOrder`, `poi`, travel time and distance from previous, arrival, departure, `visitDuration` — plus `totalTime`, `totalVisitTime`, `totalTravelTime`, `startTime`, `endTime`, `transportMode`, and `feasible`.

#### Scenario: Public call with invalid input

- GIVEN a valid anonymous payload, then a payload with `availableTime=481`, `radius=99`, or `transportMode=CYCLING`
- WHEN each is submitted
- THEN the valid one MUST return 200 with every documented field and the others MUST return 400

### Requirement: Candidates through poi-service (RN-07)

`itinerary-service` MUST request candidates from `GET /internal/pois/candidates`, forwarding start point, radius, and categories, never querying Overpass directly. No stop MAY lie outside `radius`; an unavailable `poi-service` MUST produce 503.

#### Scenario: Filters forwarded and radius enforced

- GIVEN `radius=1500` and `preferences=[MUSEUM,PARK]`
- WHEN candidates are requested and the route is built
- THEN the outbound call MUST carry those filters and no stop outside 1500 meters MAY appear

### Requirement: Travel times come from the routing engine (RN-04)

Inter-point times and distances MUST come from the routing matrix for the selected mode; Euclidean distance MUST NOT be used. An unreachable routing engine MUST produce 503 with no estimated itinerary.

#### Scenario: Matrix values surface

- GIVEN a matrix returning 25 minutes and 1200 meters for the first leg
- WHEN the route is built
- THEN the first stop MUST report those values

#### Scenario: Routing engine down

- GIVEN the routing service is unreachable
- WHEN generation is requested
- THEN the response MUST be 503

### Requirement: Time accounting and feasibility (RN-01, RN-05, RN-08)

Each stop MUST consume its `visitTime`; its `departureTime` MUST equal `arrivalTime` plus that visit time. Every 200 response MUST satisfy `totalTime = totalVisitTime + totalTravelTime`, `endTime = startTime + totalTime`, and `sum(visitTime) + sum(travelTime) <= availableTime` with `feasible=true`. If no candidate fits, including the first leg, the service MUST respond 422.

#### Scenario: Invariant holds

- GIVEN any successful generation
- WHEN totals are recomputed from the stops
- THEN they MUST be consistent and MUST NOT exceed `availableTime`

#### Scenario: Nothing fits

- GIVEN `availableTime=30` and the nearest candidate needs 20 minutes of travel plus 30 minutes of visit
- WHEN generation runs
- THEN the response MUST be 422

### Requirement: Route structure (RN-02, RN-03)

`stopOrder` MUST start at 1 and be contiguous; the first leg MUST originate at `startPoint` and each later leg at the previous stop. A POI identifier MUST appear at most once in `route`.

#### Scenario: Walkable chain without repeats

- GIVEN a returned route and a candidate list repeating one POI identifier
- WHEN order and legs are inspected
- THEN orders MUST be 1..n, each leg MUST start at the previous point, and no POI MAY appear twice

### Requirement: Prioritization and determinism (RN-06)

Candidates MUST be scored by preference match and rating, tried in descending score, added only when they fit remaining time and the `maxPois` cap, else skipped. The same request over the same scored candidate set MUST produce identical order and totals.

#### Scenario: Skip and continue

- GIVEN the top-scoring candidate does not fit but the next does
- WHEN the route is built
- THEN the next MUST be included and the route MUST remain feasible

#### Scenario: Repeat run identical

- GIVEN a fixed candidate set and fixed scores
- WHEN generation runs twice
- THEN both routes MUST be identical
