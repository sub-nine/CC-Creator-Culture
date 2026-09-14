#!/bin/sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.."

docker compose --env-file .env -f compose.yaml -f compose.apps.yaml down
docker volume rm cc-service-apps_postgres-data
"$SCRIPT_DIR/up.sh"
