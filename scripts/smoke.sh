#!/usr/bin/env bash
#
# Smoke test end-to-end de RutaTur sobre el stack de Docker Compose.
#
#   docker compose up -d --wait
#   bash scripts/smoke.sh
#
# Cubre: login en Keycloak, roles en el token, gateway (token válido vs. token con clave
# desconocida), generación anónima, guardado, compartición opt-in, sugerencia >= 0.35,
# solicitud de coincidencia, consentimiento mutuo, 403 antes del consentimiento, 200 después
# y una única fila de auditoría en match_schema.user_contact_view.
#
# Requiere: docker compose corriendo, curl, openssl y python3.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${RUTATUR_BASE_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_INTERNAL_URL="${KEYCLOAK_INTERNAL_URL:-http://keycloak:8080}"
REALM="${RUTATUR_REALM:-rutatur-realm}"
TEST_PASSWORD="${RUTATUR_TEST_PASSWORD:-password}"
TOKEN_ENDPOINT="${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token"

TMP_DIR="$(mktemp -d)"
FAILURES=0

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

log()  { printf '%s\n' "$*"; }
pass() { printf '  OK   %s\n' "$*"; }
fail() { printf '  FAIL %s\n' "$*"; FAILURES=$((FAILURES + 1)); }

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require() {
  command -v "$1" >/dev/null 2>&1 || die "falta el comando '$1'."
}

require curl
require openssl
require python3
require docker

# json_get <archivo> <ruta.con.puntos>; los segmentos numéricos indexan listas.
json_get() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    value = json.load(handle)
for part in sys.argv[2].split("."):
    value = value[int(part)] if part.isdigit() else value[part]
print(value)
PY
}

json_len() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    print(len(json.load(handle)))
PY
}

json_has_route() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    data = json.load(handle)
sys.exit(0 if data.get("route") else 1)
PY
}

json_score_at_least() {
  python3 - "$1" "$2" <<'PY'
import sys

score = float(sys.argv[1])
sys.exit(0 if score >= float(sys.argv[2]) else 1)
PY
}

suggestion_score_for() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    data = json.load(handle)
for item in data:
    if item.get("targetRouteId") == sys.argv[2]:
        print(item["matchScore"])
        sys.exit(0)
sys.exit(1)
PY
}

decode_segment() {
  local segment="$1"
  case $(( ${#segment} % 4 )) in
    2) segment="${segment}==" ;;
    3) segment="${segment}=" ;;
  esac
  printf '%s' "$segment" | tr '_-' '/+' | openssl base64 -d -A
}

b64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

request() {
  local method="$1" path="$2" token="$3" data="$4" out="$5"
  local args=(-sS -m 60 -X "$method" -o "$out" -w '%{http_code}')
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "$data" ]]; then
    args+=(-H 'Content-Type: application/json' --data "$data")
  fi
  curl "${args[@]}" "${BASE_URL}${path}"
}

expect_status() {
  local expected="$1" actual="$2" label="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "${label} (HTTP ${actual})"
  else
    fail "${label}: esperado HTTP ${expected}, obtenido ${actual}"
  fi
}

expect_status_in() {
  local label="$1" actual="$2"
  shift 2
  local expected
  for expected in "$@"; do
    if [[ "$actual" == "$expected" ]]; then
      pass "${label} (HTTP ${actual})"
      return
    fi
  done
  fail "${label}: esperado uno de [$*], obtenido ${actual}"
}

require_status() {
  local expected="$1" actual="$2" label="$3" body="$4"
  if [[ "$actual" != "$expected" ]]; then
    fail "${label}: esperado HTTP ${expected}, obtenido ${actual}"
    printf '  respuesta: %s\n' "$(head -c 600 "$body")"
    exit 1
  fi
  pass "${label} (HTTP ${actual})"
}

login() {
  local username="$1"
  local body="${TMP_DIR}/login-${username}.json"
  local status
  status="$(curl -sS -m 30 -o "$body" -w '%{http_code}' -X POST "$TOKEN_ENDPOINT" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=rutatur-api' \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${TEST_PASSWORD}")"
  if [[ "$status" != "200" ]]; then
    printf '  respuesta: %s\n' "$(head -c 600 "$body")" >&2
    die "login de '${username}' falló con HTTP ${status}."
  fi
  json_get "$body" access_token
}

token_payload_file() {
  local token="$1" out="$2"
  decode_segment "$(printf '%s' "$token" | cut -d. -f2)" > "$out"
}

assert_role() {
  local label="$1" payload_file="$2" role="$3"
  local roles
  roles="$(json_get "$payload_file" realm_access.roles)"
  if printf '%s' "$roles" | grep -q "'${role}'"; then
    pass "${label} incluye ${role}"
  else
    fail "${label} NO incluye ${role}"
  fi
}

log "== RutaTur: smoke test end-to-end =="

# ---------------------------------------------------------------- preflight
log "-- Preflight --"
if ! curl -fsS -m 10 -o /dev/null "${BASE_URL}/actuator/health"; then
  die "el gateway no responde en ${BASE_URL}/actuator/health. Ejecutá: docker compose up -d --wait"
fi
pass "gateway saludable en ${BASE_URL}"
if ! curl -fsS -m 10 -o /dev/null "${KEYCLOAK_URL}/realms/${REALM}"; then
  die "Keycloak no expone el realm ${REALM} en ${KEYCLOAK_URL}. Revisá la importación del realm."
fi
pass "realm ${REALM} disponible en ${KEYCLOAK_URL}"

# ---------------------------------------------------------------- identidad
log "-- Identidad y roles --"
USER1_TOKEN="$(login user1)"
ADMIN1_TOKEN="$(login admin1)"
pass "login de user1 y admin1"

token_payload_file "$USER1_TOKEN" "${TMP_DIR}/user1-payload.json"
token_payload_file "$ADMIN1_TOKEN" "${TMP_DIR}/admin1-payload.json"
assert_role "user1" "${TMP_DIR}/user1-payload.json" ROLE_USER
assert_role "admin1" "${TMP_DIR}/admin1-payload.json" ROLE_USER
assert_role "admin1" "${TMP_DIR}/admin1-payload.json" ROLE_ADMIN

USER1_SUB="$(json_get "${TMP_DIR}/user1-payload.json" sub)"
ADMIN1_SUB="$(json_get "${TMP_DIR}/admin1-payload.json" sub)"

# ---------------------------------------------------------------- gateway
log "-- Gateway: tokens válidos e inválidos --"
status="$(request GET /api/itineraries "$USER1_TOKEN" "" "${TMP_DIR}/gw-user1.json")"
expect_status 200 "$status" "gateway acepta el token de user1"

status="$(request GET /api/itineraries "$ADMIN1_TOKEN" "" "${TMP_DIR}/gw-admin1.json")"
expect_status 200 "$status" "gateway acepta el token de admin1"

status="$(request GET "/api/admin/itineraries?userId=${USER1_SUB}" "$ADMIN1_TOKEN" "" "${TMP_DIR}/gw-admin-surface.json")"
expect_status 200 "$status" "gateway acepta ROLE_ADMIN en /api/admin/**"

FORGED_HEADER="$(printf '%s' '{"alg":"RS256","typ":"JWT","kid":"unknown-key"}' | b64url)"
FORGED_PAYLOAD="$(printf '{"iss":"%s/realms/%s","sub":"forged","exp":%s}' \
  "$KEYCLOAK_INTERNAL_URL" "$REALM" "$(( $(date +%s) + 300 ))" | b64url)"
FORGED_TOKEN="${FORGED_HEADER}.${FORGED_PAYLOAD}.Zm9yZ2Vk"
status="$(request GET /api/itineraries "$FORGED_TOKEN" "" "${TMP_DIR}/gw-forged.json")"
expect_status 401 "$status" "gateway rechaza un token con clave desconocida"

# ---------------------------------------------------------------- generación
log "-- Generación anónima y guardado --"
GENERATE_BODY='{"startPoint":{"latitude":-34.6037,"longitude":-58.3816},"availableTime":240,"preferences":["MUSEUM","PARK","CAFE"],"radius":1500,"transportMode":"WALKING","maxPois":4}'

status="$(request POST /api/itinerary/generate "" "$GENERATE_BODY" "${TMP_DIR}/gen-user1.json")"
require_status 200 "$status" "generación anónima para user1" "${TMP_DIR}/gen-user1.json"
ITINERARY1="$(json_get "${TMP_DIR}/gen-user1.json" itineraryId)"
if ! json_has_route "${TMP_DIR}/gen-user1.json"; then
  die "el itinerario generado no tiene paradas; revisá Overpass/OSRM."
fi
pass "itinerario de user1: ${ITINERARY1}"

status="$(request POST /api/itineraries "$USER1_TOKEN" \
  "{\"itineraryId\":\"${ITINERARY1}\"}" "${TMP_DIR}/save-user1.json")"
require_status 201 "$status" "guardado de user1" "${TMP_DIR}/save-user1.json"
SAVED1="$(json_get "${TMP_DIR}/save-user1.json" id)"

status="$(request POST /api/routes/share "$USER1_TOKEN" \
  "{\"itineraryId\":\"${SAVED1}\"}" "${TMP_DIR}/share-user1.json")"
expect_status_in "compartición opt-in de user1" "$status" 200 201

status="$(request POST /api/itinerary/generate "" "$GENERATE_BODY" "${TMP_DIR}/gen-admin1.json")"
require_status 200 "$status" "generación anónima para admin1" "${TMP_DIR}/gen-admin1.json"
ITINERARY2="$(json_get "${TMP_DIR}/gen-admin1.json" itineraryId)"

status="$(request POST /api/itineraries "$ADMIN1_TOKEN" \
  "{\"itineraryId\":\"${ITINERARY2}\"}" "${TMP_DIR}/save-admin1.json")"
require_status 201 "$status" "guardado de admin1" "${TMP_DIR}/save-admin1.json"
SAVED2="$(json_get "${TMP_DIR}/save-admin1.json" id)"

status="$(request POST /api/routes/share "$ADMIN1_TOKEN" \
  "{\"itineraryId\":\"${SAVED2}\"}" "${TMP_DIR}/share-admin1.json")"
expect_status_in "compartición opt-in de admin1" "$status" 200 201
SHARE2="$(json_get "${TMP_DIR}/share-admin1.json" id)"

# ---------------------------------------------------------------- sugerencia
log "-- Sugerencia de coincidencia --"
status="$(request GET /api/routes/matches/suggestions "$USER1_TOKEN" "" "${TMP_DIR}/suggestions.json")"
require_status 200 "$status" "consulta de sugerencias de user1" "${TMP_DIR}/suggestions.json"
[[ "$(json_len "${TMP_DIR}/suggestions.json")" -ge 1 ]] || die "no hay sugerencias para user1."
SUGGESTION_SCORE="$(suggestion_score_for "${TMP_DIR}/suggestions.json" "$SHARE2")" \
  || die "la sugerencia no incluye la ruta compartida por admin1 (${SHARE2})."
if ! json_score_at_least "$SUGGESTION_SCORE" 0.35; then
  die "la sugerencia para ${SHARE2} tiene matchScore ${SUGGESTION_SCORE} < 0.35."
fi
pass "sugerencia con matchScore ${SUGGESTION_SCORE} >= 0.35"
TARGET_ROUTE="$SHARE2"

# ---------------------------------------------------------------- coincidencia
log "-- Solicitud y consentimiento mutuo --"
status="$(request POST /api/routes/matches/request "$USER1_TOKEN" \
  "{\"targetRouteId\":\"${TARGET_ROUTE}\"}" "${TMP_DIR}/match-request.json")"
expect_status_in "solicitud de coincidencia de user1" "$status" 200 201
MATCH_ID="$(json_get "${TMP_DIR}/match-request.json" id)"
[[ "$(json_get "${TMP_DIR}/match-request.json" status)" == "PENDING" ]] \
  && pass "la coincidencia queda PENDING" || fail "la coincidencia no quedó PENDING"

status="$(request POST "/api/routes/matches/${MATCH_ID}/accept" "$ADMIN1_TOKEN" "" "${TMP_DIR}/accept-responder.json")"
expect_status 200 "$status" "admin1 (responder) acepta"
[[ "$(json_get "${TMP_DIR}/accept-responder.json" status)" == "PENDING" ]] \
  && pass "con un solo consentimiento sigue PENDING" || fail "no debería estar ACCEPTED todavía"

status="$(request GET "/api/routes/matches/${MATCH_ID}/contact" "$USER1_TOKEN" "" "${TMP_DIR}/contact-before.json")"
expect_status 403 "$status" "contacto oculto antes del consentimiento mutuo"

status="$(request POST "/api/routes/matches/${MATCH_ID}/accept" "$USER1_TOKEN" "" "${TMP_DIR}/accept-requester.json")"
expect_status 200 "$status" "user1 (requester) acepta"
[[ "$(json_get "${TMP_DIR}/accept-requester.json" status)" == "ACCEPTED" ]] \
  && pass "con ambos consentimientos queda ACCEPTED" || fail "debería quedar ACCEPTED"

status="$(request GET "/api/routes/matches/${MATCH_ID}/contact" "$USER1_TOKEN" "" "${TMP_DIR}/contact-after.json")"
expect_status 200 "$status" "contacto revelado tras el consentimiento mutuo"
REVEALED_USER="$(json_get "${TMP_DIR}/contact-after.json" userId)"
[[ "$REVEALED_USER" == "$ADMIN1_SUB" ]] \
  && pass "el contacto revelado es el de admin1" \
  || fail "contacto revelado ${REVEALED_USER}, se esperaba ${ADMIN1_SUB}"

log "-- Auditoría de acceso al contacto --"
AUDIT_COUNT="$(docker compose exec -T postgres psql -U rutatur -d rutatur_db -tAc \
  "SELECT count(*) FROM match_schema.user_contact_view WHERE route_match_id = '${MATCH_ID}'" \
  2>/dev/null | tr -d '[:space:]' || true)"
if [[ "$AUDIT_COUNT" == "1" ]]; then
  pass "una única fila de auditoría para la coincidencia"
else
  fail "filas de auditoría esperadas 1, encontradas ${AUDIT_COUNT:-0}"
fi

log ""
if [[ "$FAILURES" -gt 0 ]]; then
  log "Resultado: ${FAILURES} verificación(es) fallaron."
  exit 1
fi
log "Resultado: smoke test OK."
