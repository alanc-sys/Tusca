# RutaTur — Project Context (SDD Init)

**Detected**: 2026-11-03
**Project**: tusca (product name in documentation: RutaTur)
**Repository state**: greenfield. Documentation only.
**Persistence**: OpenSpec file store (`openspec/`), mirrored to Engram `sdd-init/tusca`.

Later SDD phases read this file for stack, layout, and environment facts. `openspec/config.yaml`
holds the machine-readable summary; this file holds the evidence behind it.

## 1. Repository Inventory

| Path | Content |
|---|---|
| `README.md` | Product overview, stack table, service/port table, business rules, run instructions |
| `enunciadoPreliminar.md` | Academic assignment statement (TPI Backend de Aplicaciones 2026) |
| `docs/01-definicion-problema.md` | Problem definition |
| `docs/02-diseno-solucion.md` | Solution design: C4, service responsibilities, algorithms, schemas, security |
| `docs/api/README.md` | REST contracts for poi-service and itinerary-service |
| `docs/architecture/` | C4 C1/C2 PlantUML + sequence diagram, architecture README |
| `docs/decisions/` | ADR-001 (microservices) + ADR index |
| `docs/deployment/README.md` | Docker Compose deployment guide, Keycloak realm import, credentials |

Absent today: `pom.xml`, any `src/` tree, `docker-compose.yml`, `Dockerfile`, `keycloak/` realm
JSON, `openspec/` (created by this phase), `.atl/`.

Git: branch `main`, single commit `5d26c0f` (docs only). Existing documentation is written in
Spanish; new SDD artifacts are English by convention.

## 2. Target Stack (as documented)

| Concern | Choice |
|---|---|
| Language | Java 17+ (design intent); normalized here to **Java 21 LTS** |
| Framework | Spring Boot 3.x (pin **3.3.x** until a change states otherwise) |
| Gateway | Spring Cloud Gateway (reactive) |
| Security | Spring Security resource server + Keycloak OIDC/JWT, `ROLE_USER`, `ROLE_ADMIN` |
| Persistence | PostgreSQL 15, schema per service |
| External APIs | Overpass API (POIs), OSRM (travel-time matrix) |
| Resilience | Resilience4j (circuit breaker, retry, timeout, bulkhead, fallback) |
| API docs | OpenAPI 3.0 / Swagger UI per service + gateway aggregation |
| Build | Maven (multi-module) |
| Runtime packaging | Docker + Docker Compose |
| Testing | JUnit 5 + Mockito + Spring Boot Test (+ Testcontainers planned) |

## 3. Detected Toolchain (this machine)

| Tool | Detected | Notes |
|---|---|---|
| Maven | **3.9.16** at `/usr/local/Cellar/maven/3.9.16/libexec` | Satisfies `Maven 3.8+` prerequisite |
| JDK on `PATH` | **OpenJDK 25.0.4** (Microsoft) | Above the supported range for older Spring Boot 3.x lines |
| Maven runtime JDK | **26.0.2.1** (Homebrew `openjdk@26`) | Maven picks this because no `JAVA_HOME` is set |
| JDK 21 LTS | **Temurin 21.0.12.1** at `/Users/dev-alan/Library/Java/JavaVirtualMachines/temurin-21.0.12.1/Contents/Home` | Present but **not default**; this is the target JDK |
| `JAVA_HOME` | unset | Must be exported (or pinned via `.mvn/jvm.config` / toolchains) |
| Docker CLI | **29.7.2** | Installed |
| Docker Compose | **v5.4.0** (plugin) | Installed |
| Docker daemon | **not running** | `docker info` fails on `/Users/dev-alan/.docker/run/docker.sock` |
| Gradle | absent | Not needed; Maven is canonical |
| Local Maven repo (`~/.m2/repository`) | 356 MB, Spring Boot 3.1.4 / 3.3.5 / 4.1.x, JUnit 5.9–6.0, Mockito 5.x, Testcontainers BOM | Enables fast offline builds for 3.3.5 |
| Network | Maven Central reachable (HTTP 200), Overpass reachable | Gateway/Spring Cloud artifacts need a first online resolve |

### Verified by probe (not assumed)

A throwaway Spring Boot 3.3.5 + `spring-boot-starter-test` project (JUnit 5 + Mockito, one
mocked unit test, `java.version=21`) was built and tested offline with the default Maven runtime
(JDK 26). Result: `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0`.

Conclusion: the Maven test pipeline works on this machine, but the JVM mismatch is a latent
hazard (Mockito/Byte Buddy dynamic-agent warnings were emitted; newer JDKs may reject dynamic
agent loading). Pin `JAVA_HOME` to Temurin 21 before implementing.

## 4. Target Build Layout (Maven multi-module)

```text
pom.xml                        # aggregator + parent (dependencyManagement, pluginManagement, java.version=21)
rutatur-common/pom.xml         # library JAR, no spring-boot-maven-plugin
api-gateway/pom.xml            # Spring Cloud Gateway, spring-boot-maven-plugin
poi-service/pom.xml            # Spring Web, Data JPA, Resilience4j, springdoc
itinerary-service/pom.xml      # Spring Web, Data JPA, Resilience4j, springdoc
route-match-service/pom.xml    # Spring Web, Data JPA, springdoc
docker-compose.yml             # postgres, keycloak, gateway, three services, pgadmin (optional)
```

Conventions for the aggregator:

- Root `pom.xml` is `<packaging>pom</packaging>` and owns all versions: `spring-boot-starter-parent`
  (or imported `spring-boot-dependencies` BOM), `spring-cloud-dependencies` BOM, Resilience4j, springdoc.
- Module POMs declare dependencies without versions.
- `rutatur-common` uses `spring-boot-maven-plugin` **not at all** (plain library JAR); executable
  modules enable `repackage`.
- Services depend on `com.rutatur:rutatur-common:${project.version}` (reactor resolves it; no manual
  `mvn install` step once the aggregator exists — the README `cd rutatur-common && mvn install`
  workflow becomes obsolete).
- Package root `com.rutatur`, one Spring Boot application class per service module.
- Shared code lives only in `rutatur-common` (DTOs, `GeoUtils`, exceptions, enums, validators).
- Each reactive module (gateway) must not pull servlet starters; each servlet module must not pull
  `spring-boot-starter-webflux`. Mixing both breaks context startup.
- Persistence per service owns its schema; no cross-schema reads.

## 5. Testing Capabilities

**Strict TDD Mode**: enabled

| Layer | Available | Tool |
|---|---|---|
| Unit | yes (after bootstrap) | JUnit 5 + Mockito + AssertJ |
| Integration | yes (after bootstrap) | Spring Boot Test (`@SpringBootTest`, `MockMvc`/`WebTestClient`) |
| Contract/persistence | planned | Testcontainers (PostgreSQL module) — needs a running Docker daemon |
| E2E | limited | Docker Compose smoke checks (`/actuator/health`) |

- Runner: `mvn -B verify` (root aggregator, all modules)
- Per module: `mvn -B -pl <module> -am test`
- Single test: `mvn -B -pl <module> test -Dtest=<TestClass>#<method>`
- Coverage: `mvn -B verify jacoco:report` (JaCoCo not configured yet; no threshold set)
- Quality gates: none detected (no Checkstyle, SpotBugs, PMD, Spotless, or formatter config)
- Type checking: provided by `javac` through the Maven compiler with `release=21`
- Bootstrap order for the first implementation change: root `pom.xml` → `rutatur-common` →
  service modules, with at least one real failing-then-passing test per module before features land.

Strict TDD is enabled because the documented stack and the installed Maven/JDK 21 toolchain support
RED-GREEN-REFACTOR. It is currently **not runnable** because no `pom.xml` exists; the first change
that adds the build must land the test infrastructure before any feature work.

## 6. Domain Facts Later Phases Must Not Break

- Generation must return a route starting at the user's start point, with no repeated POI and total
  visit + travel time within the available time.
- Travel times come from a routing engine (OSRM), never Euclidean distance.
- Route sharing is opt-in and active-only; suggestions use a weighted score
  (Jaccard 0.40, start proximity 0.25, category overlap 0.20, available-time similarity 0.15) with a
  `>= 0.35` threshold.
- Contact data is revealed only when a match is `ACCEPTED` by both participants; otherwise `403`.
- Internal endpoints (`/internal/**`) are not exposed through the gateway.
- One gateway is the only externally exposed service; services validate JWT as resource servers.

## 7. Known Documentation Inconsistencies (resolve before implementation)

1. **Keycloak port**: `README.md` service table says `8080 (auth)`; README URLs, `docs/02` and
   `docs/deployment` use `8180`; `JWT_ISSUER_URI` uses `http://keycloak:8080/...`. `8080` is also
   the gateway port, so exposing Keycloak on `8080` cannot work. Standardize on container `8080`
   + host `8180`.
2. **route-match-service scope**: `docs/02` C2 and the API list include it, but the deployment guide
   and the `docker-compose.yml` component list in `docs/02 §2.12` omit it.
3. **ADR-001 is stale**: it selects "2 functional services" while the design adds a third
   (`route-match-service`). A superseding ADR is needed.
4. **Gateway docs**: the gateway is listed with Swagger aggregation, but no gateway API contract
   exists in `docs/api/README.md`; only poi-service and itinerary-service are documented there.
5. **`docs/02 §2.10`** describes manual `mvn install` of `rutatur-common`, which conflicts with a
   reactor-based multi-module build.
