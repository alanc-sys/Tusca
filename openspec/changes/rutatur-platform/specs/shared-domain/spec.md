# Shared Domain Specification

**Capability**: `shared-domain`

## Purpose

Defines the single shared contract consumed by every RutaTur module: normalized DTOs, geographic utilities, domain enums, validation constraints, and domain exceptions. Publishing one contract prevents cross-service payload drift and keeps error semantics identical across all services.

## Requirements

### Requirement: Shared contract is the only definition of cross-service types

Every module MUST consume shared DTOs, enums, exceptions, and geo utilities from the shared library. No module SHALL declare a parallel copy of a shared type.

#### Scenario: No duplicate definition compiles

- GIVEN the multi-module build
- WHEN all modules are compiled
- THEN cross-service payload types MUST resolve to the shared library
- AND no module-local duplicate of those types MAY exist

### Requirement: Geographic distance uses Haversine

The shared geo utility MUST compute great-circle distance in meters using the Haversine formula. It MUST be the only distance function used for start-point proximity and radius filtering.

#### Scenario: Reference coordinate pair

- GIVEN two coordinates with a known reference distance
- WHEN the distance is computed
- THEN the result MUST be expressed in meters and be within 1% of the reference value

#### Scenario: Identical points

- GIVEN the same coordinate supplied twice
- WHEN the distance is computed
- THEN the result MUST be 0

### Requirement: Shared domain vocabulary

The shared contract MUST define POI categories `MUSEUM`, `PARK`, `CAFE`, `VIEWPOINT`, `RESTAURANT`, `MONUMENT`, `GALLERY`, `THEATRE`, `CHURCH`, `SHOPPING`; transport modes `WALKING`, `DRIVING`; and match statuses `PENDING`, `ACCEPTED`, `REJECTED`.

#### Scenario: Unsupported vocabulary value

- GIVEN a request containing an unknown category or transport mode
- WHEN the value is validated
- THEN the request MUST be rejected with 400

#### Scenario: Match status set is closed

- GIVEN the match status type
- WHEN its values are enumerated
- THEN exactly `PENDING`, `ACCEPTED`, and `REJECTED` MUST exist

### Requirement: Domain exceptions map to stable HTTP semantics

Shared exceptions MUST map to stable statuses: resource not found → 404; not feasible or otherwise unprocessable business outcome → 422; forbidden match or contact access → 403; unavailable upstream integration → 503.

#### Scenario: Not-feasible outcome

- GIVEN the itinerary engine rejects a route as not feasible
- WHEN the service renders the error
- THEN the response status MUST be 422

#### Scenario: Denied contact access

- GIVEN a contact reveal denied by the shared match exception
- WHEN the service renders the error
- THEN the response status MUST be 403

### Requirement: Shared validation ranges

Shared validators MUST enforce `availableTime` between 30 and 480 minutes, generation `radius` between 100 and 3000 meters, non-empty `preferences`, and `maxPois` ≥ 1. POI search radius MUST follow the extent documented for that endpoint.

#### Scenario: Inclusive boundaries accepted

- GIVEN `availableTime` 30 and `radius` 100
- WHEN the generation request is validated
- THEN validation MUST pass
- AND the same MUST hold for `availableTime` 480 and `radius` 3000

#### Scenario: Out-of-range values rejected

- GIVEN `availableTime` 29 or 481, or `radius` 99 or 3001
- WHEN the generation request is validated
- THEN the request MUST be rejected with 400

## Business Rule Coverage

RN-01, RN-05, RN-07 are encoded here as shared constraints and enforced end-to-end in `itinerary-generation`.
