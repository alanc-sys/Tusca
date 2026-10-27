# Guía de Despliegue - RutaTur

## Requisitos

- Docker 20.10+
- Docker Compose 2.0+
- 4GB RAM mínimo (recomendado 6GB)
- 10GB de espacio en disco

## Arquitectura de Despliegue

El despliegue se realiza con **Docker Compose** levantando todos los componentes necesarios:

- PostgreSQL
- Keycloak
- API Gateway
- poi-service
- itinerary-service

## Configuración Inicial

### 1. Clonar Repositorio

```bash
git clone <repository-url>
cd rutatur
```

### 2. Configurar Variables de Entorno

Crear archivo `.env` en la raíz del proyecto:

```env
# PostgreSQL
POSTGRES_USER=rutatur
POSTGRES_PASSWORD=rutatur_pass
POSTGRES_DB=rutatur_db

# Keycloak
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin
KEYCLOAK_REALM=rutatur-realm

# Servicios Externos
OVERPASS_URL=https://overpass-api.de/api/interpreter
OSRM_URL=http://router.project-osrm.org

# JWT
JWT_ISSUER_URI=http://keycloak:8080/realms/rutatur-realm
```

### 3. Compilar Componentes

Primero compilar e instalar el componente común:

```bash
cd rutatur-common
mvn clean install
cd ..
```

Compilar todos los microservicios:

```bash
mvn clean package -DskipTests
```

## Levantar Servicios

### Despliegue Completo

```bash
docker-compose up -d
```

Este comando levantará:
1. PostgreSQL (espera a estar healthy)
2. Keycloak (espera a PostgreSQL)
3. poi-service (espera a PostgreSQL)
4. itinerary-service (espera a PostgreSQL, poi-service)
5. API Gateway (espera a todos los microservicios)

### Verificar Estado

```bash
# Ver contenedores
docker-compose ps

# Ver logs
docker-compose logs -f

# Ver logs de un servicio específico
docker-compose logs -f api-gateway
```

### Verificar Health Checks

```bash
# API Gateway
curl http://localhost:8080/actuator/health

# poi-service
curl http://localhost:8081/actuator/health

# itinerary-service
curl http://localhost:8082/actuator/health
```

## Configuración de Keycloak

### 1. Acceder a Keycloak Admin

URL: http://localhost:8180/admin
Usuario: admin
Contraseña: admin

### 2. Importar Realm

El proyecto incluye `keycloak/rutatur-realm.json` con la configuración predefinida:

1. Ir a `Create Realm` → `Import`
2. Seleccionar `keycloak/rutatur-realm.json`
3. Importar

El realm incluye:
- Clientes: `rutatur-api`, `rutatur-gateway`
- Roles: `ROLE_USER`, `ROLE_ADMIN`
- Usuarios de prueba: `user1` / `password`, `admin1` / `password`

### 3. Verificar Configuración

- Realm: `rutatur-realm`
- Clients configurados con URLs correctas
- Roles creados
- Usuarios de prueba disponibles

## Acceso a los Servicios

| Servicio | URL | Descripción |
|----------|-----|-------------|
| **API Gateway** | http://localhost:8080 | Punto de entrada principal |
| **Swagger UI (Gateway)** | http://localhost:8080/swagger-ui.html | Documentación unificada |
| **Keycloak** | http://localhost:8180 | Identity Provider |
| **Keycloak Admin** | http://localhost:8180/admin | Administración Keycloak |
| **pgAdmin** | http://localhost:5050 | Administración PostgreSQL (opcional) |
| **poi-service Swagger** | http://localhost:8081/swagger-ui.html | Swagger directo (dev) |
| **itinerary-service Swagger** | http://localhost:8082/swagger-ui.html | Swagger directo (dev) |

## Credenciales por Defecto

### PostgreSQL
- Host: localhost:5432
- Database: rutatur_db
- Usuario: rutatur
- Password: rutatur_pass

### Keycloak Admin
- Usuario: admin
- Password: admin

### pgAdmin
- Email: admin@rutatur.com
- Password: admin

### Usuarios de Prueba (Keycloak)
| Usuario | Password | Roles |
|---------|----------|-------|
| user1 | password | ROLE_USER |
| admin1 | password | ROLE_USER, ROLE_ADMIN |

## Comandos Útiles

### Detener Servicios

```bash
docker-compose down
```

### Detener y Eliminar Volúmenes

```bash
docker-compose down -v
```

### Rebuild Completo

```bash
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### Ver Logs en Tiempo Real

```bash
docker-compose logs -f api-gateway poi-service itinerary-service
```

### Ejecutar Comando en Contenedor

```bash
docker-compose exec postgres psql -U rutatur -d rutatur_db
```

### Escalar Servicios (Opcional)

```bash
docker-compose up -d --scale poi-service=2
```

## Troubleshooting

### Keycloak no inicia

- Verificar que PostgreSQL esté healthy
- Revisar logs: `docker-compose logs keycloak`
- Puede tardar 1-2 minutos en iniciar

### Microservicios no se conectan a Keycloak

- Verificar que Keycloak esté completamente iniciado
- Comprobar `JWT_ISSUER_URI` en configuración
- Revisar que el realm esté importado

### Error al conectar con APIs Externas

- Overpass/OSRM son públicos, pueden tener rate limits
- Verificar conectividad: `curl https://overpass-api.de/api/interpreter`
- En caso de problemas, usar servicios alternativos

### Puerto ya en uso

```bash
# Encontrar proceso usando puerto 8080
lsof -i :8080

# Matar proceso
kill -9 <PID>
```

### Limpiar todo y empezar de cero

```bash
docker-compose down -v
docker system prune -a
# Volver a levantar
docker-compose up -d
```

## Consideraciones de Producción

Para un despliegue real se deberían considerar:

- Usar HTTPS/TLS
- Configurar secrets con Docker Secrets o Vault
- Implementar límites de rate limiting
- Usar Redis para caché
- Configurar monitoring (Prometheus/Grafana)
- Implementar logging centralizado (ELK)
- Usar instancias propias de OSRM
- Configurar backups de PostgreSQL

**Nota**: Esta configuración es adecuada para entorno académico/desarrollo.
