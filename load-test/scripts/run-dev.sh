#!/bin/sh
# Usage: ENV_FILE=/path/current.env BASE_URL=https://dev.example sh run-dev.sh scenarios/...
set -eu
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.."
SCENARIO="${1:-}"
case "$SCENARIO" in
  scenarios/*.js) ;;
  *) echo "Usage: $0 scenarios/<name>.js [k6 arguments...]" >&2; exit 2 ;;
esac
case "$SCENARIO" in *..*|*//*) echo "Invalid scenario path" >&2; exit 2 ;; esac
[ -f "load-test/$SCENARIO" ] || { echo "Scenario not found: $SCENARIO" >&2; exit 2; }
shift
export TARGET="${TARGET:-dev}"
case "$TARGET" in local|dev|prod) ;; *) echo "Invalid TARGET" >&2; exit 2 ;; esac
case "$SCENARIO" in
  scenarios/smoke.js) : "${SMOKE_URL:?SMOKE_URL is required}" ;;
  scenarios/embedding-smoke.js) : "${EMBED_URL:?EMBED_URL is required}" ;;
  *)
    if [ "$TARGET" != local ] && [ -z "${BASE_URL:-}" ]; then
      echo "BASE_URL is required for dev/prod" >&2; exit 2
    fi
    ;;
esac
export TESTID="${TESTID:-$(date +%s)}"
echo "TESTID=$TESTID"
if [ -n "${ENV_FILE:-}" ] && [ ! -f "$ENV_FILE" ]; then
  echo "ENV_FILE does not exist: $ENV_FILE" >&2; exit 2
fi
compose() {
  if [ -n "${ENV_FILE:-}" ]; then
    docker compose --env-file "$ENV_FILE" -f load-test/compose.dev.yml "$@"
  else
    docker compose -f load-test/compose.dev.yml "$@"
  fi
}
compose run --rm k6 run --out experimental-prometheus-rw --tag "testid=$TESTID" "$SCENARIO" "$@"
