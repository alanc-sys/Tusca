# POI Search Specification

**Capability**: `poi-search`

## Purpose

Serves normalized Points of Interest by location, radius, and category from Overpass, with caching and degradation. Exposes one public search endpoint and one internal candidate endpoint for `itinerary-service`.

## Requirements

### Requirement: Public search contract

`GET /api/pois/search` MUST require `lat`, `lng`, and `radius`, MUST accept an optional comma-separated `categories`, and MUST return 200 with `pois[]` and `totalCount`. Each POI MUST expose `id`, `name`, `category`, `location.latitude`, `location.longitude`, `address`, `visitTime`, `rating`, and `tags`.

#### Scenario: Successful search

- GIVEN a valid coordinate, `radius=1000`, and `categories=MUSEUM,PARK`
- WHEN the endpoint is called
- THEN the response MUST be 200
- AND `totalCount` MUST equal the number of returned POIs

#### Scenario: No POIs found

- GIVEN a valid coordinate with no matching POIs
- WHEN the endpoint is called
- THEN the response MUST be 200 with an empty array, not 404

### Requirement: Query validation

The service MUST reject with 400 when `lat` is outside -90..90, `lng` outside -180..180, `radius` is not greater than 0 or exceeds 5000, a required parameter is missing, or a category is outside the shared vocabulary.

#### Scenario: Radius out of bounds

- GIVEN `radius=0` or `radius=5001`
- WHEN the endpoint is called
- THEN the response MUST be 400

#### Scenario: Unknown category

- GIVEN `categories=MUSEUM,CASTLE`
- WHEN the endpoint is called
- THEN the response MUST be 400
- AND no upstream call MAY be made

### Requirement: Radius containment (RN-07)

Every returned POI MUST lie within `radius` meters of the query point, measured with the shared Haversine utility.

#### Scenario: Outlier excluded

- GIVEN an upstream result 1200 meters away and `radius=1000`
- WHEN normalization runs
- THEN that POI MUST NOT be returned

### Requirement: Normalization and category filter

Upstream elements MUST map to the shared POI contract; unmappable elements MUST be skipped without failing the request; every POI MUST have a positive `visitTime`. When `categories` is supplied, only those categories MAY be returned.

#### Scenario: Partial upstream data

- GIVEN one element without a name and one well-formed element
- WHEN normalization runs
- THEN the incomplete element MUST be dropped
- AND the response MUST be 200 with the valid POI

### Requirement: Query cache

An identical query within the cache lifetime MUST be served from cache without a second Overpass call. Cached entries MUST record an expiry instant.

#### Scenario: Cached repeat

- GIVEN a successful search and an unexpired cache entry
- WHEN the same query repeats
- THEN no new Overpass call MUST be issued
- AND the response body MUST equal the cached result

### Requirement: Graceful degradation

When Overpass times out or fails, the service MUST serve a cached entry for the same query if one exists; otherwise it MUST respond 503 with a clear message. It MUST NOT fabricate POIs.

#### Scenario: Stale cache fallback

- GIVEN a cached entry and an unreachable Overpass
- WHEN the search runs
- THEN the response MUST be 200 from cache

#### Scenario: No cache and no upstream

- GIVEN no cached entry and an unreachable Overpass
- WHEN the search runs
- THEN the response MUST be 503
- AND the payload MUST state that the POI provider is unavailable

### Requirement: Internal candidate endpoint

`GET /internal/pois/candidates` MUST accept the same parameters and return the normalized candidate list for `itinerary-service`, applying identical validation and radius containment. It MUST NOT be reachable through the gateway.

#### Scenario: Candidates for generation

- GIVEN `radius=1500` and `categories=MUSEUM,PARK`
- WHEN `itinerary-service` calls the internal endpoint
- THEN it MUST receive candidates in the public response shape

#### Scenario: Internal path not public

- GIVEN a client requests the internal path through the gateway
- WHEN it is routed
- THEN it MUST NOT reach the POI service (see `gateway-security`)
