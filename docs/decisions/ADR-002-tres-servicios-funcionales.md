# ADR-002: Tres Servicios Funcionales, Esquema por Servicio y Frontera de Seguridad en el Gateway

## Estado

Aceptado. **Reemplaza a ADR-001** (`ADR-001-arquitectura-microservicios.md`), que fijaba dos servicios funcionales.

## Fecha

2026-11-03

## Contexto

ADR-001 eligió una arquitectura de microservicios con dos servicios funcionales (`poi-service`, `itinerary-service`) más el API Gateway. El diseño posterior de la solución incorporó un tercer servicio, `route-match-service`, responsable de compartir rutas de forma opt-in, calcular similitud entre itinerarios, gestionar el ciclo de vida de las coincidencias y revelar información de contacto solo con consentimiento mutuo.

Ese tercer servicio no es un desdoblamiento artificial del itinerario: tiene reglas de negocio propias (opt-in, consentimiento mutuo, no revelado unilateral, auditoría de accesos), un modelo de datos propio (`match_schema`) y consume al servicio de itinerarios a través de un endpoint interno. Además, la documentación de despliegue y la lista de componentes de Compose no lo incluían, y ADR-001 seguía afirmando que existían dos servicios funcionales.

En paralelo, el diseño de seguridad necesitaba una decisión explícita sobre dónde termina la confianza: el gateway es el único punto de entrada, los servicios validan el token como resource servers y los endpoints internos no deben ser alcanzables desde el exterior.

## Decisión

Se adoptan tres decisiones complementarias:

1. **Tres servicios funcionales**: `poi-service` (POIs y caché), `itinerary-service` (orquestación, optimización, historial) y `route-match-service` (coincidencias y consentimiento), además de `rutatur-common` como JAR compartido y `api-gateway` como único ingreso externo.
2. **Esquema por servicio en una única instancia PostgreSQL**: `poi_schema`, `itinerary_schema` y `match_schema`, con un rol de base de datos por servicio (`rutatur_poi`, `rutatur_itinerary`, `rutatur_match`) que solo tiene `USAGE` sobre su propio esquema. Ningún servicio lee ni escribe el esquema de otro; las colaboraciones ocurren por HTTP, no por base de datos.
3. **Frontera de seguridad en el gateway**: solo el gateway se publica en todas las interfaces; valida firma, emisor y expiración del JWT, aplica roles (`ROLE_USER`, `ROLE_ADMIN`), aplica CORS y no enruta `/internal/**`. Los servicios funcionales actúan como resource servers y derivan la identidad del `sub` del token, nunca de un identificador enviado por el cliente. Los endpoints internos se protegen además con un token compartido y con puertos publicados únicamente en loopback para desarrollo.

## Alternativas Consideradas

### 1. Mantener dos servicios funcionales (ADR-001)

- **Pros**: menor cantidad de contenedores, menos contratos entre servicios, despliegue más simple.
- **Contras**: las reglas de coincidencias, consentimiento y auditoría quedarían dentro de `itinerary-service`, mezclando dos dominios con ciclos de vida distintos y sin una tercera colaboración real entre servicios.
- **Motivo de rechazo**: el diseño ya especifica `route-match-service` con responsabilidades, esquema y endpoints propios; forzarlo dentro de itinerarios degrada la separación de responsabilidades que el propio ADR-001 declara como principio.

### 2. Base de datos por servicio

- **Pros**: aislamiento físico total, escalamiento y respaldo independientes.
- **Contras**: tres instancias de PostgreSQL en Compose, mayor consumo de memoria y operación, sin beneficio demostrable para el alcance académico.
- **Motivo de rechazo**: el esquema por servicio más roles separados logra el aislamiento lógico exigido con una sola instancia, y las violaciones de propiedad fallan con error de permisos en lugar de depender de disciplina del código.

### 3. Seguridad distribuida en cada servicio (sin gateway como frontera)

- **Pros**: no hay un punto único de falla; cada servicio decide su política.
- **Contras**: cuatro configuraciones de seguridad que pueden divergir, CORS replicado, rutas internas expuestas si un servicio se publica por error.
- **Motivo de rechazo**: el gateway como frontera única permite una matriz de autorización verificable en un solo lugar, y los servicios conservan la validación como defensa en profundidad.

## Consecuencias

### Positivas

- Cada servicio tiene un límite funcional y un esquema de datos explícitos, con permisos de base de datos que hacen cumplir la separación.
- Existen tres colaboraciones reales y verificables: itinerarios consulta POIs, itinerarios consulta el motor de ruteo, coincidencias consulta itinerarios.
- La matriz de autorización se concentra en el gateway y se replica como defensa en profundidad en cada servicio.
- El despliegue sigue siendo un único `docker compose up -d` con una sola instancia de PostgreSQL.
- Las coincidencias no acceden a la base de itinerarios ni comparten contactos antes de la aceptación mutua.

### Negativas

- Un contenedor y un contrato HTTP más que mantener y desplegar.
- La disponibilidad de coincidencias depende de `itinerary-service`; su caída se traduce en `503`, no en datos parciales.
- La incorporación del tercer servicio obliga a corregir la documentación de despliegue y la lista de componentes de Compose.
- El rol de base de datos por servicio exige inicialización de roles y contraseñas por variable de entorno.

## Notas

- Contratos entre servicios, códigos de estado, tiempos de espera y políticas de resiliencia quedan fijados en `openspec/changes/rutatur-platform/design.md`.
- `ADR-001` no se modifica; esta ADR lo reemplaza y debe citarse en su lugar.
- El índice `docs/decisions/README.md` lista un `ADR-002` previsto como "Uso de API Gateway" que no existe como archivo; la numeración debe corregirse en la fase de tareas para evitar dos ADR-002 con títulos distintos.
