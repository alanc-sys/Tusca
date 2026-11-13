# RutaTur — Motor de Rutas Turísticas y Optimización de Puntos de Interés

Solución backend basada en microservicios (Java 21 + Spring Boot) para la generación de itinerarios turísticos urbanos optimizados y descubrimiento de viajeros con rutas compatibles bajo consentimiento mutuo.

---

## Estructura actual del repositorio

```text
.
├── docker-compose.yml              # Orquestador raíz (usa `include:`, sin definir contenedores)
├── .env.example                    # Plantilla de variables de entorno
├── docs/                           # Enunciado, rúbrica, propuesta y ADRs
│   └── decisions/
│       └── ADR-001-database-per-service.md
└── infra/
    ├── postgres/                   # PostgreSQL 15 con bases y usuarios aislados por servicio
    └── keycloak/                   # Keycloak 25 persistido en Postgres + importación automática del realm
```

### Principios de infraestructura

- **Orquestación desacoplada:** El [docker-compose.yml](docker-compose.yml) raíz solo orquesta mediante `include:`. Cada componente de infraestructura y cada microservicio es dueño de su propio `Dockerfile` y `compose.yml`.
- **Database-per-Service lógico:** Una única instancia de PostgreSQL 15 inicializa bases y credenciales exclusivas (`keycloak_db`, `poi_db`, `itinerary_db`, `match_db`). Ver [ADR-001](docs/decisions/ADR-001-database-per-service.md).
- **Seguridad centralizada:** Keycloak 25 importa automáticamente el realm `rutatur` con los roles `ROLE_USER` y `ROLE_ADMIN`, el cliente `rutatur-client` y usuarios iniciales de prueba.

---

## Arranque rápido (Infraestructura base)

1. Copiar las variables de entorno y ajustar los secretos si corresponde:

   ```bash
   cp .env.example .env
   ```

2. Levantar PostgreSQL y Keycloak:

   ```bash
   docker compose up -d --wait
   ```

3. *(Opcional)* Levantar **pgAdmin** con el perfil `tools`:

   ```bash
   docker compose --profile tools up -d
   ```

### Puertos expuestos (entorno local)

| Servicio | Puerto | Alcance |
| --- | --- | --- |
| Keycloak | `8180` | Host (`http://localhost:8180`) |
| PostgreSQL 15 | `5432` | Solo `127.0.0.1` |
| pgAdmin *(perfil `tools`)* | `5050` | Solo `127.0.0.1` |

---

## Hoja de ruta de módulos

- [x] Infraestructura base (`postgres` + `keycloak` + orquestador raíz)
- [ ] `rutatur-common` (librería Maven compartida: DTOs, errores, seguridad y utilidades)
- [ ] `api-gateway`
- [ ] `poi-service`
- [ ] `itinerary-service`
- [ ] `route-match-service`
