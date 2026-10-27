# Documentación de APIs - RutaTur

## Información General

Todas las APIs siguen principios REST, utilizan JSON para intercambio de datos, validación con Bean Validation y documentación con OpenAPI 3.0.

**URL Base (a través de Gateway):** `http://localhost:8080`

## Servicios

| Servicio | Puerto Directo | Documentación Swagger | Descripción |
|----------|----------------|----------------------|-------------|
| API Gateway | 8080 | http://localhost:8080/swagger-ui.html | Punto de entrada unificado |
| poi-service | 8081 | http://localhost:8081/swagger-ui.html | Microservicio de POIs |
| itinerary-service | 8082 | http://localhost:8082/swagger-ui.html | Microservicio de Itinerarios |

## Autenticación

La autenticación se realiza mediante **OAuth 2.0 / OpenID Connect** con **Keycloak**.

### Obtener Token

```http
POST http://localhost:8180/realms/rutatur-realm/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=rutatur-api
&username=<usuario>
&password=<password>
```

### Usar Token

```http
Authorization: Bearer <access_token>
```

### Roles

| Rol | Permisos |
|-----|----------|
| `ROLE_USER` | Generar itinerarios, guardar itinerarios propios, ver historial propio |
| `ROLE_ADMIN` | Todos los permisos de USER + endpoints administrativos |

## POI Service

### 1. Buscar POIs públicos

Busca Puntos de Interés por ubicación geográfica.

```http
GET /api/pois/search?lat=-34.603722&lng=-58.381592&radius=1000&categories=MUSEUM,PARK,CAFE
```

**Parámetros Query:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `lat` | Double | Sí | Latitud del punto de búsqueda |
| `lng` | Double | Sí | Longitud del punto de búsqueda |
| `radius` | Integer | Sí | Radio de búsqueda en metros (máx 5000) |
| `categories` | String | No | Lista de categorías separadas por coma |

**Categorías disponibles:**
- `MUSEUM` - Museos
- `PARK` - Parques y plazas
- `CAFE` - Cafeterías y bares
- `VIEWPOINT` - Miradores
- `RESTAURANT` - Restaurantes
- `MONUMENT` - Monumentos
- `GALLERY` - Galerías de arte
- `THEATRE` - Teatros
- `CHURCH` - Iglesias y lugares religiosos
- `SHOPPING` - Centros comerciales

**Respuesta Exitosa (200 OK):**

```json
{
  "pois": [
    {
      "id": "poi-123",
      "name": "Museo Nacional de Bellas Artes",
      "category": "MUSEUM",
      "location": {
        "latitude": -34.5838,
        "longitude": -58.3927
      },
      "address": "Av. Del Libertador 1473, CABA",
      "visitTime": 90,
      "rating": 4.7,
      "tags": ["arte", "historia"]
    }
  ],
  "totalCount": 1
}
```

### 2. Obtener candidatos (Interno)

Endpoint interno para uso entre microservicios. No expuesto públicamente a través del Gateway.

```http
GET /internal/pois/candidates?lat=-34.603722&lng=-58.381592&radius=1000&categories=MUSEUM,PARK
```

## Itinerary Service

### 1. Generar itinerario optimizado

Genera un itinerario optimizado con tiempos de traslado reales. Puede ser llamado sin autenticación.

```http
POST /api/itinerary/generate
Content-Type: application/json
```

**Body:**

```json
{
  "startPoint": {
    "latitude": -34.603722,
    "longitude": -58.381592
  },
  "availableTime": 240,
  "preferences": ["MUSEUM", "PARK", "CAFE"],
  "radius": 1500,
  "transportMode": "WALKING",
  "maxPois": 5
}
```

**Campos:**

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `startPoint` | GeoPoint | Sí | Punto de partida (lat/lng) |
| `availableTime` | Integer | Sí | Tiempo disponible en minutos (mín 30, máx 480) |
| `preferences` | List<String> | Sí | Lista de categorías preferidas |
| `radius` | Integer | Sí | Radio de búsqueda en metros (mín 100, máx 3000) |
| `transportMode` | String | Sí | `WALKING` o `DRIVING` |
| `maxPois` | Integer | No | Máximo de POIs a incluir (por defecto 6) |

**Respuesta Exitosa (200 OK):**

```json
{
  "itineraryId": "it-abc123",
  "startPoint": {
    "latitude": -34.603722,
    "longitude": -58.381592
  },
  "route": [
    {
      "stopOrder": 1,
      "poi": {
        "id": "poi-123",
        "name": "Museo Nacional de Bellas Artes",
        "category": "MUSEUM",
        "location": {"latitude": -34.5838, "longitude": -58.3927},
        "address": "Av. Del Libertador 1473",
        "visitTime": 90,
        "rating": 4.7
      },
      "travelTimeFromPrevious": 25,
      "distanceFromPrevious": 1200,
      "arrivalTime": "10:25",
      "departureTime": "11:55",
      "visitDuration": 90
    }
  ],
  "totalTime": 205,
  "totalVisitTime": 150,
  "totalTravelTime": 55,
  "startTime": "10:00",
  "endTime": "13:25",
  "transportMode": "WALKING",
  "feasible": true
}
```

**Errores:**

| Código | Descripción | Motivo |
|--------|-------------|--------|
| `400` | Bad Request | Parámetros inválidos |
| `422` | Unprocessable Entity | No hay POIs candidatos o itinerario no viable |
| `503` | Service Unavailable | Error en integración con APIs externas |

### 2. Guardar itinerario

Guarda el itinerario generado en el historial del usuario autenticado.

```http
POST /api/itineraries
Authorization: Bearer <token>
Content-Type: application/json
```

**Body:**

```json
{
  "itineraryId": "it-abc123"
}
```

**Respuesta Exitosa (201 Created):**

```json
{
  "id": "saved-it-456",
  "userId": "user-123",
  "itineraryId": "it-abc123",
  "createdAt": "2026-10-27T10:30:00Z"
}
```

### 3. Listar itinerarios guardados

```http
GET /api/itineraries
Authorization: Bearer <token>
```

**Respuesta Exitosa (200 OK):**

```json
[
  {
    "id": "saved-it-456",
    "itineraryId": "it-abc123",
    "startPoint": {"latitude": -34.603722, "longitude": -58.381592},
    "totalTime": 205,
    "routeSummary": "Museo Nacional + Parque Lezama + Café Tortoni",
    "createdAt": "2026-10-27T10:30:00Z"
  }
]
```

### 4. Obtener itinerario guardado

```http
GET /api/itineraries/{id}
Authorization: Bearer <token>
```

### 5. Eliminar itinerario guardado

```http
DELETE /api/itineraries/{id}
Authorization: Bearer <token>
```

**Respuesta Exitosa (204 No Content):**

## Códigos de Error Comunes

| Código | Significado | Cuándo ocurre |
|--------|-------------|---------------|
| `400` | Bad Request | Validación de parámetros fallida |
| `401` | Unauthorized | Token faltante o inválido |
| `403` | Forbidden | Sin permisos para el recurso |
| `404` | Not Found | Recurso no encontrado |
| `422` | Unprocessable Entity | Regla de negocio violada (itinerario no viable) |
| `500` | Internal Server Error | Error interno del servidor |
| `503` | Service Unavailable | API externa no disponible |

## Ejemplos con cURL

### Generar itinerario sin autenticación

```bash
curl -X POST http://localhost:8080/api/itinerary/generate \
  -H "Content-Type: application/json" \
  -d '{
    "startPoint": {"latitude": -34.603722, "longitude": -58.381592},
    "availableTime": 180,
    "preferences": ["MUSEUM", "CAFE"],
    "radius": 1000,
    "transportMode": "WALKING"
  }'
```

### Buscar POIs

```bash
curl -X GET "http://localhost:8080/api/pois/search?lat=-34.603722&lng=-58.381592&radius=1000&categories=MUSEUM"
```

## Validación

Todos los requests son validados con **Bean Validation**:

- Coordenadas: latitud entre -90 y 90, longitud entre -180 y 180
- Tiempo disponible: entre 30 y 480 minutos
- Radio: entre 100 y 3000 metros
- Preferencias: no vacías
- Modo de transporte: solo `WALKING` o `DRIVING`
