# Architecture Decision Records (ADRs)

Este directorio contiene los registros de decisiones arquitectónicas tomadas durante el diseño de RutaTur.

## ¿Qué es un ADR?

Un ADR documenta una decisión arquitectónica importante junto con su contexto, alternativas consideradas y consecuencias.

## ADRs del Proyecto

| ADR | Título | Estado | Fecha |
|-----|--------|--------|-------|
| [ADR-001](./ADR-001-arquitectura-microservicios.md) | Elección de Arquitectura de Microservicios | Aceptado | 2026-10-27 |
| [ADR-002](./ADR-002-api-gateway.md) | Uso de API Gateway | Aceptado | 2026-10-27 |
| [ADR-003](./ADR-003-keycloak-idp.md) | Keycloak como Identity Provider | Aceptado | 2026-10-27 |
| [ADR-004](./ADR-004-integracion-apis-externas.md) | Integración con APIs Externas (Overpass + OSRM) | Aceptado | 2026-10-27 |
| [ADR-005](./ADR-005-componente-comun-jar.md) | Componente Común Reutilizable como JAR Maven | Aceptado | 2026-10-27 |
| [ADR-006](./ADR-006-estrategia-persistencia.md) | Estrategia de Persistencia por Esquema | Aceptado | 2026-10-27 |
| [ADR-007](./ADR-007-algoritmo-optimizacion.md) | Algoritmo de Optimización Greedy | Aceptado | 2026-10-27 |
| [ADR-008](./ADR-008-colaboracion-microservicios.md) | Colaboración entre poi-service e itinerary-service | Aceptado | 2026-10-27 |

## Plantilla

Para crear nuevos ADRs usar esta plantilla:

```markdown
# ADR-XXX: Título

## Estado
[Propuesto | Aceptado | Rechazado | Obsoleto | Supersedido por ADR-YYY]

## Fecha
YYYY-MM-DD

## Contexto
¿Qué problema estamos tratando de resolver?

## Decisión
¿Qué decisión tomamos?

## Alternativas Consideradas
¿Qué otras opciones evaluamos?

## Consecuencias
### Positivas
- ...
### Negativas
- ...

## Notas
...
```
