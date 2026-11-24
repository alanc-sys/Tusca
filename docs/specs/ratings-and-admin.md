# Especificación: Sistema de Calificaciones, Administración y Modo Demo Multi-Usuario

## 1. Objetivo
Incorporar persistencia de perfiles de usuario, sistema de calificaciones mutuas post-match, herramientas de auditoría y bloqueo para administradores en `route-match-service`, y un filtro de cambio rápido de usuario (`X-Demo-User`) en `api-gateway` respaldado por Keycloak para facilitar pruebas y demostraciones en vivo.

## 2. Reglas de Negocio
- **RN-13 (Sincronización JIT de perfiles):** Toda petición autenticada hacia `route-match-service` garantiza la existencia del usuario en `user_profile` usando los claims del JWT (`sub` como `user_id`, `preferred_username` como `username`, `email`). Estado inicial: `ACTIVE`.
- **RN-14 (Calificación post-match):** Solo los participantes de un `route_match` en estado `ACCEPTED` pueden calificarse mutuamente (`score` entero entre `1` y `5`, `comment` opcional de hasta 500 caracteres). Si el match no está `ACCEPTED` devuelve `409 Conflict` (`MATCH_NOT_ACCEPTED`); si el usuario no pertenece al match devuelve `403 Forbidden` (`NOT_MATCH_PARTICIPANT`).
- **RN-15 (Unicidad de calificación):** Un usuario puede calificar una única vez por cada match (`UNIQUE (match_id, rater_user_id)`). Un segundo intento devuelve `409 Conflict` (`RATING_ALREADY_EXISTS`).
- **RN-16 (Recálculo de reputación):** Al registrar una calificación, se recalculan atómicamente `average_rating` y `ratings_count` en el `user_profile` del usuario calificado.
- **RN-17 (Bloqueo administrativo):** Un usuario con `ROLE_ADMIN` puede bloquear (`BLOCKED`) o desbloquear (`ACTIVE`) cuentas en `user_profile`. Un usuario `BLOCKED` recibe `403 Forbidden` (`USER_BLOCKED`) al intentar compartir rutas (`POST /api/routes/share`), enviar solicitudes de match (`POST /api/routes/matches/request`) o aceptarlas (`POST /api/routes/matches/{matchId}/accept`).
- **RN-18 (Informe de calificaciones):** `GET /api/admin/ratings/report` devuelve métricas globales y el listado de usuarios ordenado ascendentemente por `average_rating` (priorizando quienes tienen `ratings_count > 0`), marcando `recommendedForBlock = true` cuando `ratings_count >= 2` y `average_rating < 2.5`.

## 3. Modelo de Datos (`match_db` en `infra/`)
Respetar la estructura actual de aislamiento de bases de datos configurada en `docker-compose.yml` e `infra/`, agregando las siguientes tablas en la base de datos de `route-match-service` (`match_db`):

- **`user_profile`**:
  - `user_id UUID PRIMARY KEY`
  - `username VARCHAR(100) NOT NULL`
  - `email VARCHAR(255)`
  - `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` (`ACTIVE` | `BLOCKED`)
  - `average_rating NUMERIC(3,2) NOT NULL DEFAULT 0.00`
  - `ratings_count INT NOT NULL DEFAULT 0`
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
  - `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- **`match_rating`**:
  - `id UUID PRIMARY KEY`
  - `match_id UUID NOT NULL REFERENCES route_match(id)`
  - `rater_user_id UUID NOT NULL REFERENCES user_profile(user_id)`
  - `rated_user_id UUID NOT NULL REFERENCES user_profile(user_id)`
  - `score INT NOT NULL CHECK (score BETWEEN 1 AND 5)`
  - `comment VARCHAR(500)`
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
  - `CONSTRAINT uk_match_rating_rater UNIQUE (match_id, rater_user_id)`

## 4. Contratos de API y DTOs (`rutatur-common` + `route-match-service`)
- **Nuevos `ErrorCode` en `rutatur-common`**:
  - `USER_BLOCKED` (403)
  - `NOT_MATCH_PARTICIPANT` (403)
  - `USER_PROFILE_NOT_FOUND` (404)
  - `MATCH_NOT_ACCEPTED` (409)
  - `RATING_ALREADY_EXISTS` (409)
- **Endpoints**:
  - `POST /api/routes/matches/{matchId}/rate` (`ROLE_USER`):
    - Body: `RateMatchRequest(Integer score, String comment)` (`@Min(1) @Max(5)`, `@Size(max = 500)`).
    - Response `201 Created`: `MatchRatingResponse(UUID id, UUID matchId, UUID raterUserId, UUID ratedUserId, int score, String comment, Instant createdAt)`.
  - `GET /api/admin/ratings/report` (`ROLE_ADMIN`):
    - Response `200 OK`: `AdminRatingsReportResponse(double platformAverageRating, int totalRatings, int blockedUsersCount, List<UserRatingSummary> users)`.
  - `POST /api/admin/users/{userId}/block` (`ROLE_ADMIN`):
    - Response `200 OK`: `UserStatusResponse(UUID userId, String username, String status)`.
  - `POST /api/admin/users/{userId}/unblock` (`ROLE_ADMIN`):
    - Response `200 OK`: `UserStatusResponse(UUID userId, String username, String status)`.

## 5. Modo Demo Multi-Usuario (`api-gateway` + `infra/`)
- **Keycloak (en `infra/`)**: Incorporar usuario `user2` con contraseña `password`, email `user2@rutatur.com` y rol `ROLE_USER` (además de `user1` y `admin1` existentes).
- **Filtro en `api-gateway` (`DemoUserAuthWebFilter`)**:
  - Activo únicamente con `@Profile("demo")` y registrado con alta precedencia (`@Order(-100)`) antes de Spring Security.
  - Si la petición no posee cabecera `Authorization` pero incluye `X-Demo-User` (ej. `user1`, `user2`, `admin1`), solicita un token JWT real a Keycloak (`grant_type=password`, `client_id=rutatur-api`, `password=password`), lo cachea en memoria hasta 30 segundos antes de expirar e inyecta `Authorization: Bearer <jwt>` en el request mutado.
- **Infraestructura y Swagger**:
  - Configurar `SPRING_PROFILES_ACTIVE=demo` en `api-gateway` dentro de `docker-compose.yml`.
  - Documentar el header global opcional `X-Demo-User` en OpenAPI/Swagger UI y actualizar el flujo de prueba en `README.md` (y en `bruno/` si corresponde).

## 6. Plan de Ejecución por Oleadas
- **W1 (Persistencia y Perfiles):** Scripts SQL en `infra/` para `match_db`, nuevos `ErrorCode` y DTOs en `rutatur-common`, entidades/repositorios JPA (`UserProfile`, `MatchRating`) y sincronización JIT de perfiles en `route-match-service`.
- **W2 (Calificaciones Post-Match):** Servicio y endpoint `POST /api/routes/matches/{matchId}/rate`, validaciones RN-14/RN-15, recálculo de promedios RN-16 y tests unitarios.
- **W3 (Administración y Bloqueos):** Endpoints `/api/admin/ratings/report`, `/block`, `/unblock`, validación `USER_BLOCKED` en operaciones de rutas/matches y tests unitarios.
- **W4 (Modo Demo, Docs y Verificación):** Usuario `user2` en Keycloak (`infra/`), `DemoUserAuthWebFilter` en `api-gateway`, perfil `demo` en `docker-compose.yml`, actualización de documentación y `mvn -B verify` completo en verde.