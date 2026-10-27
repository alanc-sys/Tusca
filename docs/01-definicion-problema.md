# 1. Definición del Problema

## 1.1 Contexto General

Los turistas (y habitantes que desean descubrir su ciudad) suelen planificar recorridos por zonas turísticas sin tener en cuenta el tiempo real de traslado entre puntos, los horarios disponibles, el tiempo que desean dedicar al recorrido o sus intereses temáticos. 

Esto deriva en:
- Itinerarios poco realistas (muy ambiciosos, con traslados largos inesperados)
- Mal aprovechamiento del tiempo disponible
- Frustración al descubrir que no es posible completar lo planificado
- Desconocimiento de POIs relevantes cercanos

## 1.2 Actores Involucrados

| Actor | Descripción | Interacción |
|-------|-------------|-------------|
| **Turista/Usuario Final** | Persona que desea generar un itinerario turístico personalizado | Ingresa parámetros, genera itinerario, guarda itinerarios en su historial, comparte rutas para coincidencias |
| **Administrador** | Gestiona el sistema, catálogos y monitoreo | Administra configuración, visualiza métricas, gestiona caché |
| **Sistemas Externos** | Proveedores de servicios geoespaciales | Proveen información de POIs y cálculos de ruteo |

## 1.3 Proceso Principal

El flujo principal del sistema es el siguiente:

1. **Solicitud**: El usuario ingresa su punto de partida (coordenadas o dirección), tiempo disponible (en minutos/horas), preferencias temáticas, radio de búsqueda, modo de traslado y restricciones opcionales.
2. **Búsqueda**: El sistema busca Puntos de Interés relevantes según preferencias temáticas y ubicación geográfica.
3. **Filtrado**: Se filtran los candidatos según radio, categoría y restricciones aplicables.
4. **Cálculo de matriz**: Se calcula una matriz de tiempos y distancias reales entre todos los puntos (inicio + candidatos) para el modo de traslado seleccionado.
5. **Optimización**: Se genera un itinerario ordenado optimizado que maximiza la cantidad/valor de POIs visitables dentro del tiempo disponible.
6. **Presentación**: Se muestra el itinerario con orden secuencial, horarios estimados, tiempos de traslado, detalles de cada POI.
7. **Coincidencias (opcional)**: El usuario puede poner su itinerario como compartible. El sistema sugiere otros usuarios con recorridos similares, gestiona solicitudes con consentimiento mutuo y, solo tras aceptación mutua, habilita el intercambio de información de contacto mínima.

## 1.4 Información Relevante

### Puntos de Interés (POIs)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID/String | Identificador único |
| `name` | String | Nombre del POI |
| `category` | Enum | Categoría/temática (MUSEUM, PARK, CAFE, VIEWPOINT, etc.) |
| `location` | GeoPoint | Coordenadas (latitud, longitud) |
| `address` | String | Dirección |
| `openingHours` | String/JSON | Horarios de apertura/cierre |
| `visitTime` | Integer | Tiempo estimado de visita en minutos |
| `rating` | Decimal | Calificación (0-5) |
| `priceRange` | String | Rango de precios aproximado |
| `tags` | List<String> | Etiquetas adicionales |

### Solicitud de Itinerario
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `startPoint` | GeoPoint | Punto de partida |
| `availableTime` | Integer | Tiempo disponible en minutos |
| `preferences` | List<Category> | Preferencias temáticas |
| `radius` | Integer | Radio de búsqueda en metros |
| `transportMode` | Enum | Modo de traslado (WALKING, DRIVING) |
| `dateTime` | DateTime | Fecha/hora de inicio (opcional) |
| `maxPois` | Integer | Límite máximo de POIs (opcional) |

### Itinerario Generado
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `itineraryId` | UUID | Identificador del itinerario |
| `route` | List<ItineraryStop> | Lista ordenada de paradas |
| `totalTime` | Integer | Tiempo total estimado (visita + traslados) |
| `totalVisitTime` | Integer | Suma de tiempos de visita |
| `totalTravelTime` | Integer | Suma de tiempos de traslado |
| `startTime` | DateTime | Hora estimada de inicio |
| `endTime` | DateTime | Hora estimada de finalización |

## 1.5 Reglas de Negocio

| Regla | Descripción | Justificación |
|-------|-------------|---------------|
| **RN-01: Viabilidad temporal** | El itinerario debe caber dentro del tiempo disponible. Suma de tiempos de visita + traslados entre puntos consecutivos ≤ tiempo disponible | Garantiza itinerarios realistas y alcanzables |
| **RN-02: Orden secuencial** | El recorrido debe ser secuencial, partiendo obligatoriamente del punto de inicio | Refleja un recorrido físico real por la ciudad |
| **RN-03: Sin repeticiones** | Un mismo POI no puede aparecer más de una vez en el itinerario | Evita redundancias |
| **RN-04: Tiempos de traslado reales** | Los tiempos entre puntos deben provenir del servicio de ruteo, no de distancia euclidiana | Esencial para la optimización real |
| **RN-05: Tiempo de visita por POI** | Cada POI tiene un tiempo de visita estimado que se descuenta del tiempo disponible | Considera el tiempo dedicado en cada lugar |
| **RN-06: Priorización** | Ante tiempo limitado, se priorizan POIs más relevantes según preferencias o mejor calificados | Maximiza la satisfacción del usuario |
| **RN-07: Respeto al radio de búsqueda** | Solo se consideran POIs dentro del radio especificado desde el punto de partida | Limita el ámbito geográfico del recorrido |
| **RN-08: Factibilidad** | Se debe validar que al menos el traslado al primer POI sea viable | Evita itinerarios imposibles |
| **RN-09: Compartir es opt-in** | Solo los itinerarios marcados explícitamente como compartibles participan en el sistema de coincidencias | Garantiza control y privacidad por parte del usuario |
| **RN-10: Consentimiento mutuo** | La información de contacto solo se revela cuando ambos usuarios aceptan la solicitud de coincidencia | Respeta la privacidad y evita revelados unilaterales |
| **RN-11: Similitud justificada** | Las sugerencias se calculan con índice de Jaccard (superposición de POIs), proximidad de punto de partida, categorías preferidas y tiempo disponible | Asegura coincidencias relevantes entre recorridos |
| **RN-12: Autorización para revelar contacto** | El acceso a la información de contacto solo está permitido a usuarios que formen parte de una coincidencia con estado `ACCEPTED` | Impide acceso no autorizado a datos de contacto |

## 1.6 Información Externa Necesaria

| Servicio | Propósito | Tipo | Justificación |
|----------|-----------|------|---------------|
| **Overpass API (OpenStreetMap)** | Obtener POIs por ubicación, radio y categorías | Gratuita, pública | Provee datos geoespaciales abiertos sin costo complejo |
| **Google Places API (New)** | Alternativa para obtener POIs con mayor enriquecimiento | Requiere API Key | Mayor cantidad de metadatos (fotos, reseñas, horarios detallados) |
| **OSRM** | Calcular matriz de tiempos/distancias por modo walking/driving | Self-hostable, abierto | Esencial para obtener tiempos de traslado reales |
| **OpenRouteService** | Alternativa para ruteo/matriz | Requiere API Key (con cuota gratuita) | API REST completa para cálculo de rutas |

**Decisión**: Se utilizarán **Overpass API** y **OSRM** por ser gratuitos y adecuados al entorno académico, aunque el diseño permite intercambiarlos fácilmente mediante el patrón Adapter.

## 1.7 Alcance

### Dentro del alcance
- Generación de itinerarios optimizados con tiempos reales
- Búsqueda de POIs por preferencias temáticas
- Cálculo de matriz origen-destino
- Historial de itinerarios para usuarios autenticados
- **Coincidencias de rutas con consentimiento mutuo**
- **Sugerencias basadas en similitud (Jaccard + proximidad + preferencias)**
- **Solicitud/aceptación/rechazo y revelado condicionado de información de contacto mínima**
- Autenticación/autorización con roles
- Arquitectura de microservicios completa
- Despliegue contenerizado

### Fuera del alcance
- Navegación turn-by-turn en tiempo real
- Reservas o compras
- Integración con transporte público en tiempo real
- Aplicación móvil nativa (solo API REST)
- Sistema de reviews/comentarios complejo
- Pagos
- Chat/mensajería entre usuarios
- Sistema de seguidores/amigos
- Geolocalización en tiempo real
