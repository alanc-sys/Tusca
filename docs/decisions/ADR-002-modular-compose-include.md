# ADR-002: Orquestación Docker Compose modular con `include:`

- **Estado:** Aceptada
- **Contexto:** La solución cuenta con múltiples componentes de infraestructura (`postgres`, `keycloak`, `monitoring`) y módulos Spring Boot (`api-gateway`, `poi-service`, `itinerary-service`, `route-match-service`). Concentrar todo en un único `docker-compose.yml` vuelve difícil mantener puertos, variables y builds de cada servicio.

## Decisión

1. El `docker-compose.yml` de la raíz actúa **exclusivamente como orquestador** mediante `include:`. No define `image`, `build`, `ports`, `environment` ni `volumes`.
2. Cada componente de infraestructura (`infra/*`) y cada microservicio es dueño de su propio `Dockerfile` y `compose.yml`.
3. Todos los módulos comparten la red bridge `rutatur-network` y leen sus variables de entorno desde el `.env` raíz (plantilla `.env.example`).

## Regla para próximos servicios

Todo nuevo microservicio que se agregue al repositorio **debe**:
- Incluir su propio `Dockerfile` (multi-stage build desde la raíz del reactor para resolver `rutatur-common`).
- Incluir su propio `compose.yml` conectado a `rutatur-network`.
- Registrarse en la lista `include:` del `docker-compose.yml` raíz.
