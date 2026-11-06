# Architecture Decision Records (ADRs)

Este directorio contiene los registros de decisiones arquitectónicas tomadas durante el diseño de RutaTur.

## ¿Qué es un ADR?

Un ADR documenta una decisión arquitectónica importante junto con su contexto, alternativas consideradas y consecuencias.

## ADRs del Proyecto

| ADR | Título | Estado | Fecha |
|-----|--------|--------|-------|
| [ADR-001](./ADR-001-arquitectura-microservicios.md) | Elección de Arquitectura de Microservicios | Supersedido por ADR-002 | 2026-10-27 |
| [ADR-002](./ADR-002-tres-servicios-funcionales.md) | Tres Servicios Funcionales, Database per Service y Frontera de Seguridad en el Gateway | Aceptado (reemplaza ADR-001) | 2026-11-03 |

> ADR-002 reemplaza a ADR-001: la arquitectura objetivo tiene tres servicios funcionales
> (`poi-service`, `itinerary-service`, `route-match-service`), no dos.

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
