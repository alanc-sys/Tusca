# Tasks: RutaTur Platform Implementation

Change: `rutatur-platform` · Artifact store: openspec + Engram mirror · Delivery: `auto-chain` · Review budget: 400 changed lines (`additions + deletions`) per slice.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 6,800 (W1 600 · W2 900 · W3 1,100 · W4 1,600 · W5 1,300 · W6 600 · W7 700) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | 23 slices → PR 1…PR 23, one wave per group (see Suggested Work Units) |
| Delivery strategy | auto-chain |
| Chain strategy | pending — orchestrator asks the user |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

Each of the 23 slices below **is** a PR work unit; per-slice goal, files, line estimate, done proof, rollback boundary and focused test command content are declared in its own block. Wave rollup:

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| W1 | Buildable reactor on JDK 21 with enforced starter isolation | PR 1–2 | `mvn -B verify` | `bash scripts/verify-enforcer.sh` (must fail with a starter added) | root + 5 module poms, `.gitignore`, main classes; no dependents |
| W2 | Shared contract JAR | PR 3–6 | `mvn -B -pl rutatur-common -am test` | N/A — library, no runtime boundary | `rutatur-common/src/**`; dependents revert in reverse order |
| W3 | POI search service | PR 7–10 | `mvn -B -pl poi-service -am test` | `mvn -B -pl poi-service spring-boot:run` + `curl :8081/api/pois/search` | `poi-service/**` only |
| W4 | Itinerary generation + history | PR 11–15 | `mvn -B -pl itinerary-service -am test` | `spring-boot:run` + `curl -X POST :8082/api/itinerary/generate` with stubbed upstream | `itinerary-service/**` only |
| W5 | Sharing, matching, consent, reveal | PR 16–19 | `mvn -B -pl route-match-service -am test` | `spring-boot:run` + two-user share/match script against stubs | `route-match-service/**` only |
| W6 | Gateway ingress | PR 20–21 | `mvn -B -pl api-gateway -am test` | `spring-boot:run` + `curl :8080/api/pois/search`, `/internal/**`, preflight | `api-gateway/**` only |
| W7 | Runnable, verified stack | PR 22–23 | `bash scripts/verify-network-isolation.sh` | `docker compose up -d` + `bash scripts/smoke.sh` | `docker-compose.yml`, `Dockerfile`, `db/init/`, `keycloak/`, `scripts/`, docs; `docker compose down -v` |

## Conventions

- Numbering: `W{wave}.{slice}.{T1=RED, I1=GREEN, R1=REFACTOR/VERIFY}`. Strict TDD: the named test MUST fail before its implementation task starts.
- Test commands: module `mvn -B -pl <module> -am test`; single `mvn -B -pl <module> test -Dtest=<Class>#<method>`; full `mvn -B verify`. No `pom.xml` exists yet, so **W1.1 unblocks every later command**.
- Slice boundary deviations from design §14: **none** — all 23 slices build and test independently.
- Line estimates count authored additions + deletions; generated `keycloak/rutatur-realm.json` is excluded from authored count but stays in the diff.

---

## W1 — Bootstrap (PR 1–2, ~600 lines)

### W1.1 — Reactor skeleton, version pins, Spring Cloud verification · PR 1 · ~330 lines

**Goal**: `mvn -B verify` runs a five-module reactor on JDK 21 with pinned versions and no manual `mvn install` step.
**Files (module root `pom.xml` + module poms)**: `pom.xml`, `rutatur-common/pom.xml`, `poi-service/pom.xml`, `itinerary-service/pom.xml`, `route-match-service/pom.xml`, `api-gateway/pom.xml`, `.gitignore`, `.mvn/jvm.config`.
**W1.1.T1 (RED)** — `rutatur-common/src/test/java/com/rutatur/common/build/ReactorBuildContractTest.java`: pins `java.version=21`, `maven.compiler.release=21`, module order, `spring-boot-starter-parent` 3.3.5, `spring-cloud-dependencies` property, `finalName=app` on executable modules, and `rutatur-common` without `spring-boot-maven-plugin`. Fails until poms exist.
**W1.1.I1 (GREEN)** — write root parent/aggregator (packaging `pom`, dependency + plugin management, `spring-cloud-dependencies` 2023.0.5 import, `maven.compiler.release=21`, `project.build.sourceEncoding=UTF-8`); module poms with dependencies only (no versions) per design §1.2; `finalName=app`.
**W1.1.R1 (VERIFY)** — confirm `spring-cloud-dependencies:2023.0.5` resolves against Boot 3.3.5; if resolution fails, step to the newest `2023.0.x`, record the exact version in the root pom and in `design.md` §1.1, then re-run.
**Done**: `mvn -B verify` green under `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`.
**Rollback**: delete root + module poms and `.gitignore`; nothing else depends on them yet.

### W1.2 — Enforcer, module skeletons, build docs · PR 2 · ~270 lines

**Goal**: fail fast on wrong JDK, Maven, or servlet/reactive starter mixing; every module has an entry point.
**Files**: `pom.xml` (enforcer plugin), `rutatur-common/src/test/java/com/rutatur/common/build/BuildGuardPomTest.java`, `poi-service/src/main/java/com/rutatur/poi/PoiServiceApplication.java`, `itinerary-service/src/main/java/com/rutatur/itinerary/ItineraryServiceApplication.java`, `route-match-service/src/main/java/com/rutatur/match/RouteMatchServiceApplication.java`, `api-gateway/src/main/java/com/rutatur/gateway/ApiGatewayApplication.java`, `scripts/verify-enforcer.sh`, `README.md` (build section).
**W1.2.T1 (RED)** — `BuildGuardPomTest` (JDK XML parser, no new dependency): asserts `requireJavaVersion` range `[21,22)`, `requireMavenVersion` `[3.9,)`, `banDuplicateClasses`, and gateway `bannedDependencies` containing `spring-boot-starter-web`, `spring-boot-starter-tomcat`, `spring-boot-starter-data-jpa`; servlet modules banning `spring-boot-starter-webflux`, `spring-cloud-starter-gateway`, `reactor-netty-http`.
**W1.2.I1 (GREEN)** — add the enforcer rules and the four `@SpringBootApplication` classes; write `scripts/verify-enforcer.sh` (temporarily injects `spring-boot-starter-web` into `api-gateway/pom.xml`, expects a non-zero build, restores the file).
**W1.2.R1 (REFACTOR/VERIFY)** — run `mvn -B verify` then `bash scripts/verify-enforcer.sh` (must fail the injected build); document `export JAVA_HOME=...` and `mvn -B verify` in `README.md` with **no** manual `mvn install` step.
*Deviation note*: per-module context tests are deferred to W3–W6 because `@SpringBootTest` context loads here would need DataSource/JPA/security exclusions that later slices remove.
**Done**: `mvn -B verify` green; `bash scripts/verify-enforcer.sh` exits 0 only when the injected starter fails the build.
**Rollback**: remove enforcer block, main classes, script; W1.1 still verifies.

---

## W2 — rutatur-common (PR 3–6, ~900 lines)

### W2.1 — Enums and `GeoUtils` · PR 3 · ~220 lines

**Goal**: closed vocabulary and the single Haversine implementation.
**Files**: `rutatur-common/src/main/java/com/rutatur/common/enums/{Category,TransportMode,MatchStatus}.java`, `rutatur-common/src/main/java/com/rutatur/common/util/GeoUtils.java`.
**W2.1.T1 (RED)** — `rutatur-common/src/test/java/com/rutatur/common/util/GeoUtilsTest.java`: identical points return exactly `0.0`; reference pair within 1 % (mean radius 6 371 008.8 m); `GeoPointDto` overload matches the primitive overload. `…/common/enums/EnumVocabularyTest.java`: exactly 10 categories, 2 transport modes, 3 match statuses.
**W2.1.I1 (GREEN)** — implement `haversineMeters` (short-circuit identical coords before trig), enums per design §4.1.
**W2.1.R1 (VERIFY)** — `mvn -B -pl rutatur-common -am test`; confirm no other distance helper exists.
**Done**: `mvn -B -pl rutatur-common test -Dtest=GeoUtilsTest,EnumVocabularyTest`.
**Rollback**: delete `enums/` + `util/GeoUtils.java`; nothing else compiled yet.

### W2.2 — POI and itinerary DTOs with binding validation · PR 4 · ~260 lines

**Goal**: generation/search request contracts that reject out-of-range input at the edge.
**Files**: `rutatur-common/src/main/java/com/rutatur/common/dto/{GeoPointDto,PoiDto,PoiSearchQuery,PoiSearchResponse,ItineraryRequestDto,ItineraryStopDto,ItineraryResponseDto,SaveItineraryRequest,SavedItineraryResponse,SavedItinerarySummary,SavedItineraryDetail}.java`.
**W2.2.T1 (RED)** — `…/common/dto/ItineraryRequestValidationTest.java`: accepts `availableTime` 30 and 480, `radius` 100 and 3000; rejects 29/481, 99/3001, empty `preferences`, `maxPois=0`, out-of-range coordinates. `…/common/dto/PoiSearchQueryValidationTest.java`: accepts `radius` 1 and 5000, rejects 0 and 5001.
**W2.2.I1 (GREEN)** — annotate per design §4.2 including the two distinct radius ranges (Q2); `PoiDto.id` `@NotBlank`; `LocalTime` fields `@JsonFormat("HH:mm")`.
**W2.2.R1 (VERIFY)** — `mvn -B -pl rutatur-common test -Dtest=ItineraryRequestValidationTest,PoiSearchQueryValidationTest`; confirm a `Validator` built without a Spring context.
**Done**: both validation classes green; inclusive boundaries accepted.
**Rollback**: delete the listed DTOs; `GeoUtils` and enums stay usable.

### W2.3 — Share, match, admin and internal DTOs · PR 5 · ~200 lines

**Goal**: route-sharing/matching contracts, contact-free by construction.
**Files**: `rutatur-common/src/main/java/com/rutatur/common/dto/{SharedRouteDto,ShareableItineraryDto,MatchSuggestionDto,MatchRequestDto,ShareRouteRequest,MatchDto,ContactDto,InternalItineraryDto,InternalItinerarySummary}.java`.
**W2.3.T1 (RED)** — `…/common/dto/MatchContractSerializationTest.java`: camelCase JSON, ISO-8601 UTC `Instant`, `matchScore` 4 dp, `matchedPoiIds` sorted, `MatchSuggestionDto` serializes no contact/coordinates/user id, `ContactDto` exposes only `userId`, `username`, `email`.
**W2.3.I1 (GREEN)** — implement records/classes per design §4.2.
**W2.3.R1 (VERIFY)** — `mvn -B -pl rutatur-common test -Dtest=MatchContractSerializationTest`.
**Done**: serialization test green and asserts absence of contact fields in `MatchSuggestionDto`.
**Rollback**: delete the listed DTOs; generation DTOs and enums unaffected.

### W2.4 — Exceptions, `ErrorCode`, `ApiError`, validators, `RealmRoleConverter` · PR 6 · ~220 lines

**Goal**: one error vocabulary where code and HTTP status can never disagree.
**Files**: `rutatur-common/src/main/java/com/rutatur/common/exception/{RutaturException,InvalidRequestException,ResourceNotFoundException,BusinessException,ForbiddenActionException,ConflictException,UpstreamServiceException}.java`, `…/common/error/{ErrorCode,ApiError}.java`, `…/common/validation/{EnumValue,EnumValueValidator}.java`, `…/common/security/RealmRoleConverter.java`.
**W2.4.T1 (RED)** — `…/common/error/ErrorCodeStatusTest.java`: every code's `httpStatus()` matches design §4.4 (404 `ITINERARY_NOT_FOUND`/`SHARE_NOT_FOUND`/`MATCH_NOT_FOUND`; 422; 403 `MATCH_ACCESS_DENIED`/`CONTACT_NOT_REVEALED`; 409; 503). `…/common/security/RealmRoleConverterTest.java`: decoded `Jwt` with `realm_access.roles=["ROLE_USER"]` yields authority `ROLE_USER`; missing claim yields empty. `…/common/validation/EnumValueValidatorTest.java`: unknown category/mode fails validation.
**W2.4.I1 (GREEN)** — implement hierarchy, `ErrorCode` carrying status, `ApiError` with `violations` only for 400, enum validator, role converter.
**W2.4.R1 (VERIFY)** — `mvn -B -pl rutatur-common -am test`; assert `rutatur-common` pom has no servlet/reactive starter.
**Done**: full `mvn -B -pl rutatur-common -am test` green.
**Rollback**: delete `exception/`, `error/`, `validation/`, `security/`; DTOs still compile (no exception dependency).

---

## W3 — poi-service (PR 7–10, ~1,100 lines)

### W3.1 — Overpass client and Resilience4j policy · PR 7 · ~280 lines

**Goal**: a stubbed-testable Overpass integration with timeout, one retry, circuit breaker.
**Files**: `poi-service/src/main/java/com/rutatur/poi/client/{OverpassClient,OverpassQueryBuilder}.java`, `…/poi/config/{ResilienceConfig,PoiProperties}.java`.
**W3.1.T1 (RED)** — `poi-service/src/test/java/com/rutatur/poi/client/OverpassClientContractTest.java`: outbound `POST ${OVERPASS_URL}` with `data=` QL body; 5 s read / 2 s connect; one retry at 500 ms only on 429/504/5xx and never on 4xx; OSM JSON parsed to `JsonNode`; failure raises `UpstreamServiceException`.
**W3.1.I1 (GREEN)** — `RestClient` + `Retry`/`CircuitBreaker` decorators (50 % over 10 calls, open 30 s, 3 half-open).
**W3.1.R1 (VERIFY)** — `mvn -B -pl poi-service test -Dtest=OverpassClientContractTest`; confirm no `WebClient` on the module classpath (enforcer rerun).
**Done**: contract test green with `MockRestServiceServer`.
**Rollback**: delete `client/` + `config/`; module still builds empty.

### W3.2 — Normalization, category defaults, radius containment (RN-07) · PR 8 · ~300 lines

**Goal**: upstream OSM → shared `PoiDto` with unmappable elements dropped and containment applied after mapping.
**Files**: `poi-service/src/main/java/com/rutatur/poi/normalize/{PoiNormalizer,CategoryVisitTime}.java`, `…/poi/geo/RadiusFilter.java`.
**W3.2.T1 (RED)** — `poi-service/src/test/java/com/rutatur/poi/normalize/PoiNormalizerTest.java`: drops elements without `name`, without usable coordinates, or unmappable tag; collapses duplicates by `external_id`; `visitTime` per category default (MUSEUM 90 … SHOPPING 45) and always ≥ 1; `rating` defaults 0.0. `…/poi/geo/RadiusFilterTest.java`: 1200 m away with `radius=1000` is excluded, 900 m kept.
**W3.2.I1 (GREEN)** — implement mapping, dedupe, defaults, `GeoUtils` containment after mapping.
**W3.2.R1 (VERIFY)** — parameterized rerun; assert no upstream id leaks into `PoiDto.id` (must be `osm:<type>/<id>`).
**Done**: `mvn -B -pl poi-service test -Dtest=PoiNormalizerTest,RadiusFilterTest`.
**Rollback**: delete `normalize/` + `geo/`; client slice unaffected.

### W3.3 — Query cache and graceful degradation · PR 9 · ~250 lines

**Goal**: identical query served from cache; stale cache beats 503; never fabricate POIs.
**Files**: `poi-service/src/main/java/com/rutatur/poi/cache/{CacheKeyFactory,PoiCacheEntity,PoiCacheRepository,CachedPoiProvider}.java`, `…/poi/client/OverpassFallbackHandler.java`.
**W3.3.T1 (RED)** — `poi-service/src/test/java/com/rutatur/poi/cache/CacheKeyFactoryTest.java`: `sha256(lat|lng|radius|sorted(categories))` lowercase hex, stable across argument order, differs per radius. `…/poi/cache/PoiCacheFallbackTest.java`: repeat query issues no second Overpass call and returns the identical body; expired entry + unreachable Overpass → 200 from cache; no entry + unreachable → 503 `POI_PROVIDER_UNAVAILABLE`.
**W3.3.I1 (GREEN)** — 15 min TTL, 7-day retention, fallback ordering cache → stale → 503.
**W3.3.R1 (VERIFY)** — `mvn -B -pl poi-service test -Dtest=CacheKeyFactoryTest,PoiCacheFallbackTest`.
**Done**: both green; fallback test asserts no fabricated `PoiDto`.
**Rollback**: delete `cache/` + `OverpassFallbackHandler`; search degrades to direct calls only after W3.4.

### W3.4 — Public/internal endpoints and internal-token filter · PR 10 · ~270 lines

**Goal**: `/api/pois/search` and `/internal/pois/candidates` with identical shape, internal path header-guarded.
**Files**: `poi-service/src/main/java/com/rutatur/poi/web/{PoiController,InternalPoiController,RestExceptionHandler}.java`, `…/poi/security/{SecurityConfig,InternalTokenFilter}.java`, `poi-service/src/main/resources/{application.yml,application-docker.yml}`.
**W3.4.T1 (RED)** — `poi-service/src/test/java/com/rutatur/poi/web/PoiSearchControllerTest.java` (`@WebMvcTest`): 200 with `totalCount == pois.size()`, empty array → 200 (not 404), `radius=0`/`5001` → 400, `categories=CASTLE` → 400 with **no** upstream call, provider down → 503. `…/poi/security/InternalTokenFilterTest.java`: `/internal/**` without or with a wrong `X-Internal-Token` → 401 `INTERNAL_AUTH_REQUIRED`, constant-time compare, `/api/**` unaffected.
**W3.4.I1 (GREEN)** — controllers, advice mapping `ErrorCode`→HTTP, filter registered only for `/internal/*`.
**W3.4.R1 (VERIFY)** — `mvn -B -pl poi-service -am test`; `spring-boot:run` + `curl "localhost:8081/api/pois/search?lat=-34.6&lng=-58.4&radius=1000"`.
**Done**: module suite green; harness returns 200 or a `503 POI_PROVIDER_UNAVAILABLE` envelope with no POI fabrication.
**Rollback**: delete `web/` + `security/` + `application*.yml`; earlier POI slices remain test-proven.

---

## W4 — itinerary-service (PR 11–15, ~1,600 lines)

### W4.1 — poi-service and OSRM clients · PR 11 · ~320 lines

**Goal**: outbound candidate and routing-matrix calls with captured contracts and 503 on failure.
**Files**: `itinerary-service/src/main/java/com/rutatur/itinerary/client/{ItineraryPoiClient,OsrmTableClient}.java`, `…/itinerary/config/{ItineraryProperties,ResilienceConfig}.java`.
**W4.1.T1 (RED)** — `itinerary-service/src/test/java/com/rutatur/itinerary/client/CandidateRequestContractTest.java`: `GET /internal/pois/candidates` carries `lat`, `lng`, `radius`, `categories` and `X-Internal-Token`; 5 s timeout; 503 → `UpstreamServiceException`. `…/client/OsrmTableMappingTest.java`: `/table/v1/{profile}/{start;candidates}?annotations=duration,distance`, sources=0; seconds → `Math.round(s/60.0)` minutes, meters rounded; `null` cell → `∞`; transport failure → 503, never Euclidean.
**W4.1.I1 (GREEN)** — `RestClient` + Resilience4j per design §5.2; `startClock` default 09:00 (540), `maxCandidates` 50.
**W4.1.R1 (VERIFY)** — `mvn -B -pl itinerary-service test -Dtest=CandidateRequestContractTest,OsrmTableMappingTest`.
**Done**: both green against `MockRestServiceServer` stubs.
**Rollback**: delete `client/` + `config/`; module still builds.

### W4.2 — Greedy optimizer (RN-01, RN-02, RN-03, RN-05, RN-06, RN-08) · PR 12 · ~340 lines

**Goal**: deterministic, feasible, preference-first route with the exact time invariants.
**Files**: `itinerary-service/src/main/java/com/rutatur/itinerary/optimize/{ItineraryOptimizer,CandidateScorer,StopTimeline}.java`.
**W4.2.T1 (RED)** — `itinerary-service/src/test/java/com/rutatur/itinerary/optimize/CandidateScorerTest.java`: `0.7*preferenceScore + 0.3*ratingScore`, `preferenceScore=(P-i)/P`, unlisted category → 0.0; total order score desc → rating desc → visitTime asc → id asc. `…/optimize/ItineraryOptimizerTest.java`: `totalTime == totalVisitTime + totalTravelTime`, `endTime == startTime + totalTime`, `used <= availableTime`; orders contiguous 1..n and first leg from `startPoint`; repeated candidate collapses to one stop; top score not fitting → next included (skip-and-continue); `maxPois` cap default 6; nothing fits → 422; two runs over fixed input identical.
**W4.2.I1 (GREEN)** — implement dedupe → score → sort → truncate 50 → matrix → greedy loop → 422 on empty.
**W4.2.R1 (VERIFY)** — `mvn -B -pl itinerary-service test -Dtest=CandidateScorerTest,ItineraryOptimizerTest`; assert no `Random`/`HashMap` iteration in the optimizer.
**Done**: both classes green, including the 30-min/20-travel/30-visit 422 case.
**Rollback**: delete `optimize/`; client slice still compiles.

### W4.3 — Draft persistence, claim, list, get, delete · PR 13 · ~360 lines

**Goal**: persist-then-claim flow with `404`/`409` semantics and owner-scoped reads.
**Files**: `itinerary-service/src/main/java/com/rutatur/itinerary/persistence/{ItineraryDraftEntity,SavedItineraryEntity,ItineraryDraftRepository,SavedItineraryRepository,ItineraryPersistenceService}.java`, `…/itinerary/web/{ItineraryController,ItineraryMapper}.java`.
**W4.3.T1 (RED)** — `itinerary-service/src/test/java/com/rutatur/itinerary/service/ItineraryClaimTest.java`: unknown/expired draft (TTL 24 h) → 404 and no row; second claim → 409 `ITINERARY_ALREADY_SAVED`; successful claim writes `id == itinerary_id`; body `userId` ignored in favour of JWT subject. `…/service/ItineraryHistoryTest.java`: list returns only `user_id = sub` ordered `createdAt` desc, empty → 200 `[]`; foreign or unknown id on get/delete → 404; delete → 204 then 404 on retry; list succeeds with POI/OSRM clients stubbed to fail.
**W4.3.I1 (GREEN)** — JPA entities with `@JdbcTypeCode(SqlTypes.JSON)`, UUID assigned before persist, single `INSERT`-guarded claim, `@Table(schema="itinerary_schema")`.
**W4.3.R1 (VERIFY)** — `mvn -B -pl itinerary-service test -Dtest=ItineraryClaimTest,ItineraryHistoryTest`.
**Done**: both green with a mocked repository port; no external call on reads.
**Rollback**: delete `persistence/` + `web/`; optimizer slice unaffected.

### W4.4 — Internal endpoints and admin listing · PR 14 · ~280 lines

**Goal**: `GET /internal/itineraries/{id}`, `GET /internal/itineraries?owner=` (Q6), `GET /api/admin/itineraries` (Q5).
**Files**: `itinerary-service/src/main/java/com/rutatur/itinerary/web/{InternalItineraryController,AdminItineraryController}.java`, `…/itinerary/service/InternalItineraryQueryService.java`.
**W4.4.T1 (RED)** — `itinerary-service/src/test/java/com/rutatur/itinerary/web/InternalItineraryEndpointTest.java`: detail returns POI ids, start point, preferences, available time, totals; unknown id → 404; missing `X-Internal-Token` → 401; `owner=` listing returns only that owner's summaries. `…/web/AdminItineraryControllerTest.java`: `ROLE_USER`-only → 403, `ROLE_ADMIN` → 200, results capped at 100 ordered `createdAt` desc, missing `userId` → 400.
**W4.4.I1 (GREEN)** — map snapshots from `request_data`/`response_data`; `@PreAuthorize("hasRole('ADMIN')")`.
**W4.4.R1 (VERIFY)** — `mvn -B -pl itinerary-service test -Dtest=InternalItineraryEndpointTest,AdminItineraryControllerTest`.
**Done**: both green; internal responses contain no contact field.
**Rollback**: delete the two controllers + query service; history slice unaffected.

### W4.5 — Security, config, error mapping · PR 15 · ~300 lines

**Goal**: resource-server validation with issuer check, method security, internal-token filter, error envelope.
**Files**: `itinerary-service/src/main/java/com/rutatur/itinerary/security/{SecurityConfig,InternalTokenFilter}.java`, `…/itinerary/web/RestExceptionHandler.java`, `…/itinerary/web/GenerateItineraryController.java`, `itinerary-service/src/main/resources/{application.yml,application-docker.yml}`.
**W4.5.T1 (RED)** — `itinerary-service/src/test/java/com/rutatur/itinerary/security/ItinerarySecurityTest.java`: `JwtDecoder` from `${JWT_ISSUER_URI}/protocol/openid-connect/certs` with `createDefaultWithIssuer`; no token → 401, `ROLE_USER` missing → 403, subject is the only ownership source. `…/web/GenerateItineraryControllerTest.java`: `POST /api/itinerary/generate` anonymous 200 with all documented fields; `availableTime=481`/`radius=99`/`transportMode=CYCLING` → 400 with `violations`; upstream down → 503.
**W4.5.I1 (GREEN)** — security chain + `@EnableMethodSecurity`, advice, docker profile env wiring.
**W4.5.R1 (VERIFY)** — `mvn -B -pl itinerary-service -am test`; confirm no `X-Internal-Token` or issuer value echoed in error payloads.
**Done**: module suite green; harness `spring-boot:run` + `curl -X POST localhost:8082/api/itinerary/generate` returns 200 or 503 without an estimated itinerary.
**Rollback**: delete `security/` + `GenerateItineraryController` + yml; history and internal endpoints remain testable with `@WebMvcTest`.

---

## W5 — route-match-service (PR 16–19, ~1,300 lines)

### W5.1 — Opt-in sharing and snapshot (RN-09) · PR 16 · ~340 lines

**Goal**: `POST /api/routes/share` and `GET /api/routes/shareable`, contact-free, ownership-scoped.
**Files**: `route-match-service/src/main/java/com/rutatur/match/persistence/{SharedRouteEntity,SharedRouteRepository}.java`, `…/match/client/ItineraryInternalClient.java`, `…/match/service/{ShareService,ShareableQueryService}.java`, `…/match/web/RouteShareController.java`, `…/match/security/SecurityConfig.java`.
**W5.1.T1 (RED)** — `route-match-service/src/test/java/com/rutatur/match/service/ShareServiceTest.java`: foreign or unknown itinerary → 404 `SHARE_NOT_FOUND` with no row; success persists POI ids, start point, preferences, available time (201); repeat share returns the existing active share and keeps exactly one active row; expired/inactive reactivation issues a fresh `expiresAt`; itinerary service 503 → 503 with no active share. `…/match/web/RouteShareControllerTest.java`: no token → 401; share response and stored entity expose no email/phone field; shareable lists shared and unshared with distinguishable state.
**W5.1.I1 (GREEN)** — internal client with `X-Internal-Token`, upsert write last in one transaction, left-join share state.
**W5.1.R1 (VERIFY)** — `mvn -B -pl route-match-service test -Dtest=ShareServiceTest,RouteShareControllerTest`.
**Done**: both green; only `match_schema` written.
**Rollback**: delete `persistence/SharedRoute*`, `client/`, `service/Share*`, controller; module still builds.

### W5.2 — Similarity scorer and suggestions (RN-11) · PR 17 · ~320 lines

**Goal**: weighted score with exact threshold and deterministic ordering.
**Files**: `route-match-service/src/main/java/com/rutatur/match/similarity/{SimilarityScorer,SuggestionService}.java`, `…/match/web/MatchSuggestionController.java`.
**W5.2.T1 (RED)** — `route-match-service/src/test/java/com/rutatur/match/similarity/SimilarityScorerTest.java`: each component at 0 and 1 (jaccard 0.40, startProximity 0.25 saturating at 5 km, categoryOverlap 0.20, timeSimilarity 0.15 with 30 vs 480 → 0.0625); identical inputs → exactly 1.0; empty union → 0.0 not NaN; rounding `HALF_UP` 4 dp; 0.349 excluded and 0.350 admitted. `…/match/similarity/SuggestionServiceTest.java`: own and expired shares excluded, `>= 0.35` only, score desc then `targetRouteId` asc, max over the caller's shares, `matchedPoiIds` sorted, no contact field.
**W5.2.I1 (GREEN)** — implement scorer + assembly + DTO mapping.
**W5.2.R1 (VERIFY)** — `mvn -B -pl route-match-service test -Dtest=SimilarityScorerTest,SuggestionServiceTest`.
**Done**: both green including the 0.90/0.42/0.349 ordering case.
**Rollback**: delete `similarity/` + suggestion controller; sharing slice unaffected.

### W5.3 — Match request, inbox, mutual acceptance (RN-10) · PR 18 · ~340 lines

**Goal**: request lifecycle with idempotent open request and the exact transition matrix.
**Files**: `route-match-service/src/main/java/com/rutatur/match/persistence/{RouteMatchEntity,RouteMatchRepository}.java`, `…/match/service/{MatchRequestService,MatchLifecycleService,MatchQueryService}.java`, `…/match/web/MatchController.java`.
**W5.3.T1 (RED)** — `route-match-service/src/test/java/com/rutatur/match/service/MatchLifecycleTest.java`: `PENDING`+accept records one side and stays `PENDING`; second side → `ACCEPTED` with `acceptedAt`/`respondedAt`; `ACCEPTED`/`REJECTED` accept or reject → 409 `MATCH_TRANSITION_INVALID` with status unchanged; unknown → 404; non-participant → 403. `…/match/service/MatchRequestTest.java`: creates `PENDING` with responder derived from the target share; target not active or own → 404; repeated request → 200 and exactly one `PENDING` row; no active share → 422 `NO_ACTIVE_SHARE`. `…/match/service/MatchInboxTest.java`: returns sent ∪ received only, `requestedAt` desc.
**W5.3.I1 (GREEN)** — conditional `UPDATE … WHERE status='PENDING' AND <side> IS NULL`, partial unique index reliance, both-side consent rule.
**W5.3.R1 (VERIFY)** — `mvn -B -pl route-match-service test -Dtest=MatchLifecycleTest,MatchRequestTest,MatchInboxTest`.
**Done**: three classes green; concurrent same-side accept recorded once.
**Rollback**: delete lifecycle/inbox code; sharing and similarity slices unaffected.

### W5.4 — Contact reveal, Keycloak Admin, audit (RN-10, RN-12) · PR 19 · ~300 lines

**Goal**: minimal contact only for an `ACCEPTED` participant, revealed through Keycloak with `view-users`, always audited on success.
**Files**: `route-match-service/src/main/java/com/rutatur/match/client/KeycloakAdminClient.java`, `…/match/persistence/UserContactViewEntity.java`, `…/match/service/{ContactRevealService,ContactAuditService}.java`, `…/match/web/ContactController.java`.
**W5.4.T1 (RED)** — `route-match-service/src/test/java/com/rutatur/match/service/ContactRevealTest.java`: `PENDING`, `REJECTED`, and non-participant → 403 `CONTACT_NOT_REVEALED` with no contact field and **no** audit row; unknown match → 404; `ACCEPTED` participant → 200 `{userId, username, email}` plus exactly one `user_contact_view` row. `…/match/client/KeycloakAdminClientTest.java`: `client_credentials` with `rutatur-match`, `GET /admin/realms/rutatur-realm/users/{sub}` with `view-users`; 3 s timeout, one retry; failure → 503 `CONTACT_PROVIDER_UNAVAILABLE` and no audit row.
**W5.4.I1 (GREEN)** — reveal flow reads Keycloak first, writes audit last in one transaction; no contact data stored on `shared_route`.
**W5.4.R1 (VERIFY)** — `mvn -B -pl route-match-service -am test`; grep the module for stored email/phone fields to prove share records stay contact-free.
**Done**: full module suite green; audit row only on success.
**Rollback**: delete contact/reveal + audit code; match lifecycle unaffected.

---

## W6 — api-gateway (PR 20–21, ~600 lines)

### W6.1 — Routes, security chain, header stripping · PR 20 · ~360 lines

**Goal**: single ingress with the exact route table, 401/403 semantics, and `/internal/**` unreachable.
**Files**: `api-gateway/src/main/java/com/rutatur/gateway/security/GatewaySecurityConfig.java`, `api-gateway/src/main/resources/{application.yml,application-docker.yml}`, `api-gateway/src/test/java/com/rutatur/gateway/GatewayRouteTest.java`.
**W6.1.T1 (RED)** — `GatewayRouteTest` (`WebTestClient`): `/api/pois/**`→8081, `/api/itinerary/**` and `/api/itineraries/**`→8082, `/api/routes/**`→8083, `/api/admin/**`→8082; unknown business path → 404 with no forward; `X-Internal-Token` stripped from every inbound request; `/internal/pois/candidates` and `/internal/itineraries/{id}` → 404 before any route match even with a valid `ROLE_USER` token.
**W6.1.I1 (GREEN)** — route definitions with `RemoveRequestHeader=X-Internal-Token`, filter chain order per design §9.3 ending in `anyExchange().denyAll()`, shared `RealmRoleConverter`.
**W6.1.R1 (VERIFY)** — `mvn -B -pl api-gateway test -Dtest=GatewayRouteTest`; rerun `scripts/verify-enforcer.sh` to prove no servlet starter on the gateway.
**Done**: `mvn -B -pl api-gateway -am test` green; harness `curl -i localhost:8080/internal/pois/candidates` → 404.
**Rollback**: revert `application*.yml` + `GatewaySecurityConfig`; gateway serves nothing but actuator.

### W6.2 — CORS, docs aggregation, reactive error handler · PR 21 · ~240 lines

**Goal**: preflight without a token, aggregated Swagger over proxied prefixes, `ApiError` on gateway failures.
**Files**: `api-gateway/src/main/java/com/rutatur/gateway/config/{CorsConfig,OpenApiDocsConfig}.java`, `…/gateway/web/GatewayErrorWebExceptionHandler.java`, `api-gateway/src/test/java/com/rutatur/gateway/{CorsPreflightTest,GatewayErrorHandlerTest,DocsAggregationTest}.java`.
**W6.2.T1 (RED)** — `CorsPreflightTest`: allowed origin `OPTIONS` returns CORS headers and never 401, methods `GET,POST,DELETE,OPTIONS`, headers `Authorization,Content-Type`, max age 3600. `GatewayErrorHandlerTest`: reactive 401/403/404/503 render the `ApiError` envelope with no upstream URL, SQL, or token. `DocsAggregationTest`: `/v3/api-docs/{pois,itineraries,routes}` proxied, `/swagger-ui.html` lists exactly those three URLs.
**W6.2.I1 (GREEN)** — CORS from `RUTATUR_CORS_ORIGINS` (default `http://localhost:3000,http://localhost:8080`), `RewritePath` docs routes, error handler.
**W6.2.R1 (VERIFY)** — `mvn -B -pl api-gateway -am test`; harness `curl -i -X OPTIONS` preflight.
**Done**: module suite green, preflight unauthenticated.
**Rollback**: delete `config/` + error handler; routes/security from W6.1 stay intact.

---

## W7 — Integration (PR 22–23, ~700 lines)

### W7.1 — Compose topology, schema init, env template, network isolation (T1 RED) · PR 22 · ~330 lines

**Goal**: `docker compose up -d` brings up 7 healthy containers with loopback-bound service ports.
**Files**: `scripts/verify-network-isolation.sh`, `Dockerfile`, `docker-compose.yml`, `.env.example`, `db/init/00-roles.sql`, `db/init/01-poi-schema.sql`, `db/init/02-itinerary-schema.sql`, `db/init/03-match-schema.sql`.
**W7.1.T1 (RED)** — `scripts/verify-network-isolation.sh`: attaches a throwaway container to a **foreign** docker network and asserts `http://poi-service:8081` (likewise `:8082`, `:8083`) **fails** while `http://api-gateway:8080` stays reachable; separately asserts host `curl localhost:8081/actuator/health` succeeds and host `curl <host-ip>:8081` fails. Fails today because no compose file exists and published ports would be `0.0.0.0`.
**W7.1.I1 (GREEN)** — root `Dockerfile` with `ARG MODULE` + `maven:3.9-eclipse-temurin-21` build stage; compose with `rutatur-network`, `postgres` `127.0.0.1:5432:5432`, `keycloak 8180:8080`, services `127.0.0.1:808{1,2,3}:808{1,2,3}`, gateway `8080:8080`, service-specific datasource roles/schemas, `KC_HOSTNAME=http://keycloak:8080`, healthchecks (`pg_isready`, Keycloak `/health/ready` on 9000, wget `/actuator/health`), `depends_on: service_healthy`, `postgres-data` volume, `pgadmin` behind `tools` profile; `db/init/*.sql` exactly per design §8.1 with `IF NOT EXISTS` guards and per-service grants; `.env.example` listing `POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_MATCH_CLIENT_SECRET`, `RUTATUR_INTERNAL_TOKEN`, `RUTATUR_OVERPASS_URL`, `RUTATUR_OSRM_BASE_URL`, `RUTATUR_CORS_ORIGINS`.
**W7.1.R1 (VERIFY)** — `docker compose up -d --wait` → all healthy; `docker compose up -d` a second time → no duplicate init errors and saved data readable; rerun the isolation script (must pass).
**Done**: `bash scripts/verify-network-isolation.sh` exits 0; `docker compose ps` shows 7 healthy.
**Rollback**: `docker compose down -v` then delete `Dockerfile`, `docker-compose.yml`, `.env.example`, `db/init/`, `scripts/verify-network-isolation.sh`.

### W7.2 — Realm export, smoke script, ADR index, README · PR 23 · ~370 lines (~150 authored; realm export is generated)

**Goal**: tokens validate against the in-network issuer and the full flow runs green.
**Files**: `keycloak/rutatur-realm.json`, `scripts/smoke.sh`, `docs/decisions/README.md`, `README.md` (run section).
**W7.2.T1 (RED)** — `scripts/smoke.sh` asserts, before any realm exists: `user1` token carries `ROLE_USER`, `admin1` carries `ROLE_USER`+`ROLE_ADMIN`, gateway accepts both and rejects an unknown-key token with 401; then login → generate → save → share (two users) → suggestion `>= 0.35` → request → responder accept (stays `PENDING`) → contact 403 → requester accept → contact 200 with one audit row. Fails while `keycloak/rutatur-realm.json` is absent.
**W7.2.I1 (GREEN)** — export realm `rutatur-realm` with clients `rutatur-api` (public, direct grants), `rutatur-gateway` (confidential, service account disabled), `rutatur-match` (confidential, service account enabled, realm-management `view-users`), roles `ROLE_USER`/`ROLE_ADMIN`, users `user1`/`admin1` (`password`), 15 min access token, `iss=http://keycloak:8080/realms/rutatur-realm`.
**W7.2.R1 (VERIFY)** — `bash scripts/smoke.sh` green; `mvn -B verify` green from a clean checkout; then the docs pass:
- `docs/decisions/README.md`: change the ADR-002 row from `[ADR-002](./ADR-002-api-gateway.md) | Uso de API Gateway` to resolve to `ADR-002-tres-servicios-funcionales.md` and note it supersedes ADR-001 (one line) — resolves the design §17 collision.
- `README.md`: document `docker compose up -d`, `bash scripts/smoke.sh`, `.env` copying, `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`, and `mvn -B verify` with **no** manual `mvn install` step.
**Done**: `bash scripts/smoke.sh` exits 0; ADR link resolves to an existing file.
**Rollback**: `docker compose down -v`; revert realm export, smoke script, and the two doc files as one unit.

---

## Mandatory Item Traceability

| Mandatory item | Tasks |
|---|---|
| ADR-002 index correction | W7.2.R1 |
| T1 RED test: foreign-network `poi-service:8081` fails, gateway reachable, host loopback works | W7.1.T1 |
| JDK 21 `requireJavaVersion` + banned servlet starter | W1.2.T1 / W1.2.I1 / `scripts/verify-enforcer.sh` |
| Spring Cloud 2023.0.x verification step | W1.1.R1 |
| `X-Internal-Token` on `/internal/**` | W3.4.T1 (POI filter), W4.5.T1 (itinerary filter), W4.4.T1 (401 cases) |
| Gateway must not route `/internal/**` | W6.1.T1 |
| Keycloak contact reveal with `view-users`, audited | W5.4.T1 / W5.4.I1 |
| `.env.example`, realm export, schema init SQL, healthchecks, smoke script | W7.1.I1, W7.2.I1, W7.2.T1 |

## Definition of Done

- [ ] `mvn -B verify` green across all five modules on Temurin 21 (platform-deployment: clean reactor build).
- [ ] Adding `spring-boot-starter-web` to `api-gateway/pom.xml` fails the build; no manual `mvn install` appears in build instructions.
- [ ] `docker compose up -d` → 7 required containers healthy, `route-match-service` present; second run clean and data preserved.
- [ ] `bash scripts/verify-network-isolation.sh` passes (T1): foreign-network `:8081` fails, gateway reachable, host loopback works.
- [ ] `bash scripts/smoke.sh` passes: login → generate → save → share → suggestion ≥ 0.35 → request → mutual accept → contact; contact 403 before acceptance.
- [ ] ADR-002 index row resolves to `ADR-002-tres-servicios-funcionales.md`.
- [ ] No image embeds a secret; every credential comes from `.env`.
- [ ] No PR slice exceeds 400 authored changed lines (`git diff --stat` per PR).

### Business rule coverage (RN-01…RN-12)

| Rule | Executable scenario | Slice |
|---|---|---|
| RN-01 time feasibility | `ItineraryOptimizerTest` invariant `<= availableTime` | W4.2 |
| RN-02 sequential order from start | `ItineraryOptimizerTest` orders 1..n, first leg from `startPoint` | W4.2 |
| RN-03 no repeated POI | `ItineraryOptimizerTest` duplicate candidate → one stop | W4.2 |
| RN-04 routing-engine times | `OsrmTableMappingTest`, `ItineraryOptimizerTest` matrix values | W4.1, W4.2 |
| RN-05 per-POI visit time | `ItineraryOptimizerTest` `totalVisitTime`; `PoiNormalizerTest` `visitTime` ≥ 1 | W3.2, W4.2 |
| RN-06 prioritization | `CandidateScorerTest` 0.7/0.3 + deterministic total order | W4.2 |
| RN-07 search radius | `RadiusFilterTest`, `PoiSearchControllerTest`, `CandidateRequestContractTest` | W3.2, W3.4, W4.1 |
| RN-08 feasibility of first leg | `ItineraryOptimizerTest` nothing fits → 422 | W4.2 |
| RN-09 opt-in sharing | `ShareServiceTest` unshared itinerary never suggested; `SuggestionServiceTest` | W5.1, W5.2 |
| RN-10 mutual consent | `MatchLifecycleTest` one-sided stays `PENDING`; `ContactRevealTest` 403 before `ACCEPTED` | W5.3, W5.4 |
| RN-11 justified similarity | `SimilarityScorerTest` weights, 1.0 identical, threshold boundary | W5.2 |
| RN-12 reveal authorization | `ContactRevealTest` participant + `ACCEPTED` only, audit on success | W5.4 |

## Review Workload Forecast

Estimated changed lines: 6800
Chained PRs recommended: Yes
400-line budget risk: High
Decision needed before apply: No
Suggested chain strategy note: 23 slices are independent and can chain either stacked-to-main or via a tracker branch; the orchestrator asks the user which one before apply.
