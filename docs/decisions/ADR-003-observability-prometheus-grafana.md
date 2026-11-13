# ADR-003: Observabilidad con Prometheus y Grafana (Auto-provisioning)

- **Estado:** Aceptada
- **Contexto:** Para demostrar trazabilidad en tiempo real durante la demo (`INT-01`, `INF-01`) y diagnosticar latencias, errores 4xx/5xx y estado de servicios, se requiere una capa visual de monitoreo sin sobrecargar la infraestructura local con stacks pesados de logs/trazas (Loki/Tempo).

## Decisión

Se incorpora `infra/monitoring/` con **Prometheus** (scraping cada 15s en `127.0.0.1:9090`) y **Grafana** (`http://localhost:3000`) con datasource y dashboard (`RutaTur — Microservices Overview`) **pre-provisionados automáticamente** al levantar Docker.

## Regla para próximos servicios

Todo nuevo microservicio Spring Boot que se implemente **debe**:
1. Incluir la dependencia `io.micrometer:micrometer-registry-prometheus`.
2. Exponer `prometheus` en `management.endpoints.web.exposure.include` (y permitir `/actuator/prometheus` dentro de la red interna).
3. Agregar su `job_name` en `infra/monitoring/prometheus/prometheus.yml`.
