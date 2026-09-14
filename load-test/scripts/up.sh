#!/bin/sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.."

docker compose --env-file .env -f compose.yaml -f compose.apps.yaml up -d --build --wait --wait-timeout 240
"$SCRIPT_DIR/seed-master.sh"
