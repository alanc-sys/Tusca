# ADR-001: Estrategia de Persistencia (Database-per-Service sobre instancia única)

- **Estado:** Aceptada
- **Contexto:** La arquitectura de RutaTur consta de 3 microservicios funcionales (`poi-service`, `itinerary-service`, `route-match-service`) más `Keycloak` como Identity Provider. Cada uno posee su propio modelo de datos sin foreign keys físicas entre dominios.

## Decisión

Se adopta **una única instancia física de PostgreSQL 15** en `infra/postgres/`, con **bases de datos y credenciales dedicadas e independientes** para cada componente:

| Componente | Base de datos | Usuario exclusivo |
| --- | --- | --- |
| Keycloak | `keycloak_db` | `keycloak_user` |
| POI Service | `poi_db` | `poi_user` |
| Itinerary Service | `itinerary_db` | `itinerary_user` |
| Route Match Service | `match_db` | `match_user` |

Cada base revoca privilegios de `PUBLIC` y otorga permisos exclusivamente a su usuario dueño.

## Alternativa ideal en producción (y por qué no se usa acá)

En un entorno productivo real, lo recomendable es **desacoplar también a nivel físico**: un motor/instancia de base de datos independiente por cada microservicio (contenedores o clusters separados), para evitar contención de CPU/I/O, aislar fallos y permitir escalado o cambio de motor independiente.

Sin embargo, para el alcance académico y de demostración reproducible del TPI (`docker compose up`), levantar 4 motores PostgreSQL en paralelo multiplica innecesariamente el consumo de RAM/CPU y el tiempo de arranque sin aportar valor funcional adicional.

## Consecuencias

- **Positivas:** Se garantiza el aislamiento lógico del patrón *Database-per-Service* (ningún microservicio tiene credenciales ni permisos para leer o hacer `JOIN` sobre las tablas de otro) con bajo consumo de recursos.
- **Negativas / Tradeoff aceptado:** Los servicios comparten recursos físicos del motor PostgreSQL. La migración futura a instancias físicas separadas solo requiere cambiar la URL JDBC en las variables de entorno de cada microservicio.
