# ADR-001: Elección de Arquitectura de Microservicios

## Estado
Aceptado

## Fecha
2026-10-27

## Contexto

El Trabajo Práctico Integrador requiere una solución backend basada en microservicios que demuestre:
- Responsabilidades funcionales diferenciadas
- Colaboración entre componentes
- Integración con sistemas externos
- Arquitectura distribuida

Necesitamos decidir el estilo arquitectónico a seguir.

## Decisión

Adoptamos **arquitectura de microservicios** con **2 microservicios funcionales** (`poi-service` e `itinerary-service`) más infraestructura (API Gateway).

## Alternativas Consideradas

### 1. Monolito
- **Pros**: Más simple, menos complejidad de red, desarrollo más rápido
- **Contras**: No cumple requisito de microservicios, no demuestra colaboración entre servicios, menor puntaje
- **Razón de rechazo**: No cumple con los requisitos obligatorios del TPI

### 2. Microservicios con 3+ servicios
- **Pros**: Mayor granularidad, mejor separación
- **Contras**: Mayor complejidad, más difícil de desplegar, riesgo de over-engineering
- **Razón de rechazo**: 2 servicios funcionales son suficientes si hay colaboración real

### 3. Microservicios con 2 servicios funcionales (Elegida)
- **Pros**: Cumple requisito mínimo, permite colaboración real, manejable, demostrable
- **Contras**: Menor granularidad que 3+ servicios
- **Razón de elección**: Balance óptimo entre cumplimiento y complejidad

## Consecuencias

### Positivas
- Cumple con requisito obligatorio (>= 2 microservicios funcionales)
- Permite demostrar límites funcionales claros
- Facilita escalamiento independiente
- Demuestra arquitectura distribuida
- Obligatorio para demostrar colaboración entre servicios

### Negativas
- Mayor complejidad que monolito
- Necesita orquestación (Docker Compose)
- Comunicación por red entre servicios
- Mayor esfuerzo de despliegue
- Necesita API Gateway

## Notas

La división se basa en responsabilidades funcionales:
- `poi-service`: Responsabilidad sobre POIs (búsqueda, enriquecimiento)
- `itinerary-service`: Responsabilidad sobre itinerarios (orquestación, optimización, persistencia)

Esta división es coherente con el dominio y justifica la colaboración real.
