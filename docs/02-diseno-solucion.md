# 2. Diseño de la Solución

## 2.1 Objetivos del Diseño

- Cumplir con todos los requisitos del enunciado del TPI
- Demostrar colaboración real entre microservicios
- Integrar al menos una API pública externa de forma significativa
- Implementar autenticación/autorización con Keycloak
- Crear un componente común reutilizable como JAR Maven
- Desplegar integralmente con Docker Compose
- Generar un itinerario optimizado con tiempos de traslado reales

## 2.2 Arquitectura General

La solución sigue una arquitectura de microservicios con API Gateway como punto de entrada único, tres microservicios funcionales con colaboraciones reales (`poi-service`, `itinerary-service`, `route-match-service`), integración con sistemas externos y componente común reutilizable.

### Principios Aplicados

- **Separación de responsabilidades**: Cada microservicio tiene un límite funcional claro
- **Comunicación desacoplada**: REST entre microservicios
- **Seguridad centralizada**: Validación JWT en Gateway y Resource Server en microservicios
- **Reutilización**: Componente común para DTOs, utilidades y excepciones
- **Resiliencia**: Timeouts, reintentos, circuit breaker para integraciones externas
- **Configuración externa**: Variables de entorno para credenciales y URLs

## 2.3 Diagrama de Contexto (C1)

**Propósito**: Mostrar el sistema en su entorno, actores y sistemas externos.

### Elementos

| Elemento | Tipo | Descripción |
|----------|------|-------------|
| **Usuario Turista** | Actor | Usuario final que genera itinerarios |
| **Administrador** | Actor | Usuario con rol ADMIN para gestión |
| **RutaTur Backend** | Sistema | Sistema a desarrollar (TPI) |
| **Keycloak** | Sistema Externo | Identity Provider - Autenticación OIDC/OAuth2, gestión de roles |
| **Overpass API** | Sistema Externo | API pública de OpenStreetMap para obtener POIs |
| **OSRM** | Sistema Externo | Servicio de ruteo para cálculo de matriz de distancias/tiempos |

### Relaciones

- Usuario/Administrador → RutaTur Backend: Consumir APIs REST vía HTTPS
- RutaTur Backend → Keycloak: Autenticación, validación de tokens, obtención de roles
- RutaTur Backend → Overpass API: Búsqueda de POIs por ubicación/categorías
- RutaTur Backend → OSRM: Solicitud de matriz origen-destino para cálculo de rutas

## 2.4 Diagrama de Contenedores (C2)

**Propósito**: Mostrar la arquitectura interna del sistema - contenedores, microservicios, base de datos.

### Contenedores

| Contenedor | Tecnología | Puerto | Responsabilidad |
|------------|------------|--------|----------------|
| **API Gateway** | Spring Cloud Gateway | 8080 | Punto de entrada único. Enrutamiento a microservicios. Security Filter para validación JWT. CORS. |
| **poi-service** | Spring Boot 3.x | 8081 | Búsqueda y enriquecimiento de POIs. Integración con Overpass API. Caché de POIs cercanos. Normalización de datos. |
| **itinerary-service** | Spring Boot 3.x | 8082 | Orquestación de itinerarios. Colaboración con poi-service. Cliente de ruteo (OSRM). Motor de optimización. Persistencia de itinerarios. |
| **route-match-service** | Spring Boot 3.x | 8083 | Descubrimiento de coincidencias. Cálculo de similitud (Jaccard + distancia + preferencias). Gestión de solicitudes, estados y revelado condicionado de contacto con consentimiento mutuo. |
| **rutatur-common** | Maven JAR | N/A | Componente común reutilizable. DTOs, utilidades GeoUtils, excepciones, validadores. Usado por los microservicios. |
| **Keycloak** | Quarkus/Keycloak | 8180 | Identity Provider. Gestión de usuarios, roles, clientes OIDC. Emisión de tokens JWT. |
| **PostgreSQL** | PostgreSQL 15+ | 5432 | Base de datos relacional. Esquemas separados: `poi_schema`, `itinerary_schema`, `match_schema`. |
| **pgAdmin** | pgAdmin 4 | 5050 | Herramienta de administración (opcional para desarrollo) |

### Colaboración entre Microservicios

**Flujo 1: Generación de itinerario (colaboración significativa):**

```text
1. Cliente → API Gateway: POST /api/itinerary/generate (con JWT opcional)
2. API Gateway → itinerary-service: Enruta solicitud + valida JWT
3. itinerary-service → poi-service: GET /internal/pois/candidates (busca POIs candidatos)
4. poi-service → Overpass API: Consulta POIs por ubicación/radio/categorías
5. poi-service → itinerary-service: Retorna lista de POIs normalizados
6. itinerary-service → OSRM: POST /table/v1/walking (solicita matriz de tiempos)
7. OSRM → itinerary-service: Retorna matriz origen-destino
8. itinerary-service: Ejecuta motor de optimización (Greedy/Orienteering)
9. itinerary-service → API Gateway → Cliente: Retorna itinerario optimizado
```

Esta colaboración entre `itinerary-service` y `poi-service` es **real y necesaria** para el negocio, no artificial.

**Flujo 2: Coincidencias de rutas (colaboración real entre servicios):**

```text
1. Cliente → API Gateway: POST /api/routes/share {itineraryId} (autenticado)
2. API Gateway → route-match-service: Enruta solicitud
3. route-match-service → itinerary-service: GET /internal/itineraries/{id} (obtiene detalle de ruta para cálculo de similitud)
4. itinerary-service → route-match-service: Retorna itinerario con POIs, ubicación, preferencias, tiempo
5. route-match-service: Marca ruta como compartible (opt-in) y persiste en match_schema
6. Cliente → API Gateway: GET /api/routes/matches/suggestions
7. route-match-service: Calcula similitud entre rutas compartibles (Índice de Jaccard + distancia Haversine + preferencias + tiempo)
8. route-match-service → API Gateway → Cliente: Retorna sugerencias ordenadas por match_score
9. Cliente A → POST /api/routes/matches/request {targetRouteId} → route-match-service crea solicitud PENDING
10. Cliente B → POST /api/routes/matches/{matchId}/accept → route-match-service actualiza estado
11. Cliente A → POST /api/routes/matches/{matchId}/accept → Al aceptarse ambos, pasa a ACCEPTED
12. Cliente A/B → GET /api/routes/matches/{matchId}/contact (solo si ACCEPTED) → route-match-service revela información mínima de contacto
```

Esta colaboración entre `route-match-service` e `itinerary-service` demuestra **una tercera interacción real** entre microservicios, con reglas de negocio estrictas (opt-in, consentimiento mutuo, autorización para revelado).

## 2.5 División de Responsabilidades

### poi-service
**Responsabilidad única**: Gestión y provisión de Puntos de Interés

- Búsqueda de POIs por coordenadas, radio y categorías
- Integración con Overpass API
- Normalización y enriquecimiento de datos
- Caché en memoria/base de datos para reducir llamadas externas
- Exposición de endpoint interno para consumo por itinerary-service
- Validación de parámetros de búsqueda

### itinerary-service
**Responsabilidad única**: Orquestación y generación de itinerarios optimizados

- Orquestación del proceso completo de generación
- Consumo de poi-service para obtener candidatos
- Integración con servicio de ruteo para matriz de tiempos
- **Motor de optimización**: Selección y ordenamiento de POIs
- Validación de reglas de negocio (viabilidad temporal)
- Persistencia de itinerarios para usuarios autenticados
- Gestión de historial de itinerarios
- Exposición de endpoints internos para consulta por `route-match-service`

### route-match-service
**Responsabilidad única**: Descubrimiento y gestión de coincidencias de rutas

- Gestión de rutas compartibles (opt-in)
- Cálculo de similitud entre itinerarios usando **Índice de Jaccard** (intersección/unión de POIs)
- Cálculo de proximidad entre puntos de partida con `GeoUtils`
- Comparación de preferencias temáticas y tiempo disponible
- Generación de sugerencias ordenadas por `match_score`
- Gestión de solicitudes de coincidencia con estados (`PENDING`, `ACCEPTED`, `REJECTED`)
- Aplicación de regla de **consentimiento mutuo** para habilitar intercambio
- **Revelado condicionado**: Solo expone información de contacto mínima tras aceptación mutua
- Auditoría de revelados (`user_contact_view`)

### api-gateway
**Responsabilidad única**: Punto de entrada y seguridad perimetral

- Enrutamiento dinámico a microservicios
- Validación de tokens JWT
- Security Filter
- CORS configuration
- (Opcional) Rate limiting
- Propagación de headers de autenticación

### rutatur-common
**Responsabilidad única**: Elementos reutilizables transversales

- DTOs compartidos entre servicios
- `GeoUtils`: Cálculo Haversine, conversión de coordenadas
- Excepciones personalizadas (`PoiNotFoundException`, `ItineraryNotFeasibleException`, `MatchNotAllowedException`, `ContactNotRevealedException`)
- Validadores
- Constantes
- Enums compartidos (`MatchStatus`, `Category`, `TransportMode`)

## 2.6 Motores de Optimización y Similitud

### 2.6.1 Motor de Optimización de Itinerarios

El núcleo del sistema es el algoritmo de optimización que genera el itinerario ordenado.

#### Algoritmo Seleccionado: Greedy con priorización

**Justificación**: Simple, eficiente, justificable y adecuado para el alcance del TPI. Demuestra el concepto de "optimización" sin complejidad innecesaria. 

### 2.6.2 Motor de Similitud para Coincidencias

Para el sistema de coincidencias se utiliza un algoritmo híbrido ponderado:

| Criterio | Peso | Fórmula/Descripción |
|----------|------|---------------------|
| **Superposición de POIs (Jaccard)** | 40% | `J(A,B) = \|POIs(A) ∩ POIs(B)\| / \|POIs(A) ∪ POIs(B)\|` |
| **Proximidad de punto de partida** | 25% | Distancia Haversine entre startPoint. Menor distancia = mayor puntaje (normalizado) |
| **Coincidencia de categorías preferidas** | 20% | Superposición entre preferencias temáticas utilizadas para generar ambos itinerarios |
| **Similitud de tiempo disponible** | 15% | Cercanía entre `availableTime` (normalizado por diferencia) |

**Umbral mínimo**: `match_score >= 0.35` para ser mostrado en sugerencias. Solo se consideran rutas marcadas como compartibles y activas.

### Proceso del Algoritmo

1. **Inicialización**: Punto de partida + tiempo disponible + preferencias
2. **Obtención de candidatos**: Consulta a poi-service con filtros
3. **Cálculo de matriz**: Matriz de tiempos desde inicio y entre todos los candidatos
4. **Scoring**: Calcular puntaje por POI (basado en coincidencia con preferencias, rating)
5. **Selección Greedy**:
   - Ordenar candidatos por score descendente
   - Intentar agregar secuencialmente al itinerario
   - Para cada candidato: verificar factibilidad (traslado desde último punto + tiempo visita + traslados futuros estimados? o simplemente acumulado)
   - Si cabe dentro del tiempo restante: agregarlo y actualizar posición actual/tiempo usado
   - Si no cabe: descartar e intentar siguiente
6. **Construcción del itinerario**: Generar lista ordenada con horarios estimados

### Fórmula de Viabilidad

```java
tiempoAcumulado = sum(visitTime_i) + sum(travelTime_{i-1 -> i})
factible = tiempoAcumulado <= availableTime
```

### Extensiones Consideradas (para subir nivel)

- **Orienteering Problem**: Maximizar valor total con restricción presupuestaria temporal
- **2-opt**: Mejorar ruta inicial mediante intercambios
- **Ventanas horarias**: Respetar apertura/cierre de POIs
- **Múltiples criterios**: Balance entre cantidad vs calidad

## 2.7 Integración con APIs Externas

### Overpass API (POIs)
- **Endpoint**: `https://overpass-api.de/api/interpreter`
- **Método**: POST con query Overpass QL
- **Query ejemplo**: Buscar amenity=tourism/museum, leisure=park, amenity=cafe dentro de bbox/circle
- **Respuesta**: XML/JSON con nodos, way, relation
- **Transformación**: Mapear a modelo interno `PoiDto`

### OSRM (Ruteo)
- **Endpoint**: `http://router.project-osrm.org/table/v1/{profile}/{coordinates}`
- **Profile**: `walking` o `driving`
- **Método**: GET
- **Respuesta**: Matriz `durations[][]` y `distances[][]`
- **Uso**: Tabla de todos-vs-todos para origen + candidatos

### Manejo de Errores e Integración Resiliente

| Patrón | Aplicación | Configuración |
|--------|------------|---------------|
| **Timeout** | Todas las llamadas HTTP externas | 3-5 segundos |
| **Retry** | Errores transitorios (5xx, timeouts) | 2-3 reintentos con backoff exponencial |
| **Circuit Breaker** | Protección ante caídas prolongadas | Abierto tras 50% errores, half-open tras 30s |
| **Fallback** | Cuando servicio externo no responde | Usar caché local o retornar error 503 con mensaje claro |
| **Bulkhead** | Limitar llamadas concurrentes | Semáforos/thread pools limitados |

## 2.8 Seguridad con Keycloak

### Configuración OIDC

- **Realm**: `rutatur-realm`
- **Clients**: 
  - `rutatur-gateway` (confidential, para Gateway)
  - `rutatur-api` (bearer-only/resource server)
- **Roles**: `ROLE_USER`, `ROLE_ADMIN`
- **Grant Type**: Authorization Code + PKCE (para clientes web) / Client Credentials (servicios)

### Flujo de Autenticación

1. Usuario se autentica en Keycloak → recibe Authorization Code
2. Cliente intercambia code por access_token (JWT) + refresh_token
3. Cliente envía requests con header `Authorization: Bearer <token>`
4. API Gateway valida JWT (firma, issuer, expiración) con Keycloak JWKS
5. Gateway reenvía request con token al microservicio
6. Microservicio actúa como Resource Server, valida token y autoriza por roles

### Trusted Zone

- **Perimetral**: Solo Gateway expuesto externamente
- **Interna**: Comunicación entre microservicios en red Docker privada
- **Validación**: Todos los endpoints protegidos validan JWT
- **Autorización**: Basada en roles (`@PreAuthorize("hasRole('USER')")`)

### Endpoints de Seguridad

| Endpoint | Acceso | Roles |
|----------|--------|-------|
| `POST /api/itinerary/generate` | Público/Opcional | - (puede funcionar sin auth) |
| `GET /api/pois/search` | Público | - |
| `POST /api/itineraries` | Protegido | `ROLE_USER` |
| `GET /api/itineraries` | Protegido | `ROLE_USER` |
| `GET /api/itineraries/{id}` | Protegido | `ROLE_USER` (solo propios) |
| `POST /api/routes/share` | Protegido | `ROLE_USER` |
| `GET /api/routes/shareable` | Protegido | `ROLE_USER` |
| `GET /api/routes/matches/suggestions` | Protegido | `ROLE_USER` |
| `POST /api/routes/matches/request` | Protegido | `ROLE_USER` |
| `GET /api/routes/matches` | Protegido | `ROLE_USER` |
| `POST /api/routes/matches/{matchId}/accept` | Protegido | `ROLE_USER` (solo participantes) |
| `POST /api/routes/matches/{matchId}/reject` | Protegido | `ROLE_USER` (solo participantes) |
| `GET /api/routes/matches/{matchId}/contact` | Protegido | `ROLE_USER` (solo participantes con match ACCEPTED) |
| `GET /api/admin/**` | Protegido | `ROLE_ADMIN` |

## 2.9 Persistencia

### Estrategia: Esquema por microservicio en misma instancia PostgreSQL

**Justificación**: 
- Demuestra separación lógica de datos entre microservicios
- Más simple que BD separadas para el entorno Docker Compose
- Permite mostrar independencia conceptual
- Fácil de administrar

### Esquemas

#### poi_schema (poi-service)
| Tabla | Propósito | Campos Clave |
|-------|-----------|--------------|
| `poi_cache` | Caché de POIs consultados | id, external_id, name, category, lat, lng, visit_time, cached_at, expires_at |

#### itinerary_schema (itinerary-service)
| Tabla | Propósito | Campos Clave |
|-------|-----------|--------------|
| `itinerary` | Itinerarios guardados | id, user_id, request_data (JSONB), response_data (JSONB), created_at, updated_at |
| `itinerary_stop` | Paradas del itinerario (opcional) | id, itinerary_id, poi_id, stop_order, arrival_time, departure_time |

#### match_schema (route-match-service)
| Tabla | Propósito | Campos Clave |
|-------|-----------|--------------|
| `shared_route` | Itinerarios marcados como compartibles (opt-in) | id, itinerary_id, owner_user_id, share_token, is_active, expires_at, created_at |
| `route_match` | Solicitudes/coincidencias entre usuarios | id, requester_user_id, responder_user_id, shared_route_a_id, shared_route_b_id, match_score, matched_poi_count, matched_pois (JSONB), status (PENDING/ACCEPTED/REJECTED), requested_at, responded_at, accepted_at |
| `user_contact_view` | Auditoría de revelado de contacto (solo tras ACCEPTED) | id, route_match_id, viewer_user_id, viewed_user_id, revealed_at, contact_info (JSONB mínimo) |

### Modelo de Datos Detallado

**itinerary (itinerary-service)**
```sql
CREATE TABLE itinerary (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id VARCHAR(255) NOT NULL,
  start_lat DECIMAL(10,7),
  start_lng DECIMAL(10,7),
  available_time INTEGER,
  transport_mode VARCHAR(20),
  request_data JSONB,
  response_data JSONB NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_itinerary_user ON itinerary(user_id);
```

## 2.10 Componente Común Reutilizable (rutatur-common)

### Estructura

```
rutatur-common/
├── src/main/java/com/rutatur/common/
│   ├── dto/           # DTOs compartidos
│   ├── exception/     # Excepciones personalizadas
│   ├── util/          # Utilidades (GeoUtils)
│   ├── validator/     # Validadores
│   └── constant/      # Constantes y Enums
└── pom.xml
```

### Elementos Principales

| Clase/Paquete | Descripción | Uso |
|---------------|-------------|-----|
| `dto/PoiDto` | DTO para POI normalizado | Usado por poi-service e itinerary-service |
| `dto/ItineraryRequestDto` | Request para generar itinerario | Compartido |
| `dto/ItineraryResponseDto` | Response con itinerario generado | Compartido |
| `dto/RouteMatrixDto` | DTO para matriz de rutas | Compartido |
| `util/GeoUtils` | Cálculo distancia Haversine, validación coords | Usado por ambos servicios |
| `exception/BusinessException` | Excepción base de negocio | Transversal |
| `exception/ItineraryNotFeasibleException` | Itinerario no viable | itinerary-service |
| `exception/PoiServiceException` | Error en poi-service | itinerary-service |
| `constant/Category` | Enum de categorías de POI | Compartido |
| `constant/TransportMode` | Enum modo de transporte | Compartido |

### Instalación

```bash
cd rutatur-common
mvn clean install
```

Esto instala el artefacto en el repositorio Maven local (`~/.m2/repository/com/rutatur/rutatur-common/...`), y los microservicios lo referencian como dependencia en su `pom.xml`.

## 2.11 APIs REST y Documentación OpenAPI

### poi-service APIs

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `GET` | `/api/pois/search` | Buscar POIs públicos | No |
| `GET` | `/internal/pois/candidates` | Obtener candidatos para itinerario (interno) | Interno |
| `GET` | `/actuator/health` | Health check | No |

### itinerary-service APIs

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/api/itinerary/generate` | Generar itinerario optimizado | Opcional |
| `POST` | `/api/itineraries` | Guardar itinerario | ROLE_USER |
| `GET` | `/api/itineraries` | Listar itinerarios del usuario | ROLE_USER |
| `GET` | `/api/itineraries/{id}` | Obtener itinerario por ID | ROLE_USER |
| `DELETE` | `/api/itineraries/{id}` | Eliminar itinerario | ROLE_USER |
| `GET` | `/actuator/health` | Health check | No |

### Documentación

Cada microservicio expone Swagger UI:
- poi-service: `http://localhost:8081/swagger-ui.html`
- itinerary-service: `http://localhost:8082/swagger-ui.html`
- route-match-service: `http://localhost:8083/swagger-ui.html`
- Gateway agrega documentación: `http://localhost:8080/swagger-ui.html`

## 2.12 Contenerización y Despliegue

### docker-compose.yml Estructura

Servicios a orquestar:
- `postgres` - Base de datos
- `keycloak` - Identity Provider
- `api-gateway` - Gateway
- `poi-service` - Microservicio POIs
- `itinerary-service` - Microservicio Itinerarios
- `pgadmin` - Admin DB (opcional)

### Redes

- `rutatur-network`: Red bridge para comunicación entre servicios

### Volúmenes

- `postgres-data`: Persistencia de PostgreSQL
- `keycloak-data`: Persistencia de Keycloak

### Health Checks

Todos los servicios con `depends_on` y `healthcheck` para garantizar orden de inicio.

## 2.13 Decisiones Arquitectónicas Clave

| Decisión | Alternativas Consideradas | Razón de Elección |
|----------|---------------------------|-------------------|
| **Microservicios con 2 servicios funcionales** | Monolito, 3+ servicios | Cumple requisito mínimo, permite colaboración real, mantiene complejidad manejable |
| **API Gateway** | Directo a servicios, Service Mesh | Requisito del TPI, centraliza seguridad y enrutamiento |
| **Keycloak como IdP** | JWT propio, OAuth2 simple | Requisito del TPI, estándar OIDC, gestión completa |
| **Overpass + OSRM** | Google Places + Google Routes | Gratuitos, sin complejidad de billing, ideales para TPI |
| **Esquema por servicio en misma BD** | BD por servicio | Balance entre separación lógica e infraestructura simple |
| **Algoritmo Greedy** | TSP exacto, Genetic Algorithm | Justificable, comprensible, demuestra optimización |
| **Componente común JAR** | Copiar código, Shared module | Requisito del TPI, reutilización real |
| **Spring Boot** | Quarkus, Micronaut | Conocimiento del curso, ecosistema maduro |

## 2.14 Cumplimiento de Requisitos del Enunciado

| Requisito | Cumplimiento | Evidencia |
|-----------|--------------|-----------|
| >= 2 microservicios funcionales | Sí | poi-service, itinerary-service |
| Colaboración entre microservicios | Sí | itinerary-service → poi-service |
| API Gateway + Security Filter | Sí | api-gateway con Spring Cloud Gateway |
| Keycloak OIDC/JWT/Roles | Sí | Configurado en realm |
| Integración API externa | Sí | Overpass API + OSRM |
| Componente común JAR usado por ambos ms | Sí | rutatur-common instalado en .m2 |
| Persistencia DB relacional | Sí | PostgreSQL |
| APIs REST + OpenAPI/Swagger | Sí | Documentación en cada servicio |
| Docker Compose integral | Sí | Todos los componentes orquestados |
| C1 y C2 con C4 Model | Sí | Diagramas en docs/architecture |
| Justificación de decisiones | Sí | Documentación y ADRs |
