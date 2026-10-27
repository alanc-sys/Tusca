# RutaTur - Motor de Rutas Turísticas y Optimización de Puntos de Interés

## Descripción del Proyecto

**RutaTur** es una API REST basada en arquitectura de microservicios que permite a los usuarios generar itinerarios turísticos optimizados. El usuario ingresa su punto de partida, tiempo disponible, preferencias temáticas (museos, parques, cafeterías, miradores, etc.) y el sistema genera un itinerario ordenado y optimizado con tiempos de traslado reales.

Además, incorpora un sistema de **Coincidencias de Rutas** que permite a usuarios con itinerarios similares descubrirse mutuamente y compartir información de contacto únicamente bajo **consentimiento mutuo**.

## Objetivo

Desarrollar una solución backend completa que demuestre los conceptos de microservicios, integración con APIs externas, seguridad, persistencia, contenerización y despliegue, cumpliendo con los lineamientos del Trabajo Práctico Integrador de Backend de Aplicaciones 2026.

## Características Principales

- **Generación de itinerarios optimizados**: Algoritmo heurístico que maximiza la cantidad de POIs visitables dentro del tiempo disponible
- **Tiempos de traslado reales**: Integración con servicios de ruteo (OSRM/OpenRouteService)
- **Búsqueda de POIs**: Integración con Overpass API (OpenStreetMap) o Google Places API
- **Itinerarios personalizados**: Basados en preferencias temáticas, ubicación, tiempo disponible y modo de traslado
- **Historial de itinerarios**: Guardado para usuarios autenticados
- **Coincidencias de Rutas**: Sugerencias de usuarios con recorridos similares basadas en superposición de POIs, proximidad y preferencias. Solicitud mutua con revelado de información mínima solo tras aceptación mutua
- **Arquitectura de microservicios**: Con API Gateway, múltiples servicios funcionales y componente común reutilizable
- **Seguridad**: Autenticación y autorización con Keycloak (OIDC + JWT + Roles)
- **Despliegue completo**: Orquestado con Docker Compose

## Stack Tecnológico

| Componente | Tecnología |
|------------|------------|
| Lenguaje | Java 17+ |
| Framework | Spring Boot 3.x |
| Microservicios | Spring Cloud Gateway |
| Seguridad | Spring Security + Keycloak (OIDC/JWT) |
| Persistencia | PostgreSQL |
| Integración Externa | Overpass API (OSRM/OpenStreetMap) / OpenRouteService |
| Documentación API | OpenAPI 3.0 / Swagger UI |
| Gestión de Dependencias | Maven |
| Contenerización | Docker & Docker Compose |
| Resiliencia | Resilience4j (Circuit Breaker, Retry, Timeout) |

## Arquitectura

El proyecto sigue el modelo C4 para la documentación arquitectónica. Ver [documentación de arquitectura](./docs/architecture/README.md) para más detalles.

### Diagrama de Contexto (C1)

![C1 - Diagrama de Contexto](./docs/architecture/c4-c1-context.png)

### Diagrama de Contenedores (C2)

![C2 - Diagrama de Contenedores](./docs/architecture/c4-c2-containers.png)

## Estructura del Proyecto

```
rutatur/
├── docs/                          # Documentación
│   ├── architecture/              # Diagramas C4 y arquitectura
│   ├── api/                       # Documentación de APIs
│   └── decisions/                 # ADRs (Architecture Decision Records)
├── rutatur-common/                # Componente JAR reutilizable
├── api-gateway/                   # API Gateway (Spring Cloud Gateway)
├── poi-service/                   # Microservicio de POIs
├── itinerary-service/             # Microservicio de Itinerarios
├── route-match-service/           # Microservicio de Coincidencias de Rutas
├── docker-compose.yml             # Orquestación de servicios
└── README.md                      # Este archivo
```

## Microservicios

| Servicio | Puerto | Responsabilidad |
|----------|--------|----------------|
| `api-gateway` | 8080 | Punto de entrada, enrutamiento, filtrado de seguridad |
| `poi-service` | 8081 | Búsqueda, enriquecimiento y caché de Puntos de Interés |
| `itinerary-service` | 8082 | Orquestación, optimización y persistencia de itinerarios |
| `route-match-service` | 8083 | Descubrimiento de coincidencias, cálculo de similitud, gestión de solicitudes y revelado condicionado de contacto |
| `keycloak` | 8080 (auth) | Identity Provider (OIDC/JWT) |
| `postgres` | 5432 | Base de datos relacional |

## APIs Principales

| Endpoint | Método | Auth | Descripción |
|----------|--------|------|-------------|
| `/api/pois/search` | GET | Opcional | Buscar POIs por ubicación, radio y categorías |
| `/api/itinerary/generate` | POST | Opcional | Generar itinerario optimizado |
| `/api/itineraries` | POST | Requerida (USER) | Guardar itinerario generado |
| `/api/itineraries` | GET | Requerida (USER) | Listar itinerarios guardados del usuario |
| `/api/routes/share` | POST | Requerida (USER) | Poner un itinerario compartible para coincidencias |
| `/api/routes/shareable` | GET | Requerida (USER) | Listar itinerarios propios compartibles |
| `/api/routes/matches/suggestions` | GET | Requerida (USER) | Sugerencias de rutas con alta similitud |
| `/api/routes/matches/request` | POST | Requerida (USER) | Enviar solicitud de coincidencia |
| `/api/routes/matches` | GET | Requerida (USER) | Bandeja de solicitudes (enviadas/recibidas) |
| `/api/routes/matches/{matchId}/accept` | POST | Requerida (USER) | Aceptar solicitud de coincidencia |
| `/api/routes/matches/{matchId}/reject` | POST | Requerida (USER) | Rechazar solicitud de coincidencia |
| `/api/routes/matches/{matchId}/contact` | GET | Requerida (USER) | Obtener información de contacto (solo tras aceptación mutua) |
| `/api/admin/**` | * | Requerida (ADMIN) | Endpoints administrativos |

Ver [documentación completa de APIs](./docs/api/README.md).

## Reglas de Negocio Clave

1. **Viabilidad temporal**: La suma de tiempos de visita + traslados debe ser ≤ tiempo disponible
2. **Orden secuencial**: Ruta ordenada partiendo desde el punto de inicio
3. **Sin repeticiones**: Un mismo POI no puede aparecer dos veces
4. **Tiempos reales**: Traslados calculados con servicio de ruteo, no distancia euclidiana
5. **Priorización inteligente**: Selección y ordenamiento según relevancia a preferencias
6. **Colaboración entre microservicios**: `itinerary-service` colabora con `poi-service` para obtener candidatos
7. **Compartir es opt-in**: Solo los itinerarios explícitamente marcados como compartibles participan en el sistema de coincidencias
8. **Consentimiento mutuo**: La información de contacto solo se revela cuando ambos usuarios aceptan la solicitud de coincidencia
9. **Similitud basada en superposición**: Las sugerencias se calculan con índice de Jaccard (POIs en común), proximidad de punto de partida, categorías y tiempo disponible
10. **Sin revelado unilateral**: El endpoint de contacto devuelve 403 si no existe una coincidencia aceptada entre ambos usuarios

## Cómo Ejecutar

### Prerequisitos

- Docker y Docker Compose
- Java 17+ (para desarrollo local)
- Maven 3.8+ (para desarrollo local)

### Despliegue con Docker Compose

```bash
# Clonar el repositorio
git clone <repo-url>
cd rutatur

# Levantar todos los servicios
docker-compose up -d

# Verificar estado
docker-compose ps

# Ver logs
docker-compose logs -f
```

### URLs de Acceso

| Servicio | URL | Credenciales |
|----------|-----|--------------|
| API Gateway | http://localhost:8080 | - |
| Swagger UI (Gateway) | http://localhost:8080/swagger-ui.html | - |
| Keycloak Admin | http://localhost:8180/admin | admin/admin |
| pgAdmin (opcional) | http://localhost:5050 | admin@rutatur.com/admin |

## Desarrollo

### Build local

```bash
# Compilar componente común e instalarlo en .m2
cd rutatur-common && mvn clean install

# Compilar todos los microservicios
mvn clean package
```

### Testing

```bash
# Ejecutar tests unitarios
mvn test

# Ejecutar tests con cobertura
mvn test jacoco:report
```

## Documentación Adicional

- [Arquitectura y Diagramas C4](./docs/architecture/README.md)
- [Definición del Problema](./docs/01-definicion-problema.md)
- [Diseño de Solución](./docs/02-diseno-solucion.md)
- [APIs y Contratos](./docs/api/README.md)
- [Decisiones Arquitectónicas (ADRs)](./docs/decisions/README.md)
- [Guía de Despliegue](./docs/deployment/README.md)

## Autores

Trabajo Práctico Integrador - Backend de Aplicaciones 2026

## Licencia

Proyecto académico - Uso educativo
