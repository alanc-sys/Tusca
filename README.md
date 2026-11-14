# RutaTur — Motor de Rutas Turísticas y Optimización de Puntos de Interés

Solución backend basada en microservicios (Java 21 + Spring Boot) para la generación de itinerarios turísticos urbanos optimizados y descubrimiento de viajeros con rutas compatibles bajo consentimiento mutuo.

---

## Estructura actual del repositorio

```text
.
├── pom.xml                         # Reactor Maven padre (Java 21, Spring Boot 3.3.5, Spring Cloud 2023.0.3)
├── docker-compose.yml              # Orquestador raíz (usa `include:`, sin definir contenedores)
├── .env.example                    # Plantilla de variables de entorno
├── docs/                           # Enunciado, rúbrica, propuesta y ADRs
│   └── decisions/
│       ├── ADR-001-database-per-service.md
│       ├── ADR-002-modular-compose-include.md
│       └── ADR-003-observability-prometheus-grafana.md
├── infra/
│   ├── postgres/                   # PostgreSQL 15 con bases y usuarios aislados por servicio
│   ├── keycloak/                   # Keycloak 25 persistido en Postgres + importación automática del realm
│   └── monitoring/                 # Prometheus + Grafana con auto-provisioning de datasource y dashboard
├── rutatur-common/                 # JAR compartido en .m2 (DTOs, enums, errores, seguridad, GeoUtils)
├── api-gateway/                    # Spring Cloud Gateway reactivo (puerto 8080, JWT, Swagger agregado)
├── poi-service/                    # Búsqueda/caché de POIs con Overpass API y Resilience4j (puerto 8081)
├── itinerary-service/              # Motor optimizador con OSRM y CRUD de itinerarios (puerto 8082)
├── route-match-service/            # Matching opt-in de rutas con patrón State y consentimiento (puerto 8083)
└── bruno/                          # Colección de pruebas API encadenadas (entorno `local`)
```

### Principios de infraestructura

- **Orquestación desacoplada:** El [docker-compose.yml](docker-compose.yml) raíz solo orquesta mediante `include:`. Cada componente de infraestructura y cada microservicio es dueño de su propio `Dockerfile` y `compose.yml`.
- **Database-per-Service lógico:** Una única instancia de PostgreSQL 15 inicializa bases y credenciales exclusivas (`keycloak_db`, `poi_db`, `itinerary_db`, `match_db`). Ver [ADR-001](docs/decisions/ADR-001-database-per-service.md).
- **Seguridad centralizada:** Keycloak 25 importa automáticamente el realm `rutatur` con los roles `ROLE_USER` y `ROLE_ADMIN`, el cliente `rutatur-client` y usuarios iniciales de prueba.

---

## Arranque rápido

1. Copiar las variables de entorno y ajustar los secretos si corresponde:

   ```bash
   cp .env.example .env
   ```

2. Construir y levantar toda la plataforma (infraestructura + los 4 microservicios):

   ```bash
   docker compose up -d --build --wait
   ```

3. *(Opcional)* Levantar **pgAdmin** con el perfil `tools`:

   ```bash
   docker compose --profile tools up -d
   ```

### Puertos expuestos (entorno local)

| Servicio | Puerto | Alcance |
| --- | --- | --- |
| API Gateway | `8080` | Host (`http://localhost:8080`, Swagger agregado en `/swagger-ui.html`) |
| POI Service | `8081` | Solo `127.0.0.1` (consumo público vía Gateway) |
| Itinerary Service | `8082` | Solo `127.0.0.1` (consumo público vía Gateway) |
| Route Match Service | `8083` | Solo `127.0.0.1` (consumo público vía Gateway) |
| Keycloak | `8180` | Host (`http://localhost:8180`) |
| Grafana | `3000` | Host (`http://localhost:3000`, dashboard `RutaTur — Microservices Overview`) |
| Prometheus | `9090` | Solo `127.0.0.1` |
| PostgreSQL 15 | `5432` | Solo `127.0.0.1` |
| pgAdmin *(perfil `tools`)* | `5050` | Solo `127.0.0.1` |

---

## Pruebas API con Bruno

La carpeta [`bruno/`](bruno/) contiene la colección completa de pruebas encadenadas (`01-auth` → `05-route-match`):

```bash
cd bruno
npx @usebruno/cli run -r --env local
```

Usuarios de prueba precargados en Keycloak (realm `rutatur`):

| Usuario | Rol | Uso en pruebas |
| --- | --- | --- |
| `turista1` | `ROLE_USER` | Generación/guardado de itinerarios y solicitud de match |
| `turista2` | `ROLE_USER` | Contraparte para sugerencias, aceptación/rechazo y revelación de contacto |
| `admin` | `ROLE_ADMIN` | Endpoints administrativos (`/api/admin/routes`, `/api/admin/metrics`, `/api/pois/sync`) |

---

## Arquitectura y decisiones clave

- **Seguridad y aislamiento:** el API Gateway valida el JWT emitido por Keycloak, sanitiza cabeceras internas y bloquea `/internal/**` (`403 Forbidden`) desde el exterior. Los microservicios validan `X-Internal-Token` en sus endpoints internos.
- **Resiliencia:** `poi-service` (Overpass API) e `itinerary-service` (OSRM) usan `Resilience4j` (`@CircuitBreaker` + `@Retry`) con fallback a caché local y cálculo geodésico Haversine respectivamente.
- **Patrón State en matching:** `route-match-service` modela el ciclo de vida `PENDING -> ACCEPTED | REJECTED` mediante estados explícitos (`MatchState`), garantizando transiciones irreversibles (`400 INVALID_STATE_TRANSITION`) y revelación de contacto solo con consentimiento bilateral (`403 CONSENT_REQUIRED` si está pendiente o rechazado).

---

## Hoja de ruta de módulos

- [x] Infraestructura base (`postgres` + `keycloak` + `monitoring` + orquestador raíz)
- [x] `rutatur-common` (librería Maven compartida: DTOs, errores, seguridad y utilidades)
  - Instalación en `.m2`: `mvn -pl rutatur-common install`
- [x] `api-gateway` (enrutamiento, filtro de seguridad JWT, aislamiento `/internal/**`, `/api/admin/*`)
- [x] `bruno/` (colección base: `01-auth` + `02-gateway-security` + `03-pois` + `04-itineraries` + `05-route-match` con assertions)
- [x] `poi-service` (Overpass API, caché geográfico por área, fallback resiliente, `/internal/pois/search`)
- [x] `itinerary-service` (colaboración con `poi-service`, OSRM + fallback Haversine, CRUD protegido por JWT)
- [x] `route-match-service` (publicación opt-in, similitud Jaccard + Haversine, patrón State, consentimiento bilateral)
