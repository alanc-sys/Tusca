# Guía de Despliegue - RutaTur

## Requisitos

- Docker 24+ con Docker Compose v2 (`docker compose`, no `docker-compose`)
- JDK 21 (Temurin recomendado) para compilar y correr los tests
- Maven 3.9+
- 6 GB de RAM recomendados
- 10 GB de espacio en disco

El stack completo son **7 contenedores**: `postgres`, `keycloak`, `poi-service`, `itinerary-service`,
`route-match-service`, `api-gateway` y, opcionalmente, `pgadmin` bajo el perfil `tools`.

## Arquitectura de Despliegue

| Componente | Puerto host | Acceso |
|---|---|---|
| `api-gateway` | `8080` | Todas las interfaces (único ingreso público) |
| `keycloak` | `8180` → `8080` | Todas las interfaces |
| `poi-service` | `8081` | Solo `127.0.0.1` (debug local) |
| `itinerary-service` | `8082` | Solo `127.0.0.1` (debug local) |
| `route-match-service` | `8083` | Solo `127.0.0.1` (debug local) |
| `postgres` | `5432` | Solo `127.0.0.1` (debug local) |
| `pgadmin` (perfil `tools`) | `5050` | Solo `127.0.0.1` |

Los servicios se comunican por la red bridge `rutatur-network`. Los puertos de los servicios
funcionales se publican en loopback únicamente para desarrollo; ningún cliente de la red puede
usarlos como ingreso. `scripts/verify-network-isolation.sh` verifica esa propiedad.

## Configuración Inicial

### 1. Clonar el repositorio

```bash
git clone <repository-url>
cd rutatur
```

### 2. Crear el archivo `.env`

```bash
cp .env.example .env
```

Editá `.env` y ajustá como mínimo:

```env
POSTGRES_PASSWORD=...
KEYCLOAK_ADMIN_PASSWORD=...
KEYCLOAK_MATCH_CLIENT_SECRET=...
RUTATUR_INTERNAL_TOKEN=...
```

`.env` está en `.gitignore` y no se versiona. Las variables opcionales
(`RUTATUR_OVERPASS_URL`, `RUTATUR_OSRM_BASE_URL`, `RUTATUR_CORS_ORIGINS`) tienen valores por
defecto en `docker-compose.yml`.

> `KEYCLOAK_MATCH_CLIENT_SECRET` debe coincidir con el secreto del cliente `rutatur-match` del
> realm importado (`keycloak/rutatur-realm.json`). Si lo cambiás en `.env`, actualizá también el
> realm o volvé a exportarlo.

### 3. Compilar y verificar (opcional, para desarrollo local)

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # macOS
# export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk        # Linux

mvn -B verify
```

`mvn -B verify` compila y testea todo el reactor, incluido `rutatur-common`. **No hace falta**
ejecutar `mvn install` a mano sobre el componente común: el reactor resuelve el orden.

## Levantar Servicios

### Despliegue completo

```bash
docker compose up -d --wait
```

`--wait` espera a que todos los healthchecks pasen. El arranque respeta las dependencias:

1. `postgres` (`pg_isready`)
2. `keycloak` (health en `:9000`, espera a PostgreSQL)
3. `poi-service` (espera a PostgreSQL)
4. `itinerary-service` (espera a PostgreSQL y `poi-service`)
5. `route-match-service` (espera a PostgreSQL, Keycloak e `itinerary-service`)
6. `api-gateway` (espera a Keycloak y a los tres servicios funcionales)

Los esquemas `poi_schema`, `itinerary_schema` y `match_schema` se crean una sola vez desde
`db/init/*.sql` cuando el volumen `postgres-data` está vacío. Los scripts son idempotentes y el
volumen conserva los datos entre reinicios: un segundo `docker compose up -d` no duplica nada.

### Verificar estado

```bash
docker compose ps
docker compose logs -f
docker compose logs -f api-gateway
```

### Health checks

```bash
curl http://localhost:8080/actuator/health   # api-gateway
curl http://localhost:8081/actuator/health   # poi-service
curl http://localhost:8082/actuator/health   # itinerary-service
curl http://localhost:8083/actuator/health   # route-match-service
```

## Keycloak

El realm se importa automáticamente al arrancar (`start-dev --import-realm`, montando
`./keycloak` en `/opt/keycloak/data/import`). No hace falta importarlo a mano.

- Realm: `rutatur-realm`
- Access token lifespan: 15 minutos
- Emisor (`iss`): `http://keycloak:8080/realms/rutatur-realm`, fijado con `KC_HOSTNAME` para que
  los tokens pedidos desde `localhost:8180` validen dentro de la red
- Clientes:
  - `rutatur-api`: público, direct access grants (curl/Swagger)
  - `rutatur-gateway`: confidencial, sin service account (solo valida)
  - `rutatur-match`: confidencial, service account con `realm-management:view-users`
- Roles: `ROLE_USER`, `ROLE_ADMIN`
- Usuarios: `user1` / `password` (USER), `admin1` / `password` (USER + ADMIN)

### Obtener un token

```bash
curl -s -X POST http://localhost:8180/realms/rutatur-realm/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=rutatur-api' \
  -d 'username=user1' \
  -d 'password=password'
```

## Acceso a los Servicios

| Servicio | URL | Descripción |
|---|---|---|
| **API Gateway** | http://localhost:8080 | Punto de entrada principal |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Documentación unificada |
| **Keycloak** | http://localhost:8180 | Identity Provider |
| **Keycloak Admin** | http://localhost:8180/admin | Administración de Keycloak |
| **pgAdmin** | http://localhost:5050 | Administración PostgreSQL (perfil `tools`) |
| **poi-service Swagger** | http://localhost:8081/swagger-ui.html | Swagger directo (solo loopback) |
| **itinerary-service Swagger** | http://localhost:8082/swagger-ui.html | Swagger directo (solo loopback) |
| **route-match-service Swagger** | http://localhost:8083/swagger-ui.html | Swagger directo (solo loopback) |

pgAdmin no arranca por defecto:

```bash
docker compose --profile tools up -d
```

## Credenciales por Defecto

### PostgreSQL

- Host: `localhost:5432`
- Database: `rutatur_db`
- Superusuario: `rutatur` / valor de `POSTGRES_PASSWORD`
- Roles por servicio (creados por `db/init/00-roles.sql`):
  - `rutatur_poi` → `poi_schema`
  - `rutatur_itinerary` → `itinerary_schema`
  - `rutatur_match` → `match_schema`
  - `rutatur_kc` → base `keycloak`

Cada rol tiene `USAGE` sobre un único esquema: un servicio no puede leer las tablas de otro.

### Keycloak Admin

- Usuario: valor de `KEYCLOAK_ADMIN` (por defecto `admin`)
- Password: valor de `KEYCLOAK_ADMIN_PASSWORD`

### pgAdmin

- Email: `admin@rutatur.com`
- Password: `admin`

### Usuarios de prueba (Keycloak)

| Usuario | Password | Roles |
|---|---|---|
| `user1` | `password` | `ROLE_USER` |
| `admin1` | `password` | `ROLE_USER`, `ROLE_ADMIN` |

## Verificación de Red

Con el stack levantado:

```bash
bash scripts/verify-network-isolation.sh
```

El script crea una red Docker ajena y comprueba que:

- `poi-service:8081`, `itinerary-service:8082` y `route-match-service:8083` **no** son alcanzables
  desde esa red;
- `api-gateway:8080` **sí** es alcanzable a través del puerto publicado en el host;
- desde el loopback del host, `localhost:8081/8082/8083/actuator/health` responde;
- desde la IP LAN del host, esos puertos **no** responden.

## Smoke Test End-to-End

```bash
bash scripts/smoke.sh
```

Cubre el flujo completo: login en Keycloak, roles del token, aceptación/rechazo de tokens en el
gateway, generación anónima, guardado, compartición opt-in, sugerencia con `matchScore >= 0.35`,
solicitud de coincidencia, consentimiento mutuo, `403` antes del consentimiento, `200` después y
una única fila de auditoría en `match_schema.user_contact_view`.

Requiere `curl`, `openssl`, `python3` y Docker Compose. La generación usa Overpass y OSRM reales,
así que necesita conectividad a internet.

## Comandos Útiles

```bash
# Detener servicios (conserva datos)
docker compose down

# Detener y borrar el volumen de PostgreSQL (reinicia esquemas y datos)
docker compose down -v

# Rebuild completo
docker compose down
docker compose build --no-cache
docker compose up -d --wait

# Logs en tiempo real
docker compose logs -f api-gateway poi-service itinerary-service route-match-service

# Consola de PostgreSQL
docker compose exec postgres psql -U rutatur -d rutatur_db

# Estado de healthchecks
docker compose ps
```

## Troubleshooting

### Keycloak no inicia

- Verificá que PostgreSQL esté `healthy`: `docker compose ps`.
- Revisá `docker compose logs keycloak`.
- El primer arranque puede tardar 1–2 minutos (crea el esquema de Keycloak).

### Los servicios no validan tokens

- Confirmá que Keycloak esté `healthy` y que el realm `rutatur-realm` exista.
- `JWT_ISSUER_URI` debe ser `http://keycloak:8080/realms/rutatur-realm` en los cuatro servicios JVM.
- Si cambiaste `KC_HOSTNAME`, los tokens emitidos tendrán otro `iss` y serán rechazados.

### Error al conectar con APIs externas

- Overpass/OSRM son públicos y pueden tener rate limits.
- Verificá conectividad: `curl https://overpass-api.de/api/interpreter`.
- Ajustá `RUTATUR_OVERPASS_URL` y `RUTATUR_OSRM_BASE_URL` en `.env`.

### Puerto ya en uso

```bash
lsof -i :8080
kill -9 <PID>
```

### Limpiar todo y empezar de cero

```bash
docker compose down -v
docker system prune -a
docker compose up -d --wait
```

## Consideraciones de Producción

Esta configuración es adecuada para entorno académico/desarrollo. Para un despliegue real:

- Usar HTTPS/TLS y `KC_HOSTNAME` con el dominio público.
- Gestionar secretos con Docker Secrets o Vault (no `.env` en disco).
- Rotar los secretos de clientes de Keycloak y los roles de PostgreSQL.
- Añadir rate limiting y límites de recursos.
- Usar Redis para caché y monitoreo con Prometheus/Grafana.
- Implementar logging centralizado (ELK) y backups de PostgreSQL.
- Usar instancias propias de OSRM y Overpass.
