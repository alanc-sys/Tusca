#!/usr/bin/env bash
#
# Verifica la propiedad de red de RutaTur:
#   - desde una red Docker ajena, los servicios funcionales NO son alcanzables por nombre;
#   - el gateway SÍ es alcanzable desde esa red a través del puerto publicado en el host;
#   - desde el loopback del host, los puertos 8081/8082/8083 responden;
#   - desde la IP LAN del host, esos mismos puertos NO responden.
#
# Requiere el stack levantado (`docker compose up -d --wait`). Sale con código 0 si todo pasa.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PROBE_IMAGE="${PROBE_IMAGE:-curlimages/curl:8.10.1}"
FOREIGN_NETWORK="rutatur-foreign-probe-$$"
FAILURES=0

log() { printf '%s\n' "$*"; }
pass() { printf '  OK   %s\n' "$*"; }
fail() { printf '  FAIL %s\n' "$*"; FAILURES=$((FAILURES + 1)); }

cleanup() {
  docker network rm "$FOREIGN_NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

require() {
  command -v "$1" >/dev/null 2>&1 || {
    log "ERROR: falta el comando '$1'."
    exit 2
  }
}

require docker
require curl

log "== RutaTur: verificación de aislamiento de red =="

if ! docker compose ps --status running --services 2>/dev/null | grep -qx 'api-gateway'; then
  log "ERROR: el stack no está corriendo. Ejecutá: docker compose up -d --wait"
  exit 2
fi

log "Creando red ajena $FOREIGN_NETWORK ..."
docker network create "$FOREIGN_NETWORK" >/dev/null

# curl dentro de la red ajena. api-gateway se resuelve al host para probar el puerto publicado.
probe() {
  docker run --rm \
    --network "$FOREIGN_NETWORK" \
    --add-host api-gateway:host-gateway \
    "$PROBE_IMAGE" -sS -m 8 -o /dev/null "$1" >/dev/null 2>&1
}

expect_reachable_from_foreign() {
  if probe "$1"; then pass "$1 alcanzable desde la red ajena"; else fail "$1 debería ser alcanzable desde la red ajena"; fi
}

expect_unreachable_from_foreign() {
  if probe "$1"; then fail "$1 NO debería ser alcanzable desde la red ajena"; else pass "$1 inalcanzable desde la red ajena"; fi
}

log "-- Desde una red Docker ajena --"
expect_unreachable_from_foreign "http://poi-service:8081/actuator/health"
expect_unreachable_from_foreign "http://itinerary-service:8082/actuator/health"
expect_unreachable_from_foreign "http://route-match-service:8083/actuator/health"
expect_reachable_from_foreign "http://api-gateway:8080/actuator/health"

log "-- Desde el loopback del host --"
for port in 8081 8082 8083; do
  if curl -fsS -m 8 -o /dev/null "http://127.0.0.1:${port}/actuator/health"; then
    pass "127.0.0.1:${port} responde"
  else
    fail "127.0.0.1:${port} debería responder"
  fi
done

log "-- Desde la IP LAN del host --"
HOST_IP=""
if command -v ipconfig >/dev/null 2>&1; then
  HOST_IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
elif command -v hostname >/dev/null 2>&1; then
  HOST_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
fi

if [[ -z "$HOST_IP" || "$HOST_IP" == "127.0.0.1" ]]; then
  log "  SKIP no se pudo determinar una IP LAN; no se prueba el acceso externo al host"
else
  for port in 8081 8082 8083; do
    if curl -fsS -m 5 -o /dev/null "http://${HOST_IP}:${port}/actuator/health" 2>/dev/null; then
      fail "${HOST_IP}:${port} NO debería responder (los puertos de servicio deben estar en 127.0.0.1)"
    else
      pass "${HOST_IP}:${port} no responde"
    fi
  done
fi

log ""
if [[ "$FAILURES" -gt 0 ]]; then
  log "Resultado: $FAILURES verificación(es) fallaron."
  exit 1
fi
log "Resultado: aislamiento de red correcto."
