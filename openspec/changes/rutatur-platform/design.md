# Design: RutaTur Platform Implementation

## Technical Approach

Five Maven modules behind one reactor: `rutatur-common` (contract JAR), three servlet Spring Boot services, one reactive gateway. Every cross-service payload is a class from `rutatur-common`; services never define a parallel copy. Generation is a **persist-then-claim** flow: `POST /api/itinerary/generate` stores an unowned draft and returns its UUID, `POST /api/itineraries` atomically claims that draft for the JWT subject. This keeps the documented "generate is public, save needs an id" contract while making `404` on save meaningful, and it gives one identifier that flows from generation through save to sharing and matching.

```text
Client ──► api-gateway :8080 ──► poi-service :8081 ──► Overpass
                  │                    ▲
                  ├──► itinerary-service :8082 ──► OSRM /table
                  │            │
                  └──► route-match-service :8083 ──► itinerary-service (internal)
                                     └──► Keycloak Admin API (contact reveal only)
```

## 1. Module and Build Design

### 1.1 Reactor, versions, JDK

| Item | Value |
|---|---|
| Root `pom.xml` | `<packaging>pom</packaging>`, aggregator + parent, module order `rutatur-common`, `poi-service`, `itinerary-service`, `route-match-service`, `api-gateway` |
| Platform | `spring-boot-starter-parent` **3.3.5** (present in local `~/.m2`, offline-verified by sdd-init probe) |
| Cloud | `spring-cloud-dependencies` **2023.0.5** (`Leyton`, supports Boot 3.3.x) |
| Framework patch | `spring-framework.version` **6.1.15**: Gateway 4.1.6 calls `HttpHeaders.headerSet()`, introduced in 6.1.15, while Boot 3.3.5 manages 6.1.14 |
| Resilience | `io.github.resilience4j:resilience4j-spring-boot3` **2.2.0** |
| Docs | `org.springdoc:springdoc-openapi-starter-webmvc-ui` / `-webflux-ui` **2.6.0** |
| Java | `java.version=21`, `maven.compiler.release=21`, `project.build.sourceEncoding=UTF-8` |
| Maven | 3.9.x (enforced `>=3.9`) |
| Executable modules | `<finalName>app</finalName>` + `spring-boot-maven-plugin` `repackage`; `rutatur-common` has **no** spring-boot plugin |

**JDK pinning (sdd-init risk: PATH is JDK 25, Maven runtime is JDK 26, target is Temurin 21).** `maven.compiler.release=21` alone is not a pin; the compiler would happily emit 21 bytecode while Mockito/Byte Buddy run on 26. Therefore:

1. `maven-enforcer-plugin` bound to `validate` with `requireJavaVersion` range `[21,22)` and an explicit message naming Temurin 21. A JDK 25/26 shell fails fast instead of producing a flaky test run.
2. Documented local command: `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` before any Maven invocation (README build section + W1 task).
3. Optional, not required: `maven-toolchains-plugin` with `~/.m2/toolchains.xml`. Not used by default because it needs machine-local setup that a clean checkout cannot assume.
4. Compose/CI build image `maven:3.9-eclipse-temurin-21`, so container builds are always JDK 21 regardless of host.

### 1.2 Dependency edges and starters

| Module | Depends on | Starters / libraries |
|---|---|---|
| `rutatur-common` | — | `jakarta.validation:jakarta.validation-api`, `jackson-annotations`, `spring-security-oauth2-jose` (for `RealmRoleConverter`); test: `spring-boot-starter-test`, `spring-boot-starter-validation` |
| `poi-service` | common | `-web`, `-validation`, `-data-jpa`, `-security`, `-oauth2-resource-server`, `-actuator`, resilience4j, springdoc-webmvc-ui, `postgresql` (runtime) |
| `itinerary-service` | common | same set as poi-service |
| `route-match-service` | common | same set as poi-service |
| `api-gateway` | common | `spring-cloud-starter-gateway`, `-security`, `-oauth2-resource-server`, `-actuator`, springdoc-webflux-ui |

`rutatur-common` stays web-stack-neutral: no servlet or reactive starter, so the gateway can depend on it without pulling Tomcat. Outbound HTTP from servlet services uses **`RestClient`** (Spring 6.1); `WebClient`/`spring-boot-starter-webflux` is banned outside the gateway.

**Servlet/reactive isolation is enforced, not documented.** Root `maven-enforcer-plugin` adds `bannedDependencies`:
- `api-gateway`: ban `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-tomcat`.
- servlet modules: ban `spring-boot-starter-webflux`, `spring-cloud-starter-gateway`, `reactor-netty-http`, `spring-boot-starter-reactor-netty`.

Plus `banDuplicateClasses` (blocks a module-local copy of a shared type) and `requireMavenVersion`. Because module POMs declare no versions, `spring-boot-dependencies` and `spring-cloud-dependencies` remain the only version sources.

## 2. Architecture Decisions

| # | Decision | Options considered | Choice | Rationale |
|---|---|---|---|---|
| D1 | Generation persistence | stateless + opaque payload; draft+claim | **Draft row + single atomic claim** | Satisfies `itineraryId` in save, keeps `404` meaningful, one id across save/share/match, no client-side replay of the whole itinerary |
| D2 | Outbound HTTP client | `WebClient`; `RestClient` | **`RestClient`** | Servlet services must not load a reactive context; RestClient is blocking and integrates with Resilience4j decorators |
| D3 | Schema lifecycle | Flyway per module; initdb scripts + `validate` | **`db/init/*.sql` + `ddl-auto=validate`** | Runs once on an empty volume (restart-safe by construction), one source of DDL, no migration engine in 3 modules; `validate` fails fast on drift |
| D4 | Schema isolation | convention only; DB roles | **one Postgres role per service** | A misconfigured datasource URL fails with a permission error instead of silently writing a foreign schema |
| D5 | JWT role mapping | per-service converter; shared converter | **shared `RealmRoleConverter` in common** | Keycloak emits roles in `realm_access.roles`; four copies would drift |
| D6 | Internal endpoint protection | network-only; shared secret | **shared secret header + no route + loopback-bound published ports** | Service ports are published for dev, so the network alone is not a boundary on the host |
| D7 | `/api/admin/**` target | out of scope; new admin service; single endpoint | **`GET /api/admin/itineraries` in itinerary-service** | The route requirement demands a real target; this is the smallest genuinely administrative read |
| D8 | Contact data source | store at share time; Keycloak Admin API | **Keycloak Admin API at reveal time** | Share records must not hold contact data; the IdP already owns the profile |
| D9 | Duplicate open request | 409; idempotent 200 | **idempotent 200 + partial unique index** | Spec forbids duplicates but not a second call; DB index makes it race-free |
| D10 | Keycloak issuer | host issuer + split JWKS; pinned in-network issuer | **`KC_HOSTNAME=http://keycloak:8080`** | One issuer string everywhere; host clients obtain tokens from `:8180` but tokens carry the in-network `iss` |

## 3. Resolved Spec Open Questions

| # | Question | Resolution | Rationale |
|---|---|---|---|
| Q1 | No POI-by-id endpoint | **Do not add one.** The only POI-identifier consumer is Jaccard in `route-match-service`, which compares opaque strings from two stored snapshots. "Unknown POI" is therefore **never observable** as an error: no endpoint accepts a POI id, generation accepts filters only, and saved itineraries keep full POI snapshots. | No spec scenario can be RED without it; adding it would be unexercised surface. Docs' `PoiNotFoundException` is dropped for the same reason. |
| Q2 | `radius`: search `1..5000` vs generation `100..3000` | **Keep both, per endpoint, as two distinct shared types.** `PoiSearchQuery.radius` `@Min(1) @Max(5000)`; `ItineraryRequestDto.radius` `@Min(100) @Max(3000)`. | Search is a direct Overpass query bounded by provider cost; generation bounds OSRM matrix size and itinerary plausibility. `shared-domain` explicitly defers search radius to "the extent documented for that endpoint". |
| Q3 | Generation stateless vs save `itineraryId` | **Persist an unowned draft, claim once.** Generate writes `itinerary_draft` and returns its UUID as `itineraryId`. Save inserts `saved_itinerary` with the **same UUID** as `id` and `itinerary_id`, copying `request_data`/`response_data`; unknown or expired draft → `404`; second claim → `409`. Draft TTL 24 h, evaluated on read; no scheduled purge. | Satisfies the 201 `id` + `itineraryId` shape, guarantees `404` for unknown ids, keeps one id for `GET/DELETE /api/itineraries/{id}`, `/api/routes/share`, and `/internal/itineraries/{id}`, and keeps reads independent of external services. |
| Q4 | Derived `409` / `404` | **Confirmed.** Rule: any accept/reject on a non-`PENDING` match → `409 MATCH_TRANSITION_INVALID` (status unchanged); unknown match → `404`; non-participant accept/reject → `403`; **any** non-`ACCEPTED` or non-participant contact request → `403`; unknown match on contact → `404`; unknown **or foreign** itinerary/share → `404 ITINERARY_NOT_FOUND` / `SHARE_NOT_FOUND`, never `403`. | `404` for ownership prevents existence disclosure in the history/share surfaces; `route-matching` mandates `403` for match access, so match existence is disclosed there by spec and is accepted (ids are UUIDv4). |
| Q5 | `/api/admin/**` target | **In scope with one endpoint:** `GET /api/admin/itineraries?userId=` in `itinerary-service`, `ROLE_ADMIN`. Gateway routes `/api/admin/**` there. | A `MUST` route needs a target; inventing a service or returning `501` breaks `gateway-security`. Read-only, no cross-service aggregation. |
| Q6 | `GET /api/routes/shareable` needs the caller's itinerary list, but only a detail internal endpoint exists | **Add** `GET /internal/itineraries?owner={subject}` (network + internal token) returning `InternalItinerarySummary[]`. | `route-sharing` requires listing shared *and* unshared owned itineraries; `route-match-service` cannot read `itinerary_schema`. Additive, no spec edit. |
| Q7 | Contact payload has no source in any spec | **Keycloak Admin API** `GET /admin/realms/rutatur-realm/users/{sub}` via a `rutatur-match` service account with `view-users`; return `{userId, username, email}`; failure → `503 CONTACT_PROVIDER_UNAVAILABLE`, **no audit row**. | Share records must stay contact-free; the IdP already holds the profile; audit is written only on success, matching "every successful reveal appends a record". |

## 4. Shared Domain Model (`rutatur-common`)

Package roots: `com.rutatur.common.dto`, `.enums`, `.exception`, `.error`, `.util`, `.validation`, `.security`. JSON is camelCase; timestamps ISO-8601 UTC (`Instant`); clocks `LocalTime` serialized `HH:mm`.

### 4.1 Enums

| Enum | Values | Notes |
|---|---|---|
| `Category` | `MUSEUM, PARK, CAFE, VIEWPOINT, RESTAURANT, MONUMENT, GALLERY, THEATRE, CHURCH, SHOPPING` | Closed set; unknown value → Jackson conversion error → `400` |
| `TransportMode` | `WALKING, DRIVING` | Closed set; OSRM profile mapping stays in `itinerary-service` |
| `MatchStatus` | `PENDING, ACCEPTED, REJECTED` | Exactly three values; transition rules live in `route-match-service` |

### 4.2 DTOs

| DTO | Field | Type | Null | Validation |
|---|---|---|---|---|
| `GeoPointDto` | `latitude` / `longitude` | `Double` | no | `@NotNull`, `@DecimalMin("-90")`/`@DecimalMax("90")`, `@DecimalMin("-180")`/`@DecimalMax("180")` |
| `PoiDto` | `id` | `String` | no | `@NotBlank`; format `osm:<node\|way\|relation>/<id>` (`poi-123` in docs is illustrative) |
| | `name` | `String` | no | `@NotBlank` |
| | `category` | `Category` | no | `@NotNull` |
| | `location` | `GeoPointDto` | no | `@NotNull @Valid` |
| | `address` | `String` | yes | — |
| | `visitTime` | `int` | no | `@Min(1)` (minutes) |
| | `rating` | `double` | no | `@DecimalMin("0") @DecimalMax("5")`, default `0.0` |
| | `tags` | `List<String>` | no | default empty list |
| `PoiSearchQuery` | `lat`, `lng` | `Double` | no | same coordinate bounds |
| | `radius` | `Integer` | no | `@Min(1) @Max(5000)` — search extent (Q2) |
| | `categories` | `List<Category>` | yes | empty ⇒ all categories |
| `PoiSearchResponse` | `pois` | `List<PoiDto>` | no | `totalCount == pois.size()` |
| | `totalCount` | `int` | no | — |
| `ItineraryRequestDto` | `startPoint` | `GeoPointDto` | no | `@NotNull @Valid` |
| | `availableTime` | `Integer` | no | `@NotNull @Min(30) @Max(480)` |
| | `preferences` | `List<Category>` | no | `@NotEmpty` |
| | `radius` | `Integer` | no | `@NotNull @Min(100) @Max(3000)` — generation extent (Q2) |
| | `transportMode` | `TransportMode` | no | `@NotNull` |
| | `maxPois` | `Integer` | yes | `@Min(1)`; service applies `6` when null |
| `ItineraryStopDto` | `stopOrder` | `int` | no | `@Min(1)` |
| | `poi` | `PoiDto` | no | `@NotNull @Valid` |
| | `travelTimeFromPrevious` | `int` | no | `@Min(0)`, minutes from routing matrix |
| | `distanceFromPrevious` | `int` | no | `@Min(0)`, meters from routing matrix |
| | `arrivalTime`, `departureTime` | `LocalTime` | no | `@JsonFormat(pattern="HH:mm")` |
| | `visitDuration` | `int` | no | `@Min(1)` |
| `ItineraryResponseDto` | `itineraryId` | `UUID` | no | draft id (Q3) |
| | `startPoint` | `GeoPointDto` | no | echo |
| | `route` | `List<ItineraryStopDto>` | no | ≥1 |
| | `totalTime`, `totalVisitTime`, `totalTravelTime` | `int` | no | minutes, invariant §6 |
| | `startTime`, `endTime` | `LocalTime` | no | `HH:mm` |
| | `transportMode` | `TransportMode` | no | echo |
| | `feasible` | `boolean` | no | always `true` on 200 |
| `SaveItineraryRequest` | `itineraryId` | `UUID` | no | `@NotNull` |
| `SavedItineraryResponse` | `id`, `itineraryId` | `UUID` | no | equal by D1/Q3 |
| | `userId` | `String` | no | JWT `sub` |
| | `createdAt` | `Instant` | no | — |
| `SavedItinerarySummary` | `id`, `itineraryId` | `UUID` | no | — |
| | `startPoint` | `GeoPointDto` | no | — |
| | `totalTime` | `int` | no | — |
| | `routeSummary` | `String` | no | `"A + B + C"`, first 3 names |
| | `createdAt` | `Instant` | no | ordering key desc |
| `SavedItineraryDetail` | `id`, `userId`, `itineraryId`, `startPoint`, `availableTime`, `transportMode`, `totalTime`, `totalVisitTime`, `totalTravelTime`, `startTime`, `endTime`, `route[]`, `createdAt` | as above | no | stored snapshot, no external calls |
| `InternalItineraryDto` | `itineraryId` | `UUID` | no | — |
| | `ownerUserId` | `String` | no | JWT `sub` |
| | `startPoint` | `GeoPointDto` | no | — |
| | `preferences` | `List<Category>` | no | from `request_data` |
| | `availableTime` | `int` | no | minutes |
| | `transportMode` | `TransportMode` | no | — |
| | `totalTime`, `totalVisitTime`, `totalTravelTime` | `int` | no | — |
| | `poiIds` | `List<String>` | no | ordered, deduplicated |
| | `routeSummary` | `String` | no | — |
| | `createdAt` | `Instant` | no | — |
| `InternalItinerarySummary` | `itineraryId`, `ownerUserId`, `startPoint`, `totalTime`, `routeSummary`, `createdAt` | as above | no | Q6 |
| `SharedRouteDto` | `id`, `itineraryId` | `UUID` | no | — |
| | `active` | `boolean` | no | — |
| | `createdAt`, `expiresAt` | `Instant` | no | — |
| `ShareableItineraryDto` | `itineraryId` | `UUID` | no | — |
| | `startPoint` | `GeoPointDto` | no | — |
| | `totalTime` | `int` | no | — |
| | `shared` | `boolean` | no | — |
| | `shareId`, `expiresAt` | `UUID` / `Instant` | yes | null when unshared |
| `MatchSuggestionDto` | `targetRouteId` | `UUID` | no | responder share id |
| | `sourceRouteId` | `UUID` | no | caller's best share |
| | `matchScore` | `double` | no | `0.35..1.0`, 4 dp |
| | `matchedPoiCount` | `int` | no | `@Min(0)` |
| | `matchedPoiIds` | `List<String>` | no | sorted ascending |
| | `sharedAt` | `Instant` | no | no contact, no user id, no coordinates |
| `MatchRequestDto` | `targetRouteId` | `UUID` | no | `@NotNull` |
| `ShareRouteRequest` | `itineraryId` | `UUID` | no | `@NotNull` |
| `MatchDto` | `id` | `UUID` | no | — |
| | `requesterUserId`, `responderUserId` | `String` | no | Keycloak subjects |
| | `requesterRouteId`, `responderRouteId` | `UUID` | no | share ids |
| | `matchScore`, `matchedPoiCount` | `double` / `int` | no | — |
| | `status` | `MatchStatus` | no | — |
| | `requesterAcceptedAt`, `responderAcceptedAt` | `Instant` | yes | per-side consent |
| | `requestedAt`, `respondedAt`, `acceptedAt` | `Instant` | no / yes / yes | `respondedAt` on terminal transition |
| `ContactDto` | `userId` | `String` | no | counterpart `sub` |
| | `username` | `String` | no | Keycloak `username` |
| | `email` | `String` | yes | only contact field |

### 4.3 `GeoUtils`

```java
public static double haversineMeters(double lat1, double lon1, double lat2, double lon2)
public static double haversineMeters(GeoPointDto a, GeoPointDto b)
```

Mean Earth radius **6 371 008.8 m**; result in **meters**; identical coordinates return exactly `0.0` (short-circuit before the trig block); tolerance **1 %** against reference pairs. This is the only distance function in the platform: POI radius containment, generation radius assertions, and start-point proximity all call it.

### 4.4 Exceptions and error envelope

```text
RutaturException(RuntimeException)  — ErrorCode code, HttpStatus status
├── InvalidRequestException          400  VALIDATION_FAILED
├── ResourceNotFoundException        404  ITINERARY_NOT_FOUND | SHARE_NOT_FOUND | MATCH_NOT_FOUND
├── BusinessException                422  ITINERARY_NOT_FEASIBLE | NO_ACTIVE_SHARE
├── ForbiddenActionException         403  MATCH_ACCESS_DENIED | CONTACT_NOT_REVEALED
├── ConflictException                409  MATCH_TRANSITION_INVALID | ITINERARY_ALREADY_SAVED
└── UpstreamServiceException         503  POI_PROVIDER_UNAVAILABLE | ROUTING_PROVIDER_UNAVAILABLE
                                         | CONTACT_PROVIDER_UNAVAILABLE | UPSTREAM_UNAVAILABLE
```

`ErrorCode` is an enum carrying `httpStatus()` so status and code can never disagree. Mapping to HTTP happens in a thin `@RestControllerAdvice` per servlet module (≈20 lines) and a reactive `ErrorWebExceptionHandler` in the gateway; the handlers live outside `rutatur-common` because common must not depend on `spring-web` or `spring-webflux`.

```json
{
  "timestamp": "2026-11-03T14:02:11Z",
  "status": 404,
  "error": "Not Found",
  "code": "ITINERARY_NOT_FOUND",
  "message": "Itinerary not found",
  "path": "/api/itineraries/2f0c...",
  "violations": [{"field": "radius", "message": "must be between 100 and 3000"}]
}
```

`violations` is present only for `400`. `message` never contains upstream URLs, SQL, or tokens.

## 5. Inter-Service HTTP Contracts

Conventions for every table below: `internal token` = header `X-Internal-Token` equal to `RUTATUR_INTERNAL_TOKEN`, checked by a filter on `/internal/**`, compared in constant time, missing/wrong → `401 INTERNAL_AUTH_REQUIRED`. `CB` = Resilience4j circuit breaker, 50 % failure rate over a 10-call sliding window, open 30 s, 3 half-open calls. Retries apply to timeouts and 5xx only, never to 4xx. All services bind their own business paths (`/api/...`) so gateway routes are pass-through and need no path rewriting.

### 5.1 poi-service (consumer: itinerary-service, gateway)

| Call | Auth | Request | Response | Codes | Timeout | Retry | CB | Fallback |
|---|---|---|---|---|---|---|---|---|
| `GET /api/pois/search` | public; optional JWT validated by gateway | query `lat`, `lng`, `radius` (1..5000), `categories` (`A,B`) | `PoiSearchResponse` | 200, 400, 503 | Overpass 5 s (connect 2 s) | 1, 500 ms backoff | yes | unexpired cache hit → 200; expired cache → 200; else `503 POI_PROVIDER_UNAVAILABLE` |
| `GET /internal/pois/candidates` | internal token | same query as search | `PoiSearchResponse` (identical shape) | 200, 400, 401, 503 | same | same | same | same |
| Overpass `POST ${OVERPASS_URL}` | none | Overpass QL body (`data=`) | OSM JSON | 200, 429, 504 | 5 s | 1 | yes | cache then 503; never fabricate POIs |
| `GET /actuator/health` | none (not routed) | — | Boot health JSON | 200, 503 | — | — | — | — |

Normalization: elements without `name`, without usable coordinates, or mapping to no `Category` are dropped; duplicates by `external_id` collapse; `rating` defaults `0.0`; `visitTime` derives from category default (MUSEUM 90, PARK 45, CAFE 30, VIEWPOINT 20, RESTAURANT 60, MONUMENT 30, GALLERY 45, THEATRE 90, CHURCH 30, SHOPPING 45) and is always ≥ 1. Radius containment via `GeoUtils` is applied **after** mapping, before caching.

Cache key: `sha256(lat|lng|radius|sorted(categories))` as lowercase hex. TTL 15 min; rows retained 7 days so stale fallback is possible.

### 5.2 itinerary-service

| Call | Auth | Request | Response | Codes | Timeout | Retry | CB | Fallback |
|---|---|---|---|---|---|---|---|---|
| `POST /api/itinerary/generate` | public; optional JWT | `ItineraryRequestDto` | `ItineraryResponseDto` | 200, 400, 422, 503 | — | — | — | — |
| `POST /api/itineraries` | `ROLE_USER` | `{"itineraryId":"<uuid>"}` | `SavedItineraryResponse` | 201, 400, 401, 403, 404, 409 | — | — | — | — |
| `GET /api/itineraries` | `ROLE_USER` | — | `SavedItinerarySummary[]` | 200, 401, 403 | — | — | — | — |
| `GET /api/itineraries/{id}` | `ROLE_USER`, owner | — | `SavedItineraryDetail` | 200, 400, 401, 403, 404 | — | — | — | — |
| `DELETE /api/itineraries/{id}` | `ROLE_USER`, owner | — | empty | 204, 400, 401, 403, 404 | — | — | — | — |
| `GET /api/admin/itineraries?userId=` | `ROLE_ADMIN` | — | `SavedItinerarySummary[]` (cap 100, `createdAt` desc) | 200, 400, 401, 403 | — | — | — | — |
| `GET /internal/itineraries/{id}` | internal token | — | `InternalItineraryDto` | 200, 400, 401, 404 | — | — | — | — |
| `GET /internal/itineraries?owner=` | internal token | query `owner=<sub>` | `InternalItinerarySummary[]` | 200, 400, 401 | — | — | — | — |
| → poi-service `GET /internal/pois/candidates` | internal token | `lat`, `lng`, `radius`, `categories` from request | `PoiSearchResponse` | 200, 400, 503 | 5 s | 1 | yes | `503 UPSTREAM_UNAVAILABLE`, no itinerary |
| → OSRM `GET ${OSRM_URL}/table/v1/{profile}/{lon,lat;...}?annotations=duration,distance` | none | coordinates = start + candidates | `durations[][]`, `distances[][]` | 200, 400, 503 | 5 s (connect 2 s) | 1, 300 ms backoff | yes | `503 ROUTING_PROVIDER_UNAVAILABLE`, never Euclidean estimates |

Mapping rules: `401` originates at the gateway; a service-level `@PreAuthorize` failure yields `403`. Ownership failures and unknown ids both produce `404 ITINERARY_NOT_FOUND` with the same message. `POST /api/itineraries` errors: unknown/expired draft → `404`; already claimed by any user → `409 ITINERARY_ALREADY_SAVED`; claim is a single `INSERT` on the primary key, so concurrency cannot produce two winners.

### 5.3 route-match-service

| Call | Auth | Request | Response | Codes | Timeout | Retry | CB | Fallback |
|---|---|---|---|---|---|---|---|---|
| `POST /api/routes/share` | `ROLE_USER` | `{"itineraryId":"<uuid>"}` | `SharedRouteDto` — 201 created, 200 existing/reactivated | 200, 201, 400, 401, 403, 404, 503 | — | — | — | — |
| `GET /api/routes/shareable` | `ROLE_USER` | — | `ShareableItineraryDto[]` | 200, 401, 403, 503 | — | — | — | — |
| `GET /api/routes/matches/suggestions` | `ROLE_USER` | — | `MatchSuggestionDto[]` (§7) | 200, 401, 403 | — | — | — | — |
| `POST /api/routes/matches/request` | `ROLE_USER` | `{"targetRouteId":"<uuid>"}` | `MatchDto` — 201 created, 200 existing open match | 200, 201, 400, 401, 403, 404, 422 | — | — | — | — |
| `GET /api/routes/matches` | `ROLE_USER` | — | `MatchDto[]` sent ∪ received, `requestedAt` desc | 200, 401, 403 | — | — | — | — |
| `POST /api/routes/matches/{matchId}/accept` | participant | — | `MatchDto` | 200, 400, 401, 403, 404, 409 | — | — | — | — |
| `POST /api/routes/matches/{matchId}/reject` | participant | — | `MatchDto` | 200, 400, 401, 403, 404, 409 | — | — | — | — |
| `GET /api/routes/matches/{matchId}/contact` | participant + `ACCEPTED` | — | `ContactDto` | 200, 400, 401, 403, 404, 503 | — | — | — | — |
| → itinerary `GET /internal/itineraries/{id}` | internal token | — | `InternalItineraryDto` | 200, 404, 503 | 3 s | 1 | yes | `503 UPSTREAM_UNAVAILABLE`; share writes only after a successful snapshot |
| → itinerary `GET /internal/itineraries?owner=` | internal token | `owner=<sub>` | `InternalItinerarySummary[]` | 200, 503 | 3 s | 1 | yes | `503 UPSTREAM_UNAVAILABLE` |
| → Keycloak `GET ${KEYCLOAK_ADMIN_URL}/admin/realms/rutatur-realm/users/{sub}` | client_credentials (`rutatur-match`, `view-users`) | — | user representation | 200, 404, 503 | 3 s | 1 | yes | `503 CONTACT_PROVIDER_UNAVAILABLE`, no audit row |

`POST /api/routes/share` flow: detail lookup → `404` propagates unchanged → **ownership check `detail.ownerUserId == sub`, mismatch → `404 SHARE_NOT_FOUND`** → build snapshot (POI ids, start point, preferences, available time, totals) → upsert on `shared_route.itinerary_id` (active → return existing; inactive/expired → reactivate with a fresh `expiresAt`). Failure leaves no partial share because the write happens last in one transaction. `GET /api/routes/shareable` lists the internal summaries for `owner=sub` and left-joins the caller's shares on `itinerary_id`; an itinerary with no row is `shared=false`. `POST /api/routes/matches/request` recomputes similarity between the target and the caller's best active share, so the stored score is reproducible; caller with no active share → `422 NO_ACTIVE_SHARE`; target not an active share or owned by the caller → `404 SHARE_NOT_FOUND`.

### 5.4 Gateway routes

| Route id | Path predicate | Target | Filters | Auth |
|---|---|---|---|---|
| `pois` | `/api/pois/**` | `http://poi-service:8081` | `RemoveRequestHeader=X-Internal-Token` | public |
| `itinerary-generate` | `/api/itinerary/**` | `http://itinerary-service:8082` | `RemoveRequestHeader=X-Internal-Token` | public |
| `itineraries` | `/api/itineraries/**` | `http://itinerary-service:8082` | `RemoveRequestHeader=X-Internal-Token` | `ROLE_USER` |
| `routes` | `/api/routes/**` | `http://route-match-service:8083` | `RemoveRequestHeader=X-Internal-Token` | `ROLE_USER` |
| `admin` | `/api/admin/**` | `http://itinerary-service:8082` | `RemoveRequestHeader=X-Internal-Token` | `ROLE_ADMIN` |
| `docs-pois` | `/v3/api-docs/pois` | `http://poi-service:8081/v3/api-docs` | `RewritePath` | public |
| `docs-itineraries` | `/v3/api-docs/itineraries` | `http://itinerary-service:8082/v3/api-docs` | `RewritePath` | public |
| `docs-routes` | `/v3/api-docs/routes` | `http://route-match-service:8083/v3/api-docs` | `RewritePath` | public |

No route exists for `/internal/**`, `/actuator/**`, or any other prefix; Spring Cloud Gateway answers `404` for unmatched paths, which satisfies both "unknown business path" and "internal path isolation" with one mechanism. Gateway adds no business endpoint; Swagger UI at `/swagger-ui.html` is served locally with `urls` pointing at the three proxied docs routes.

## 6. Optimizer Design

Input: `ItineraryRequestDto`; `maxPois = request.maxPois ?? 6`; fixed clock anchor `startTime = ItineraryProperties.startClock` (default **09:00**, minutes 540). Determinism requires an anchor because the contract carries no clock input.

1. **Candidates.** Call `GET /internal/pois/candidates?lat&lng&radius&categories=<preferences>`. Any `503` aborts generation with `503`.
2. **Dedupe.** Collapse candidates by `PoiDto.id` (first occurrence wins), drop candidates whose `GeoUtils` distance to `startPoint` exceeds `radius`, drop `visitTime <= 0`.
3. **Score.** For candidate `c` with preference index `i` in `preferences` (`0`-based, `P = preferences.size()`):
   `preferenceScore = (P - i) / P` (category absent from `preferences` → `0.0`); `ratingScore = clamp(c.rating, 0, 5) / 5.0`;
   `score = 0.7 * preferenceScore + 0.3 * ratingScore` (preferences dominate per RN-06; rating orders candidates inside one category and rewards a listed category over an unlisted one).
4. **Total order.** Sort by `score` desc, then `rating` desc, then `visitTime` asc, then `id` `String.compareTo` asc. The final key is unique, so the order is total.
5. **Bound.** Truncate to `maxCandidates` (default **50**, `rutatur.itinerary.max-candidates`). This caps the OSRM matrix at 51×51 and keeps payload memory bounded; the cut is deterministic because the comparator already fixed the order.
6. **Matrix.** One OSRM `/table` call, coordinates = `[start] + candidates` in the sorted order, `sources=0`, `annotations=duration,distance`. Durations in seconds → `Math.round(seconds / 60.0)` minutes; distances → `Math.round(meters)`. A `null` cell means "no route" and is treated as `∞` for that leg; an HTTP/transport failure aborts with `503`.
7. **Greedy loop.** `currentIndex = 0` (start), `used = 0`, `visitTotal = 0`, `travelTotal = 0`, `stops = []`.
   For each candidate in order: if `stops.size == maxPois` break; `leg = duration[currentIndex][candidateIndex]`; if `leg == ∞` skip; if `used + leg + c.visitTime > availableTime` skip (no "look ahead"); else append the stop, set `currentIndex = candidateIndex`, `used += leg + visitTime`, `visitTotal += visitTime`, `travelTotal += leg`.
   The loop visits candidates in score order and skips without reordering, which is exactly the "skip and continue" scenario.
8. **Empty result.** `stops.isEmpty()` → `422 ITINERARY_NOT_FEASIBLE` (covers "first leg does not fit").
9. **Timeline.** `arrival_1 = 540 + travel_1`; `departure_k = arrival_k + visit_k`; `arrival_{k+1} = departure_k + travel_{k+1}`; `endTime = startTime + totalTime`. Because every minute is either travel or visit, `totalTime = totalVisitTime + totalTravelTime` and `endTime = startTime + totalTime` hold by construction, and `used == totalTime <= availableTime`.
10. **Persist draft.** Serialize the request/response as JSONB, insert `itinerary_draft`, return `itineraryId`.

Complexity: scoring `O(n)`, sort `O(n log n)`, greedy `O(n)`, network `O(1)` calls with `O(n)` coordinates and `O(n²)` matrix payload (`n ≤ 50` → ≤ 2601 cells). Determinism is guaranteed by: fixed clock anchor, total-order comparator with a unique final key, `Math.round` (half-up) on matrix values, ordered `List` (no map iteration) in the response, and no randomness anywhere.

## 7. Similarity Design

For a caller share `A` and a third-party active share `B`:

| Component | Weight | Formula | Edge cases |
|---|---|---|---|
| `jaccard` | 0.40 | `\|poiIds(A) ∩ poiIds(B)\| / \|poiIds(A) ∪ poiIds(B)\|` | union empty → `0.0` (never `NaN`) |
| `startProximity` | 0.25 | `clamp(1 - GeoUtils.haversineMeters(A.start, B.start) / 5000.0, 0, 1)` | same point → `1.0`; ≥ 5 km → `0.0` |
| `categoryOverlap` | 0.20 | Jaccard over `preferences` sets | union empty → `0.0` |
| `timeSimilarity` | 0.15 | `1 - \|tA - tB\| / max(tA, tB, 1)` | identical → `1.0`; 30 vs 480 → `0.0625` |

Computation and rounding:

```text
sum  = 0.40*jaccard + 0.25*startProximity + 0.20*categoryOverlap + 0.15*timeSimilarity
score = roundHalfUp(clamp(sum, 0.0, 1.0), 4)      // BigDecimal setScale(4, HALF_UP)
```

The `clamp` upper bound absorbs the binary-drift of `0.40 + 0.25 + 0.20 + 0.15` so identical inputs return exactly `1.0`. The threshold compares the **rounded** value: `score >= new BigDecimal("0.35")`. Rounding before comparison is what makes `0.349` excluded and `0.350` admitted deterministically. `matchScore` in responses equals the stored `numeric(5,4)` value.

Suggestion assembly: load all shares where `owner_user_id <> caller` and `is_active = true` and (`expires_at is null or expires_at > now()`). For each target `B`, score every caller share `A`, keep the maximum; tie-break by `A.id` ascending. Drop `score < 0.35`, sort by `score` desc then `targetRouteId` ascending (deterministic ties), and map to `MatchSuggestionDto` with `matchedPoiIds` sorted ascending. Sources of complexity: `O(S_A × S_B)` Jaccard set operations on ≤ 50 ids each; a caller with no active share yields an empty list with `200`.

Match lifecycle state machine (owned by `route-match-service`, not common):

| Current | accept | reject |
|---|---|---|
| `PENDING` | record caller-side acceptance; both sides set → `ACCEPTED`, `acceptedAt`/`respondedAt` set; else stay `PENDING`, `200` | `REJECTED`, `respondedAt` set, `200` |
| `ACCEPTED` | `409 MATCH_TRANSITION_INVALID` | `409 MATCH_TRANSITION_INVALID` |
| `REJECTED` | `409 MATCH_TRANSITION_INVALID` | `409 MATCH_TRANSITION_INVALID` |
| unknown id | `404 MATCH_NOT_FOUND` | `404 MATCH_NOT_FOUND` |
| non-participant | `403 MATCH_ACCESS_DENIED` | `403 MATCH_ACCESS_DENIED` |

`POST /api/routes/matches/{id}/accept` on an `ACCEPTED` match is refused with `409` per spec, so a repeated accept after completion is an error, not a no-op. Within `PENDING`, the acceptance write is a conditional `UPDATE ... SET requester_accepted_at = ... WHERE id = ? AND status = 'PENDING' AND <own side> IS NULL`, so two concurrent accepts from the same side cannot both record consent, and the second acceptance flips the row to `ACCEPTED` under the same guard.

## 8. Persistence Design

One PostgreSQL 15 instance (`rutatur_db`), schema per service, **one role per service**. UUIDs are generated in the application (`UUID.randomUUID()`, v4) and assigned to the entity before persist; no `gen_random_uuid()` default, so tests can construct ids without a database.

### 8.1 `db/init` (executed once on an empty volume)

```sql
-- 00-roles.sql  (runs as POSTGRES_USER=rutatur)
CREATE ROLE rutatur_poi       LOGIN PASSWORD 'rutatur_poi';
CREATE ROLE rutatur_itinerary LOGIN PASSWORD 'rutatur_itinerary';
CREATE ROLE rutatur_match     LOGIN PASSWORD 'rutatur_match';
CREATE ROLE rutatur_kc        LOGIN PASSWORD 'rutatur_kc';
CREATE DATABASE keycloak OWNER rutatur_kc;
```

```sql
-- 01-poi-schema.sql
CREATE SCHEMA IF NOT EXISTS poi_schema AUTHORIZATION rutatur;
SET search_path TO poi_schema;
CREATE TABLE poi_cache (
  query_key   varchar(64)  NOT NULL,
  external_id varchar(64)  NOT NULL,
  name        text         NOT NULL,
  category    varchar(32)  NOT NULL,
  lat         numeric(9,6) NOT NULL,
  lng         numeric(9,6) NOT NULL,
  visit_time  integer      NOT NULL CHECK (visit_time > 0),
  rating      numeric(2,1) NOT NULL DEFAULT 0,
  address     text,
  tags        jsonb        NOT NULL DEFAULT '[]'::jsonb,
  cached_at   timestamptz  NOT NULL DEFAULT now(),
  expires_at  timestamptz  NOT NULL,
  PRIMARY KEY (query_key, external_id)
);
CREATE INDEX idx_poi_cache_lookup ON poi_cache (query_key, expires_at);
GRANT USAGE ON SCHEMA poi_schema TO rutatur_poi;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA poi_schema TO rutatur_poi;
```

```sql
-- 02-itinerary-schema.sql
CREATE SCHEMA IF NOT EXISTS itinerary_schema AUTHORIZATION rutatur;
SET search_path TO itinerary_schema;
CREATE TABLE itinerary_draft (
  id            uuid        PRIMARY KEY,
  request_data  jsonb       NOT NULL,
  response_data jsonb       NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_itinerary_draft_created ON itinerary_draft (created_at);

CREATE TABLE saved_itinerary (
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
CREATE INDEX idx_saved_itinerary_owner ON saved_itinerary (user_id, created_at DESC);
GRANT USAGE ON SCHEMA itinerary_schema TO rutatur_itinerary;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA itinerary_schema TO rutatur_itinerary;
```

```sql
-- 03-match-schema.sql
CREATE SCHEMA IF NOT EXISTS match_schema AUTHORIZATION rutatur;
SET search_path TO match_schema;
CREATE TABLE shared_route (
  id              uuid         PRIMARY KEY,
  itinerary_id    uuid         NOT NULL,
  owner_user_id   varchar(255) NOT NULL,
  start_lat       numeric(9,6) NOT NULL,
  start_lng       numeric(9,6) NOT NULL,
  available_time  integer      NOT NULL CHECK (available_time BETWEEN 30 AND 480),
  transport_mode  varchar(16)  NOT NULL,
  total_time      integer      NOT NULL CHECK (total_time >= 0),
  poi_ids         jsonb        NOT NULL,
  categories      jsonb        NOT NULL,
  is_active       boolean      NOT NULL DEFAULT true,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  expires_at      timestamptz  NOT NULL,
  CONSTRAINT uk_shared_route_itinerary UNIQUE (itinerary_id)
);
CREATE INDEX idx_shared_route_pool ON shared_route (is_active, expires_at);
CREATE INDEX idx_shared_route_owner ON shared_route (owner_user_id);

CREATE TABLE route_match (
  id                      uuid         PRIMARY KEY,
  requester_user_id       varchar(255) NOT NULL,
  responder_user_id       varchar(255) NOT NULL,
  shared_route_a_id       uuid         NOT NULL REFERENCES shared_route (id),
  shared_route_b_id       uuid         NOT NULL REFERENCES shared_route (id),
  match_score             numeric(5,4) NOT NULL CHECK (match_score BETWEEN 0 AND 1),
  matched_poi_count       integer      NOT NULL CHECK (matched_poi_count >= 0),
  matched_pois            jsonb        NOT NULL,
  status                  varchar(16)  NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
  requester_accepted_at   timestamptz,
  responder_accepted_at   timestamptz,
  requested_at            timestamptz  NOT NULL DEFAULT now(),
  responded_at            timestamptz,
  accepted_at             timestamptz
);
CREATE UNIQUE INDEX uk_route_match_open
  ON route_match (requester_user_id, shared_route_b_id) WHERE status = 'PENDING';
CREATE INDEX idx_route_match_requester ON route_match (requester_user_id, requested_at DESC);
CREATE INDEX idx_route_match_responder ON route_match (responder_user_id, requested_at DESC);

CREATE TABLE user_contact_view (
  id            uuid         PRIMARY KEY,
  route_match_id uuid        NOT NULL REFERENCES route_match (id),
  viewer_user_id varchar(255) NOT NULL,
  viewed_user_id varchar(255) NOT NULL,
  revealed_at   timestamptz  NOT NULL DEFAULT now(),
  contact_info  jsonb        NOT NULL
);
CREATE INDEX idx_user_contact_view_match ON user_contact_view (route_match_id);
GRANT USAGE ON SCHEMA match_schema TO rutatur_match;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA match_schema TO rutatur_match;
```

Deliberate omissions from `docs/02 §2.9`: `itinerary_stop` (stops live inside `response_data`; no scenario queries a stop row) and `shared_route.share_token` (the row id is already an unguessable UUID). `saved_itinerary` keeps no FK to `itinerary_draft` so drafts can be purged without touching history; the saved row owns its full snapshot.

### 8.2 JPA and schema rules

| Rule | Implementation |
|---|---|
| One entity package per service | `com.rutatur.<service>.persistence` — no entity is imported across modules |
| Schema binding | `spring.jpa.properties.hibernate.default_schema=<schema>` + `@Table(schema="...")` for clarity |
| Validation | `spring.jpa.hibernate.ddl-auto=validate` — startup fails if DDL and entity disagree |
| JSONB | Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)` on `JsonNode`/`List<String>` fields |
| Decimals | `numeric(5,4)` → `BigDecimal` with `precision=5, scale=4`; `numeric(9,6)` → `BigDecimal` for coordinates |
| Times | `Instant` ↔ `timestamptz`; no `java.util.Date` |
| Cross-schema | impossible by grant: each service's role has `USAGE` on exactly one schema |
| Schema init | `docker-entrypoint-initdb.d` (mounted read-only, lexical order, once per empty volume); restart safety comes from the `postgres-data` volume, and every statement is `IF NOT EXISTS`-guarded |
| Draft expiry | reads filter `created_at > now() - interval '24 hours'`; expired drafts remain rows but are invisible, and `saved_itinerary` is never affected |

## 9. Security Design

### 9.1 Realm, clients, roles

| Object | Configuration |
|---|---|
| Realm | `rutatur-realm`, access token lifespan 15 min, SSL not required (dev) |
| Client `rutatur-api` | public client, standard flow + direct access grants, used by curl/Swagger/`user1` to obtain tokens |
| Client `rutatur-gateway` | confidential, service account **disabled** (gateway only validates) |
| Client `rutatur-match` | confidential, service account enabled, realm-management role `view-users` |
| Roles | realm roles `ROLE_USER`, `ROLE_ADMIN` (exact names, `ROLE_` prefix included) |
| Users | `user1`/`password` → `ROLE_USER`; `admin1`/`password` → `ROLE_USER`, `ROLE_ADMIN` |
| Claims used | `sub` = owner identity; `preferred_username` (display only); `realm_access.roles` = authorities |

### 9.2 Issuers: in-network vs host

`docker compose up` serves Keycloak on container `8080` and host `8180`. Tokens requested from `http://localhost:8180/...` must still validate inside the network, so the issuer is pinned server-side:

| Setting | Value |
|---|---|
| `KC_HOSTNAME` | `http://keycloak:8080` |
| `KC_HTTP_ENABLED` | `true` |
| `KC_HEALTH_ENABLED` | `true` |
| `KC_DB`, `KC_DB_URL`, `KC_DB_USERNAME/PASSWORD` | `postgres`, `jdbc:postgresql://postgres:5432/keycloak`, `rutatur_kc` |
| Service `JWT_ISSUER_URI` | `http://keycloak:8080/realms/rutatur-realm` (identical in all four JVM services) |
| Token endpoint used by clients | `http://localhost:8180/realms/rutatur-realm/protocol/openid-connect/token` |

Keycloak therefore signs `iss=http://keycloak:8080/realms/rutatur-realm` regardless of the request host, and both host and container clients obtain tokens that the gateway and services accept. The gateway validates signature via JWKS, `iss`, and `exp`/`nbf`; the host name `keycloak` never needs to resolve from the host because only bearer tokens cross that boundary.

### 9.3 Gateway validation strategy

`SecurityWebFilterChain` order:

1. `OPTIONS /**` → `permitAll` with CORS preflight handled before authentication.
2. `Path=/actuator/health,/swagger-ui/**,/v3/api-docs/**,/webjars/**` → `permitAll`.
3. `Path=/api/pois/search` and `POST /api/itinerary/generate` → `permitAll` **with** resource-server authentication still installed, so a supplied bearer token is parsed and an invalid one fails with `401` before authorization.
4. `Path=/api/admin/**` → `hasRole("ADMIN")` (authority `ROLE_ADMIN`).
5. `Path=/api/**` → `hasRole("USER")`.
6. `anyExchange().denyAll()` — `/internal/**` never matches a route, and this denies it even if a route were added later.

Invalid bearer tokens are rejected by the reactive `AuthenticationWebFilter` with `401` on all paths, including the public ones; missing tokens on public paths pass through. `KeycloakRealmRoleConverter` (in `rutatur-common`) maps `realm_access.roles` to `ROLE_*` authorities, which is why `hasRole("USER")` matches `ROLE_USER`.

CORS: allowed origins from `RUTATUR_CORS_ORIGINS` (default `http://localhost:3000,http://localhost:8080`), methods `GET,POST,DELETE,OPTIONS`, headers `Authorization,Content-Type`, max age 3600. Documentation routes are public and advertise only the proxied prefixes.

### 9.4 Resource servers and internal endpoints

The three functional services are OAuth2 resource servers with the same `JwtDecoder` shape: `jwk-set-uri = ${JWT_ISSUER_URI}/protocol/openid-connect/certs` plus `JwtValidators.createDefaultWithIssuer(${JWT_ISSUER_URI})`, so `iss` is checked without a startup discovery call. `@PreAuthorize("hasRole('USER')")`/`hasRole('ADMIN')` duplicate the gateway rule (defense in depth); ownership is always derived from `Jwt.getSubject()`, never from a request parameter.

`/internal/**` is protected three ways: (1) no gateway route and `denyAll()`, (2) `X-Internal-Token` compared to `RUTATUR_INTERNAL_TOKEN` by a servlet filter registered for `/internal/**` only (`401` otherwise), (3) published service ports bound to `127.0.0.1` so the host is not a public ingress. Callers set the header from the same environment variable; the gateway strips any inbound `X-Internal-Token` so a client cannot smuggle it.

### 9.5 Authorization matrix

| Endpoint | Gateway | Service | Role | Ownership / extra |
|---|---|---|---|---|
| `GET /api/pois/search` | public, optional JWT | permitAll | — | — |
| `POST /api/itinerary/generate` | public, optional JWT | permitAll | — | draft is unowned; any authenticated caller may claim it once |
| `POST /api/itineraries` | `ROLE_USER` | `hasRole('USER')` | USER | claim of a draft; `sub` is the owner |
| `GET /api/itineraries` | `ROLE_USER` | `hasRole('USER')` | USER | filter `user_id = sub` |
| `GET /api/itineraries/{id}` | `ROLE_USER` | `hasRole('USER')` | USER | owner or `404` |
| `DELETE /api/itineraries/{id}` | `ROLE_USER` | `hasRole('USER')` | USER | owner or `404` |
| `GET /api/admin/itineraries` | `ROLE_ADMIN` | `hasRole('ADMIN')` | ADMIN | `userId` query required |
| `POST /api/routes/share` | `ROLE_USER` | `hasRole('USER')` | USER | itinerary owner or `404` |
| `GET /api/routes/shareable` | `ROLE_USER` | `hasRole('USER')` | USER | own itineraries only |
| `GET /api/routes/matches/suggestions` | `ROLE_USER` | `hasRole('USER')` | USER | excludes own shares |
| `POST /api/routes/matches/request` | `ROLE_USER` | `hasRole('USER')` | USER | target active share, not own |
| `GET /api/routes/matches` | `ROLE_USER` | `hasRole('USER')` | USER | sent ∪ received only |
| `POST /api/routes/matches/{id}/accept` | `ROLE_USER` | `hasRole('USER')` | USER | participant, `PENDING` |
| `POST /api/routes/matches/{id}/reject` | `ROLE_USER` | `hasRole('USER')` | USER | participant, `PENDING` |
| `GET /api/routes/matches/{id}/contact` | `ROLE_USER` | `hasRole('USER')` | USER | participant and `ACCEPTED` |
| `GET /internal/**` | no route → 404 | internal token filter | — | network + header |
| `/actuator/health` | no route → 404 | permitAll | — | in-network only |

## 10. Runtime Topology

### 10.1 Images and build

One root `Dockerfile`, parameterized by `ARG MODULE`, so the reactor and layer cache are shared:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY rutatur-common/pom.xml rutatur-common/
COPY poi-service/pom.xml poi-service/
COPY itinerary-service/pom.xml itinerary-service/
COPY route-match-service/pom.xml route-match-service/
COPY api-gateway/pom.xml api-gateway/
RUN mvn -B -q -DskipTests dependency:go-offline || true
COPY . .
ARG MODULE
RUN mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
RUN addgroup -S rutatur && adduser -S rutatur -G rutatur
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/app.jar app.jar
USER rutatur
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Compose builds with `build: { context: ., args: { MODULE: poi-service } }` etc. Healthcheck uses BusyBox `wget`, present in the alpine JRE image: `wget -qO- http://localhost:<port>/actuator/health | grep -q '"status":"UP"'`.

### 10.2 Services

| Service | Port host:container | Env (beyond `SPRING_PROFILES_ACTIVE=docker`) | Healthcheck | `depends_on` |
|---|---|---|---|---|
| `postgres` | `127.0.0.1:5432:5432` | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB=rutatur_db` | `pg_isready -U rutatur -d rutatur_db` | — |
| `keycloak` | `8180:8080` | `KC_DB`, `KC_DB_URL`, `KC_DB_USERNAME/PASSWORD`, `KC_HOSTNAME=http://keycloak:8080`, `KC_HTTP_ENABLED=true`, `KC_HEALTH_ENABLED=true`, `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`; `command: start-dev --import-realm`; mounts `./keycloak:/opt/keycloak/data/import:ro` | `bash -c '{ printf "GET /health/ready HTTP/1.0\r\n\r\n" >&3; grep -q "200" <&3; } 3<>/dev/tcp/localhost/9000'` | `postgres: service_healthy` |
| `poi-service` | `127.0.0.1:8081:8081` | `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/rutatur_db?currentSchema=poi_schema`, `SPRING_DATASOURCE_USERNAME=rutatur_poi`, `SPRING_DATASOURCE_PASSWORD`, `RUTATUR_OVERPASS_URL`, `RUTATUR_INTERNAL_TOKEN` | wget `/actuator/health` | `postgres: service_healthy` |
| `itinerary-service` | `127.0.0.1:8082:8082` | same pattern with `rutatur_itinerary`/`itinerary_schema`, `RUTATUR_POI_BASE_URL=http://poi-service:8081`, `RUTATUR_OSRM_BASE_URL`, `RUTATUR_INTERNAL_TOKEN`, `JWT_ISSUER_URI` | wget `/actuator/health` | `postgres`, `poi-service: service_healthy` |
| `route-match-service` | `127.0.0.1:8083:8083` | `rutatur_match`/`match_schema`, `RUTATUR_ITINERARY_BASE_URL=http://itinerary-service:8082`, `KEYCLOAK_ADMIN_URL=http://keycloak:8080`, `KEYCLOAK_MATCH_CLIENT_SECRET`, `RUTATUR_INTERNAL_TOKEN`, `JWT_ISSUER_URI` | wget `/actuator/health` | `postgres`, `keycloak`, `itinerary-service: service_healthy` |
| `api-gateway` | `8080:8080` | `JWT_ISSUER_URI`, `RUTATUR_CORS_ORIGINS`, route URIs (`POI_SERVICE_URI`, `ITINERARY_SERVICE_URI`, `ROUTE_MATCH_SERVICE_URI`) | wget `/actuator/health` | all three services `service_healthy`, `keycloak: service_healthy` |
| `pgadmin` | `5050:80` | `PGADMIN_DEFAULT_EMAIL`, `PGADMIN_DEFAULT_PASSWORD` | — | profile `tools` only |

Networking: one bridge network `rutatur-network`; only the gateway is published on all interfaces (`8080:8080`), Keycloak on `8180:8080`. Volumes: `postgres-data` only — Keycloak state lives in its `keycloak` database, so a separate `keycloak-data` volume (named in `docs/02 §2.12`) is unnecessary and omitted to avoid a second source of truth. `--import-realm` skips a realm that already exists, and every init script is `IF NOT EXISTS`-guarded, so a second `docker compose up -d` is clean.

Every credential comes from `.env` / environment variables (`POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_MATCH_CLIENT_SECRET`, `RUTATUR_INTERNAL_TOKEN`); `.env.example` documents them and no default secret is written into a Dockerfile or `application.yml`.

## 11. Sequence Diagrams

### 11.1 Itinerary generation (public) and save

```mermaid
sequenceDiagram
    participant C as Client
    participant G as api-gateway
    participant I as itinerary-service
    participant P as poi-service
    participant O as OSRM
    participant DB as itinerary_schema
    C->>G: POST /api/itinerary/generate (no token)
    G->>I: forward (public route)
    I->>I: validate ItineraryRequestDto (400 on failure)
    I->>P: GET /internal/pois/candidates (+X-Internal-Token)
    P->>P: cache hit? else Overpass + normalize + radius filter
    P-->>I: PoiSearchResponse
    I->>I: dedupe, score, total-order, truncate to 50
    I->>O: GET /table/v1/{profile}/{start;candidates}
    O-->>I: durations[][], distances[][]
    I->>I: greedy loop (skip non-fitting, maxPois, <= availableTime)
    alt no stop fits
        I-->>G: 422 ITINERARY_NOT_FEASIBLE
        G-->>C: 422 + ApiError
    else at least one stop
        I->>DB: INSERT itinerary_draft (request/response JSONB)
        I-->>G: 200 ItineraryResponseDto (itineraryId)
        G-->>C: 200
    end
    C->>G: POST /api/itineraries {itineraryId} (Bearer user1)
    G->>G: validate JWT + ROLE_USER
    G->>I: forward (sub propagated)
    I->>DB: SELECT draft (not expired) ; INSERT saved_itinerary(id = draft.id, user_id = sub)
    alt draft unknown/expired
        I-->>C: 404 ITINERARY_NOT_FOUND
    else already claimed
        I-->>C: 409 ITINERARY_ALREADY_SAVED
    else claimed
        I-->>C: 201 SavedItineraryResponse
    end
```

### 11.2 Route sharing, matching, mutual consent, contact reveal

```mermaid
sequenceDiagram
    participant A as User A
    participant B as User B
    participant G as api-gateway
    participant M as route-match-service
    participant I as itinerary-service
    participant K as Keycloak
    participant DB as match_schema
    A->>G: POST /api/routes/share {itineraryId} (Bearer A)
    G->>M: forward (ROLE_USER, sub=A)
    M->>I: GET /internal/itineraries/{id} (+X-Internal-Token)
    alt itinerary not found
        I-->>M: 404
        M-->>A: 404 SHARE_NOT_FOUND (no share written)
    else unavailable
        M-->>A: 503 UPSTREAM_UNAVAILABLE
    else snapshot
        I-->>M: InternalItineraryDto
        M->>DB: upsert shared_route (active, expiresAt) + audit-free
        M-->>A: 201 SharedRouteDto
    end
    B->>G: POST /api/routes/share {itineraryIdB}
    G->>M: forward (sub=B) ; M stores B route
    A->>G: GET /api/routes/matches/suggestions
    G->>M: forward (sub=A)
    M->>DB: load active, non-expired shares of other users
    M->>M: score 0.40 J + 0.25 prox + 0.20 cat + 0.15 time, keep >= 0.35
    M-->>A: MatchSuggestionDto[] (no contact, no coordinates)
    A->>G: POST /api/routes/matches/request {targetRouteId}
    G->>M: forward (sub=A)
    M->>M: recompute score, select A's best share
    M->>DB: INSERT route_match (PENDING, requester=A, responder=B)
    M-->>A: 201 MatchDto PENDING
    B->>G: POST /api/routes/matches/{id}/accept
    G->>M: forward (sub=B)
    M->>DB: UPDATE ... SET responder_accepted_at (status stays PENDING)
    M-->>B: 200 MatchDto PENDING
    A->>G: GET /api/routes/matches/{id}/contact
    M-->>A: 403 CONTACT_NOT_REVEALED (not ACCEPTED)
    A->>G: POST /api/routes/matches/{id}/accept
    G->>M: forward (sub=A)
    M->>DB: UPDATE ... SET requester_accepted_at, status=ACCEPTED, accepted_at
    M-->>A: 200 MatchDto ACCEPTED
    A->>G: GET /api/routes/matches/{id}/contact
    G->>M: forward (sub=A)
    M->>K: POST /token (client_credentials rutatur-match) + GET /admin/realms/rutatur-realm/users/{B}
    K-->>M: {username, email}
    M->>DB: INSERT user_contact_view (match, viewer=A, viewed=B, payload)
    M-->>A: 200 ContactDto
```

## 12. Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (common) | `GeoUtils` against reference pairs (1 % tolerance, 0 for identical), enum closure, validator boundaries (30/480, 100/3000, 1/5000, maxPois 0), `RealmRoleConverter` on a decoded `Jwt`, `ErrorCode` ↔ status consistency | JUnit 5 + AssertJ, no Spring context |
| Unit (poi) | Overpass JSON → `PoiDto` mapping (missing name, missing coords, unknown tag), radius containment at 1000 m vs 1200 m, cache key stability, category default `visitTime` | Mockito-stubbed HTTP client, parameterized tests |
| Unit (itinerary) | scoring/ordering, dedupe, skip-and-continue, `maxPois` cap, `totalTime` invariant, `422` when nothing fits, `503` on provider failure, claim `404/409` | plain JUnit on the optimizer + service with mocked ports |
| Unit (match) | each similarity component at 0 and 1, identical routes → 1.0, threshold 0.349/0.350, deterministic tie order, state machine matrix, audit written only on success | plain JUnit, fixed UUID fixtures |
| Web slice | MockMvc per service: 400/401/403/404/409/422/503 mapping, `ApiError` fields, `X-Internal-Token` filter on `/internal/**`, no token leaking into responses | `@WebMvcTest` + `@Import(SecurityConfig)`; gateway uses `WebTestClient` |
| Persistence | schema/entity agreement and repository queries | `@DataJpaTest` against H2? **No** — H2 diverges on `jsonb`; gated Testcontainers PostgreSQL suite in W7, skipped when the daemon is down |
| Contract | outbound request shape captured by a stub server (path, query, headers) for poi candidates, OSRM table, Keycloak Admin | `MockRestServiceServer` / WireMock-free stubs |
| E2E | login → generate → save → share (two users) → suggestion ≥ 0.35 → request → mutual accept → contact; `403` before acceptance | `scripts/smoke.sh` over the running Compose stack, upstream-tolerant |

## 13. Threat Matrix

| Boundary | Applicability | Design response | Planned RED tests |
|---|---|---|---|
| Documentation-like paths (`requirements.txt`, `CMakeLists.txt`, executable Markdown, `README.sh`) | N/A — no file-classification or doc-execution feature; the only scripts are the fixed `scripts/smoke.sh` and Maven/Docker invocations | — | — |
| Git repository selection (`git -C`, relative/absolute paths) | N/A — no VCS automation in this change | — | — |
| Commit state (staged, `commit -a`, empty index) | N/A — no commit automation | — | — |
| Push state (tracking branch, first push, refspec) | N/A — no push automation | — | — |
| PR commands (`--head`, env prefix, composed commands) | N/A — no PR automation; chained PRs are a delivery convention, not code | — | — |

No routing/shell/process boundary exists in the implemented platform beyond fixed Compose commands, so no adversarial case is manufactured. HTTP boundaries are covered by the gateway/security tests in §12.

## 14. Wave → Design Mapping

| Wave | Modules | Slices | Contents after design | Done proof |
|---|---|---|---|---|
| W1 Bootstrap | root pom, 5 module poms, `.mvn/`, `Dockerfile` | 2 | version pins, enforcer (`requireJavaVersion`, `bannedDependencies`, `banDuplicateClasses`), `finalName`, one context test per module | `mvn -B verify` green; a deliberate `spring-boot-starter-web` in the gateway fails the build |
| W2 Common | `rutatur-common` | 4 (was 3) | (1) enums + `GeoUtils`, (2) POI/itinerary DTOs, (3) match/share/admin DTOs, (4) exceptions + `ErrorCode` + `ApiError` + validators + `RealmRoleConverter` | unit tests incl. Haversine reference pairs |
| W3 POI | `poi-service` | 4 | Overpass client + Resilience4j, normalization + containment, cache + degradation, public/internal endpoints + internal-token filter | MockMvc search/candidates, stubbed Overpass JSON, stale-cache fallback |
| W4 Itinerary | `itinerary-service` | 5 | (1) poi + OSRM clients, (2) optimizer, (3) draft + save/list/get/delete, (4) internal endpoints + admin listing, (5) security/config wiring | optimizer feasibility/determinism, claim `404/409`, offline list |
| W5 Route match | `route-match-service` | 4 | (1) sharing + snapshot, (2) similarity + suggestions, (3) lifecycle/inbox/consent, (4) contact + Keycloak Admin + audit | score-weighted tests, `403` before acceptance, `409` matrix |
| W6 Gateway | `api-gateway` | 2 | (1) routes + security chain + header stripping, (2) CORS + docs aggregation + reactive error handler | 401/403/404 tests, `/internal/**` unreachable, preflight without token |
| W7 Integration | Compose, `db/init`, `keycloak/`, `scripts/` | 2 | (1) Compose + init SQL + `.env.example` + Dockerfile wiring, (2) realm export + smoke script + README build/run fix | all healthy; full smoke incl. mutual consent |

Deltas versus the proposal: +1 slice in W2 (new error/security types), W4 absorbs the draft model and admin endpoint inside its existing 5 slices, W5 absorbs the Keycloak Admin client inside its 4. Total **23 slices**, each bounded by the 400-line budget; only W3 is estimated near the ceiling (~280 lines per slice) because normalization tests are large.

## 15. Migration / Rollout

No data migration — greenfield, no existing schema or users. Rollout is wave order, each wave independently revertible. Compose data survives restarts through `postgres-data`; `docker compose down -v` is the documented reset. Draft TTL (24 h) means a generated itinerary cannot be saved after a day; the smoke script generates and saves within one run.

## 16. Spec Tension Register

| # | Tension | Type | Resolution |
|---|---|---|---|
| T1 | `platform-deployment` requires host exposure of 8081/8082/8083/5432, while `gateway-security` requires that "a client calls a functional service port from outside the network … the call MUST fail". Both `MUST`s cannot hold for a host-local client. | **Genuine contradiction — interpreted, not silently diverged** | Service and Postgres ports are published as `127.0.0.1:<port>:<port>`, the gateway stays `8080:8080` on all interfaces. "Outside the network" is read as *any source that is not on `rutatur-network`*; the RED test executes a `curl http://poi-service:8081` from a container attached to a foreign docker network and asserts failure, and separately asserts that `localhost:8081` works only from the host loopback. This keeps dev access required by `platform-deployment` while preserving the single-ingress property for network clients. |
| T2 | `route-sharing` needs the caller's itinerary ids, but `itinerary-history` defines only `GET /internal/itineraries/{id}`. | Design addition (no spec edit) | Add `GET /internal/itineraries?owner={sub}` (Q6). |
| T3 | `gateway-security` demands an `/api/admin/**` route; no service declares an admin endpoint. | Design addition (no spec edit) | Add `GET /api/admin/itineraries` (Q5). |
| T4 | `route-matching` requires "the counterpart's minimal fields", but no service owns contact data. | Design addition (no spec edit) | Keycloak Admin API at reveal time (Q7). |
| T5 | `itinerary-history` returns both `id` and `itineraryId`; nothing says whether they differ. | Interpretation | They are equal by design (Q3); `itineraryId` names the generation artifact, `id` names the saved record, and both remain populated for contract compatibility. |

## 17. Open Questions

- [ ] ADR numbering: `docs/decisions/README.md` already lists `ADR-002` as "Uso de API Gateway" (file absent). The new superseding ADR is required at `ADR-002-tres-servicios-funcionales.md`; the index needs a correction row (one-line docs change) during the tasks phase.
- [ ] T1: whether the graded environment accepts loopback-bound service ports as satisfying `gateway-security`'s "direct service port closed".
- [ ] Exact Spring Cloud patch: if `2023.0.5` fails to resolve with Boot 3.3.5 on the first online build, step to the newest `2023.0.x` inside W1 and record it in the pom.
