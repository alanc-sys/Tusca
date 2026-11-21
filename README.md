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
- **Seguridad centralizada:** Keycloak 25 importa automáticamente el realm `rutatur` con los roles `ROLE_USER` y `ROLE_ADMIN`, los clientes `rutatur-client` y `rutatur-api` (este último para el flujo `password` del modo demo), los usuarios iniciales de prueba y los usuarios demo `user1`, `user2` y `admin1`.

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

### Flujo E2E de calificaciones y administración

La carpeta [`bruno/flujo-calificaciones-admin/`](bruno/flujo-calificaciones-admin/) agrega 10 peticiones secuenciales que autentican con el header `X-Demo-User` (sin bearer token). Requiere la plataforma levantada con el gateway en perfil `demo` —valor por defecto en `api-gateway/compose.yml`— y el entorno `local`:

```bash
cd bruno
npx @usebruno/cli run flujo-calificaciones-admin -r --env local
```

Los bloques `script:pre-request` ejecutan las llamadas auxiliares (generar + guardar itinerarios, consultar sugerencias y aceptar el match) y los `script:post-response` encadenan `itineraryId`, `itineraryId2`, `sharedRoute1Id`/`sharedRoute2Id` (alias `sharedRouteId`), `matchId` y `user2Id`. Cada `.bru` incluye asserts `tests { ... }` de status y campos clave.

| Paso | Petición principal | Resultado esperado |
| --- | --- | --- |
| `01-user1-generar-guardar-compartir` | `POST /api/routes/share` como `user1` | `201`, ruta activa |
| `02-user2-generar-guardar-compartir` | `POST /api/routes/share` como `user2` | `201`, captura `user2Id` |
| `03-user2-sugerencias-y-solicitar-match` | `POST /api/routes/matches/request` como `user2` | `201`, estado `PENDING` |
| `04-user1-aceptar-match-y-ver-contacto` | `GET /api/routes/matches/{matchId}/contact` como `user1` | `200`, contacto de `user2` |
| `05-user1-calificar-match-ok` | `POST /api/routes/matches/{matchId}/rate` (`score: 2`) | `201` |
| `06-user1-calificar-duplicado-error` | ídem | `409`, `RATING_ALREADY_EXISTS` |
| `07-admin-informe-calificaciones` | `GET /api/admin/ratings/report` como `admin1` | `200`, `user2` en el informe |
| `08-admin-bloquear-usuario` | `POST /api/admin/users/{user2Id}/block` como `admin1` | `200`, `BLOCKED` |
| `09-user2-accion-bloqueada-error` | `POST /api/routes/share` como `user2` | `403`, `USER_BLOCKED` |
| `10-admin-desbloquear-usuario` | `POST /api/admin/users/{user2Id}/unblock` como `admin1` | `200`, `ACTIVE` |

Usuarios de prueba precargados en Keycloak (realm `rutatur`):

| Usuario | Rol | Uso en pruebas |
| --- | --- | --- |
| `turista1` | `ROLE_USER` | Generación/guardado de itinerarios y solicitud de match |
| `turista2` | `ROLE_USER` | Contraparte para sugerencias, aceptación/rechazo y revelación de contacto |
| `admin` | `ROLE_ADMIN` | Endpoints administrativos (`/api/admin/routes`, `/api/admin/metrics`, `/api/pois/sync`) |
| `user1`, `user2` | `ROLE_USER` | Modo demo: calificaciones y bloqueo (contraseña `password`) |
| `admin1` | `ROLE_ADMIN` | Modo demo: informe y bloqueo (contraseña `password`) |

---

## Calificaciones post-match y administración

Una vez que un match está `ACCEPTED`, cualquiera de los dos participantes puede calificar a su contraparte una sola vez:

```bash
POST /api/routes/matches/{matchId}/rate
Authorization: Bearer <jwt>
Content-Type: application/json

{ "score": 4, "comment": "Muy buena ruta" }
```

- `score` entero entre `1` y `5`, `comment` opcional de hasta 500 caracteres.
- Devuelve `201 Created` con el detalle de la calificación. El perfil del calificado se actualiza de forma atómica con su nuevo `ratings_count` y `average_rating` (2 decimales, `HALF_UP`).
- Errores: `409 MATCH_NOT_ACCEPTED`, `403 NOT_MATCH_PARTICIPANT`, `409 RATING_ALREADY_EXISTS`.

El perfil local de cada usuario (`user_profile`) se crea/actualiza automáticamente al primer request autenticado contra `route-match-service` usando los claims del JWT (sincronización JIT).

Consola administrativa (`ROLE_ADMIN`):

| Endpoint | Descripción |
| --- | --- |
| `GET /api/admin/ratings/report` | Promedio global, total de calificaciones, usuarios bloqueados y ranking por reputación. Marca `recommendedForBlock` cuando `ratingsCount >= 2` y `averageRating < 2.5`. |
| `POST /api/admin/users/{userId}/block` | Bloquea al usuario (`403 USER_BLOCKED` al compartir rutas o gestionar matches). |
| `POST /api/admin/users/{userId}/unblock` | Restaura el estado `ACTIVE`. |

`/api/admin/routes` y `/api/admin/metrics` siguen siendo servidos por el gateway; `/api/admin/ratings/**` y `/api/admin/users/**` se enrutan a `route-match-service`.

---

## Modo demo multi-usuario (`X-Demo-User`)

El gateway se levanta con el perfil `demo` por defecto (configurable con `SPRING_PROFILES_ACTIVE`). En ese modo podés cambiar de usuario sin pegar el token a mano: omití `Authorization` y enviá el header `X-Demo-User`:

```bash
curl -i http://localhost:8080/api/routes \
  -H "X-Demo-User: user2"
```

Usuarios disponibles: `user1`, `user2` y `admin1` (todos con contraseña `password` en Keycloak). El filtro `DemoUserAuthWebFilter`:

1. Pide un JWT real a Keycloak con `grant_type=password`, `client_id=rutatur-api`, `username=<X-Demo-User>` y `password=password`.
2. Lo guarda en memoria caché por usuario hasta **30 segundos antes de su expiración**.
3. Inyecta `Authorization: Bearer <jwt>` en el request antes de la cadena de seguridad.

Si ya envías `Authorization`, el filtro no interviene. El header `X-Demo-User` también aparece como parámetro opcional en Swagger UI (`/swagger-ui.html`) para todas las operaciones.

Ejemplo de flujo completo para probar calificaciones y bloqueo:

```bash
# 1. Compartir ruta y aceptar un match como user1 / user2 (con X-Demo-User)
# 2. Calificar la contraparte
curl -i -X POST http://localhost:8080/api/routes/matches/{matchId}/rate \
  -H "X-Demo-User: user1" -H "Content-Type: application/json" \
  -d '{"score": 2, "comment": "Poco coincide"}'

# 3. Ver el informe y bloquear al usuario mal calificado como admin1
curl -i http://localhost:8080/api/admin/ratings/report -H "X-Demo-User: admin1"
curl -i -X POST http://localhost:8080/api/admin/users/{userId}/block -H "X-Demo-User: admin1"
```

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
- [x] `bruno/flujo-calificaciones-admin/` (flujo E2E de calificaciones, informe, bloqueo y desbloqueo con `X-Demo-User`)
- [x] `poi-service` (Overpass API, caché geográfico por área, fallback resiliente, `/internal/pois/search`)
- [x] `itinerary-service` (colaboración con `poi-service`, OSRM + fallback Haversine, CRUD protegido por JWT)
- [x] `route-match-service` (publicación opt-in, similitud Jaccard + Haversine, patrón State, consentimiento bilateral)
- [x] Calificaciones post-match, reputación, informe administrativo y bloqueo (`user_profile`, `match_rating`, RN-13 a RN-18)
- [x] Modo demo multi-usuario en `api-gateway` (`X-Demo-User` + caché de JWT) y documentación Swagger/README
