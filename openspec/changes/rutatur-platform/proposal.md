# Proposal: RutaTur Platform Implementation

## Intent

The repository documents a complete tourist-itinerary platform (4 C4 containers, 3 functional services, Keycloak security, PostgreSQL, Docker Compose) but contains **zero executable code**: no `pom.xml`, no `src/`, no `docker-compose.yml`, no realm export. Documentation cannot be demonstrated, graded, or verified. This change turns the documented design into a buildable, testable, deployable platform.

**Outcome**: `docker compose up -d` yields a healthy stack; a user can generate an itinerary with real travel times, save it, opt it into route matching, and reveal contact details only after mutual acceptance.

## Scope

### In Scope

| Module / asset | Introduced as | Responsibility |
|---|---|---|
| `pom.xml` (root) | New Maven aggregator/parent | Versions, `java.version=21`, module list |
| `rutatur-common` | New Maven module (plain JAR) | DTOs, enums, `GeoUtils`, exceptions, validators |
| `poi-service` | New module + Docker service `:8081` | Overpass integration, normalization, cache, `/api/pois/search`, `/internal/pois/candidates` |
| `itinerary-service` | New module + Docker service `:8082` | poi-service client, OSRM client, greedy optimizer, persistence, itinerary CRUD + generation, `/internal/itineraries/{id}` |
| `route-match-service` | New module + Docker service `:8083` | Opt-in shared routes, weighted similarity, match lifecycle, mutual-consent reveal, audit |
| `api-gateway` | New module + Docker service `:8080` | Spring Cloud Gateway routes, JWT Security Filter, CORS, OpenAPI aggregation |
| `docker-compose.yml`, `keycloak/rutatur-realm.json`, `db/init/*.sql` | New infrastructure assets | Postgres + Keycloak + services, healthchecks, schemas |

Business rules RN-01…RN-12 (README "Reglas de Negocio Clave", `docs/01 §1.5`) are **in scope** and MUST become spec scenarios: time feasibility, sequential order from start point, no repeated POI, OSRM-derived travel times, preference prioritization, inter-service collaboration, opt-in sharing, mutual consent, Jaccard-based similarity, no unilateral reveal (403).

### Out of Scope

- Native mobile app, chat/messaging, payments, reviews, turn-by-turn navigation, real-time transit (docs §1.7).
- Production hardening: TLS, Vault/secrets, Redis cache, Prometheus/Grafana, ELK, self-hosted OSRM.
- Google Places / OpenRouteService adapters (design allows them; not built).
- Testcontainers DB suites while the Docker daemon is down — gated, deferred to W7.
- Fixing the prose in `docs/` (contradictions are resolved in code + a new ADR, not by rewriting docs).

## Capabilities

### New Capabilities

- `poi-search`: public POI search + internal candidate endpoint, Overpass-backed, cached.
- `itinerary-generation`: feasibility-constrained greedy generation with real travel times.
- `itinerary-history`: authenticated save/list/get/delete of owned itineraries.
- `route-sharing`: opt-in, active-only publication of itineraries for matching.
- `route-matching`: weighted similarity suggestions, request lifecycle, mutual consent, contact reveal + audit.
- `gateway-security`: single ingress, JWT validation, role enforcement, internal-path isolation.
- `platform-deployment`: Compose stack, Keycloak realm/roles/clients, schema init, healthchecks.
- `shared-domain`: reusable DTOs, enums, `GeoUtils`, exceptions.

### Modified Capabilities

None (greenfield).

## Delivery Waves

Each wave is **not** one PR. Each wave splits into chained PR slices of ≤400 changed lines (additions + deletions), tests included in the slice that they verify.

| Wave | Modules | Contents | Est. lines | Done proof |
|---|---|---|---|---|
| **W1 Bootstrap** | root `pom.xml`, 5 module poms, `.gitignore`, `.mvn/` | JDK 21 pinning, dependency/plugin management, one context/unit test per module | ~600 → **2 PRs** | `mvn -B verify` green on empty modules |
| **W2 Common** | `rutatur-common` | DTOs, enums, `GeoUtils`, exceptions, validators + unit tests | ~900 → **3 PRs** | Common unit tests green; `GeoUtils` Haversine vs known pairs |
| **W3 POI** | `poi-service` | Overpass client + Resilience4j, normalization, cache, public/internal endpoints | ~1100 → **4 PRs** | MockMvc tests for search/candidates; Overpass client test with stubbed JSON |
| **W4 Itinerary** | `itinerary-service` | poi-service + OSRM clients, greedy optimizer, JPA persistence, CRUD + generate, internal endpoint | ~1600 → **5 PRs** | Optimizer unit tests (feasibility, no repeat); generation integration test with stubbed clients |
| **W5 Route match** | `route-match-service` | Shared routes, similarity scorer, match lifecycle, consent gate, audit | ~1300 → **4 PRs** | Similarity unit tests per weight; 403 without mutual acceptance |
| **W6 Gateway** | `api-gateway` | Routes, JWT Security Filter, CORS, OpenAPI aggregation | ~600 → **2 PRs** | Route + 401/403 filter tests; `/internal/**` not routable |
| **W7 Integration** | `docker-compose.yml`, `keycloak/`, `db/init/`, Dockerfiles | Realm export, roles/clients, schemas, healthchecks, smoke verification | ~700 → **2 PRs** | `docker compose up -d` → all healthy; end-to-end smoke (login → generate → share → match → reveal) |

Ordering is forced by compilation dependencies: common → POI → itinerary → route-match → gateway → Compose.

## Cut Decision

**One OpenSpec change (`rutatur-platform`) with wave-based tasks and chained PRs — not seven independent changes.** Rationale: `rutatur-common` is a shared contract. Splitting into separate changes would let one service's DTO, enum, or exception shape drift while another service is specified against a stale version, and cross-service payloads (candidate lists, itinerary detail for matching, JWT claims) would be negotiated twice. A single change keeps one capability set, one spec set, and one design contract for all four modules; review focus is protected by PR slicing, not by change fragmentation.

## Documentation Contradictions — Resolution Direction

| # | Contradiction | Resolution (detail deferred to design) |
|---|---|---|
| 1 | Keycloak `8080` in README table vs `8180` in URLs; `JWT_ISSUER_URI` uses `keycloak:8080` | Container port **8080**, host port **8180**; issuer stays `http://keycloak:8080/realms/rutatur-realm` for in-network validation. Gateway owns host 8080. |
| 2 | `route-match-service` absent from deployment guide and §2.12 compose list | Add as a Compose service on `:8083` with its own `match_db` init and gateway route. |
| 3 | ADR-001 says "2 functional services"; design has 3 | Code implements 3; write **ADR-002 superseding ADR-001**, not an edit of ADR-001. |
| 4 | Gateway documented with Swagger aggregation but no gateway API contract | Gateway adds no business endpoints: its contract is the documented `/api/**` surface plus CORS/security behaviour. Owned paths: `/api/pois/**`, `/api/itinerary/**`, `/api/itineraries/**`, `/api/routes/**`, `/api/admin/**`. Contract fixed in design before W6. |
| 5 | `docs/02 §2.10` and README require manual `mvn install` of `rutatur-common` | Reactor build (`mvn -B -pl <module> -am test`) supersedes it; the manual step is obsolete and MUST NOT appear in build instructions. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Wide change (≈6.8k lines) drifts from specs | High | Specs before code; each PR cites the scenario it satisfies |
| External Overpass/OSRM instability breaks tests/demo | High | Adapter interfaces; stubbed unit tests; Resilience4j fallback; smoke test tolerant of upstream failure |
| Docker daemon down / JDK 25–26 on PATH | Med | Pin `JAVA_HOME` to Temurin 21; Compose work deferred to W7 with explicit precondition |
| Servlet/reactive starter mixing in gateway | Med | Per-module dependency audit task in W1 and W6 |
| Contract mismatch between services | Med | Contracts in design; `rutatur-common` DTOs are the single source |

## Assumptions

- Java 21 LTS, Spring Boot 3.3.x, Spring Cloud 2023.x, Maven 3.9.
- One PostgreSQL instance, schema per service; no cross-schema writes.
- Reference data (POIs) always originates from Overpass; no seeded POI catalogue.
- Keycloak realm holds `rutatur-api`, `rutatur-gateway`, `ROLE_USER`, `ROLE_ADMIN`, `user1`/`admin1`.
- `availableTime` 30–480 min, `radius` 100–3000 m per API docs.

## Rollback Plan

Greenfield, so rollback is **revert of the wave's PR(s)**; earlier waves are unaffected because each slice compiles and tests independently.

- W2–W6: revert removes that module's code only; `rutatur-common` API changes require reverting dependents in reverse order (documented in the PR chain).
- W7: `docker compose down -v` removes volumes; compose/Keycloak/Dockerfile changes revert as one unit with no data migration to undo.
- Security rollback (gateway filter, realm roles): restoring the previous commit restores prior JWT behaviour; no persisted credentials beyond local dev users.
- No production data exists at any point, so no data rollback is required.

## Success Criteria

- [ ] `mvn -B verify` green across all modules.
- [ ] `docker compose up -d` reports all containers healthy.
- [ ] End-to-end smoke: login → generate → save → share → suggestion ≥0.35 → request → mutual accept → contact revealed; contact `403` before acceptance.
- [ ] Every RN-01…RN-12 has at least one executable scenario.
- [ ] No PR in the chain exceeds 400 changed lines.
