#!/bin/sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.."

docker exec -i cc-service-apps-postgres-1 psql -U user_db -d user_db < load-test/seed-master.sql
