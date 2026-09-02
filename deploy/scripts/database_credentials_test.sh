#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POSTGRES_IMAGE="docker.io/library/postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73"
TEST_ID="cc-db-isolation-$$"
SERVICES=(user product order)

container_name() {
  printf '%s-%s-postgres' "$TEST_ID" "$1"
}

network_name() {
  printf '%s-%s-data' "$TEST_ID" "$1"
}

volume_name() {
  printf '%s-%s-postgres-data' "$TEST_ID" "$1"
}

cleanup() {
  if [[ "${KEEP_TEST_ROOT:-false}" == "true" ]]; then
    echo "Database test resources retained with prefix: $TEST_ID" >&2
    return
  fi

  local service
  for service in "${SERVICES[@]}"; do
    docker rm -f "$(container_name "$service")" >/dev/null 2>&1 || true
    docker volume rm "$(volume_name "$service")" >/dev/null 2>&1 || true
    docker network rm "$(network_name "$service")" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

start_postgres() {
  local service="$1"
  local suffix="$2"
  local container network volume database username alias
  container="$(container_name "$service")"
  network="$(network_name "$service")"
  volume="$(volume_name "$service")"
  database="${service}_db"
  username="${service}_app"
  alias="${service}-postgres"

  docker network inspect "$network" >/dev/null 2>&1 || docker network create "$network" >/dev/null
  docker run -d \
    --name "$container" \
    --network "$network" \
    --network-alias "$alias" \
    -e "POSTGRES_DB=$database" \
    -e POSTGRES_USER=postgres \
    -e "POSTGRES_PASSWORD=${service}-admin-${suffix}" \
    -e "SERVICE_DB_NAME=$database" \
    -e "SERVICE_DB_USERNAME=$username" \
    -e "SERVICE_DB_PASSWORD=${service}-app-${suffix}" \
    -v "$volume:/var/lib/postgresql/data" \
    -v "$REPOSITORY_ROOT/deploy/postgres/init-service-database.sh:/docker-entrypoint-initdb.d/10-init-service-database.sh:ro" \
    "$POSTGRES_IMAGE" \
    -c max_connections=30 >/dev/null

  for _ in $(seq 1 30); do
    if docker exec "$container" pg_isready --username postgres --dbname "$database" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "$service PostgreSQL test container did not become ready." >&2
  return 1
}

assert_login() {
  local service="$1"
  local password="$2"
  local username="$3"
  local database="${service}_db"
  docker exec -e "PGPASSWORD=$password" "$(container_name "$service")" \
    psql --host "${service}-postgres" --username "$username" --dbname "$database" \
      --tuples-only --no-align --command 'SELECT 1' \
    | grep -Fqx 1
}

assert_ownership_and_isolation() {
  local service="$1"
  local container database username
  container="$(container_name "$service")"
  database="${service}_db"
  username="${service}_app"

  docker exec -e "PGPASSWORD=${service}-app-new" "$container" \
    psql --host "${service}-postgres" --username "$username" --dbname "$database" \
      --tuples-only --no-align \
      --command "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = current_database()" \
    | grep -Fqx "$username"

  docker exec "$container" psql --username postgres --dbname "$database" \
    --command "CREATE ROLE isolation_probe LOGIN PASSWORD 'probe-password'" >/dev/null
  if docker exec -e PGPASSWORD=probe-password "$container" \
    psql --host "${service}-postgres" --username isolation_probe --dbname "$database" \
      --command 'SELECT 1' >/dev/null 2>&1; then
    echo "PUBLIC retained CONNECT permission on $database." >&2
    return 1
  fi
  docker exec "$container" psql --username postgres --dbname "$database" \
    --command 'DROP ROLE isolation_probe' >/dev/null
}

for service in "${SERVICES[@]}"; do
  start_postgres "$service" old
  assert_login "$service" "${service}-app-old" "${service}_app"
done

for service in "${SERVICES[@]}"; do
  docker rm -f "$(container_name "$service")" >/dev/null
  start_postgres "$service" new
  docker exec -i "$(container_name "$service")" sh -euc \
    'psql --username "$POSTGRES_USER" --dbname "$SERVICE_DB_NAME"' \
    < "$REPOSITORY_ROOT/deploy/postgres/reconcile-credentials.sql" >/dev/null

  assert_login "$service" "${service}-admin-new" postgres
  assert_login "$service" "${service}-app-new" "${service}_app"
  assert_ownership_and_isolation "$service"

  if assert_login "$service" "${service}-admin-old" postgres >/dev/null 2>&1 \
    || assert_login "$service" "${service}-app-old" "${service}_app" >/dev/null 2>&1; then
    echo "Previous $service database password remained valid after reconciliation." >&2
    exit 1
  fi
done

if docker exec -e PGPASSWORD=user-app-new "$(container_name user)" \
  psql --host product-postgres --username user_app --dbname product_db \
    --command 'SELECT 1' >/dev/null 2>&1; then
  echo "User database credentials crossed the product data network." >&2
  exit 1
fi

echo "isolated database credential rotation integration test passed."
