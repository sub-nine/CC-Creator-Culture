#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AWS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
POSTGRES_IMAGE="docker.io/library/postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73"
TEST_ID="cc-aws-db-$$"
PROVIDED_MIGRATE="${MIGRATE_IMAGE:-}"
PROVIDED_SEED="${SEED_IMAGE:-}"
MIGRATE_IMAGE="${PROVIDED_MIGRATE:-cc-db-migrate:$TEST_ID}"
SEED_IMAGE="${PROVIDED_SEED:-cc-db-seed:$TEST_ID}"
SKIP_IMAGE_BUILD=false
PULL_DIGEST_IMAGES=false
REQUIRE_DOCKER="${REQUIRE_DOCKER:-false}"
SERVICES=(user product order)

fail() {
  echo "$*" >&2
  exit 1
}

is_digest_image() {
  [[ "$1" =~ @sha256:[0-9a-f]{64}$ ]]
}

if [[ -n "$PROVIDED_MIGRATE" || -n "$PROVIDED_SEED" ]]; then
  [[ -n "$PROVIDED_MIGRATE" && -n "$PROVIDED_SEED" ]] || fail "MIGRATE_IMAGE and SEED_IMAGE must be set together"
  SKIP_IMAGE_BUILD=true
  if is_digest_image "$MIGRATE_IMAGE" && is_digest_image "$SEED_IMAGE"; then
    PULL_DIGEST_IMAGES=true
  elif is_digest_image "$MIGRATE_IMAGE" || is_digest_image "$SEED_IMAGE"; then
    fail "MIGRATE_IMAGE and SEED_IMAGE must both be digest refs or both be local images"
  fi
fi

ensure_image() {
  local image="$1"
  if docker image inspect "$image" >/dev/null 2>&1; then
    return
  fi
  if [[ "$PULL_DIGEST_IMAGES" == "true" ]]; then
    docker pull "$image" >/dev/null
    return
  fi
  fail "required image is missing: $image"
}

static_checks() {
  sh -n "$SCRIPT_DIR/scripts/migrate.sh"
  sh -n "$SCRIPT_DIR/scripts/bootstrap-roles.sh"
  for service in "${SERVICES[@]}"; do
    [[ -f "$SCRIPT_DIR/migrations/$service/V1__${service}_schema.sql" ]] || fail "missing $service migration"
  done
  if grep -RE "TRUNCATE|DROP TABLE|CREATE EXTENSION[[:space:]]+vector" "$SCRIPT_DIR/scripts" "$SCRIPT_DIR/migrations"; then
    fail "migrations must not truncate, drop tables, or create vector"
  fi
  echo "database static checks passed"
}

cleanup() {
  if [[ "${KEEP_TEST_ROOT:-false}" == "true" ]]; then
    echo "Database test resources retained with prefix: $TEST_ID" >&2
    return
  fi
  docker rm -f "$TEST_ID-postgres" >/dev/null 2>&1 || true
  docker network rm "$TEST_ID-net" >/dev/null 2>&1 || true
  if [[ "$SKIP_IMAGE_BUILD" != "true" ]]; then
    docker rmi "$MIGRATE_IMAGE" "$SEED_IMAGE" >/dev/null 2>&1 || true
  fi
}

docker_available() {
  docker info >/dev/null 2>&1
}

psql_admin() {
  docker exec -e PGPASSWORD=admin-pass "$TEST_ID-postgres" \
    psql --host 127.0.0.1 --username postgres --dbname "$1" --tuples-only --no-align --command "$2"
}

wait_postgres() {
  local i
  for i in $(seq 1 30); do
    if docker exec "$TEST_ID-postgres" pg_isready --host 127.0.0.1 --username postgres >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail "PostgreSQL 17 test container did not become ready"
}

integration_checks() {
  docker network create "$TEST_ID-net" >/dev/null
  docker run -d --name "$TEST_ID-postgres" --network "$TEST_ID-net" --network-alias postgres \
    -e POSTGRES_PASSWORD=admin-pass \
    "$POSTGRES_IMAGE" >/dev/null
  wait_postgres


  if [[ "$SKIP_IMAGE_BUILD" == "true" ]]; then
    ensure_image "$MIGRATE_IMAGE"
    ensure_image "$SEED_IMAGE"
  else
    docker build -f "$SCRIPT_DIR/Dockerfile" --build-arg RELEASE_SHA=test -t "$MIGRATE_IMAGE" "$AWS_DIR" >/dev/null
    docker build -f "$AWS_DIR/seed/Dockerfile" -t "$SEED_IMAGE" "$AWS_DIR" >/dev/null
  fi

  local service database username
  for service in "${SERVICES[@]}"; do
    database="${service}_db"
    username="${service}_app"
    docker exec -e PGPASSWORD=admin-pass "$TEST_ID-postgres" \
      psql --username postgres --dbname postgres --command "CREATE DATABASE ${database}" >/dev/null

    docker run --rm --network "$TEST_ID-net" --entrypoint /scripts/bootstrap-roles.sh \
      -e SERVICE="$service" \
      -e DB_HOST=postgres \
      -e DB_NAME="$database" \
      -e ADMIN_USER=postgres \
      -e ADMIN_PASSWORD=admin-pass \
      -e APP_USER="$username" \
      -e APP_PASSWORD="$service-app" \
      -e PGSSLMODE=disable \
      "$SEED_IMAGE"

    docker run --rm --network "$TEST_ID-net" \
      -e SERVICE="$service" \
      -e FLYWAY_URL="jdbc:postgresql://postgres:5432/${database}" \
      -e FLYWAY_USER="$username" \
      -e FLYWAY_PASSWORD="$service-app" \
      -e RELEASE_SHA=test \
      "$MIGRATE_IMAGE"

    docker run --rm --network "$TEST_ID-net" \
      -e SERVICE="$service" \
      -e FLYWAY_URL="jdbc:postgresql://postgres:5432/${database}" \
      -e FLYWAY_USER="$username" \
      -e FLYWAY_PASSWORD="$service-app" \
      -e RELEASE_SHA=test \
      "$MIGRATE_IMAGE"
  done

  [[ "$(psql_admin user_db "SELECT nspname FROM pg_namespace WHERE nspname = 'private'")" == "private" ]] \
    || fail "user private schema missing"
  [[ "$(psql_admin user_db "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'private' AND table_name = 'p_users'")" == "1" ]] \
    || fail "private.p_users missing"
  [[ "$(psql_admin user_db "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'p_users'")" == "0" ]] \
    || fail "p_users leaked to public"
  [[ "$(psql_admin user_db "SELECT data_type FROM information_schema.columns WHERE table_schema = 'private' AND table_name = 'p_users' AND column_name = 'created_at'")" == "timestamp with time zone" ]] \
    || fail "p_users.created_at is not timestamptz"
  [[ "$(psql_admin product_db "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'p_products'")" == "1" ]] \
    || fail "p_products missing"
  [[ "$(psql_admin order_db "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'p_order_command_requests' AND column_name = 'response_payload'")" == "jsonb" ]] \
    || fail "response_payload is not jsonb"
  [[ "$(psql_admin user_db "SELECT count(*) FROM pg_extension WHERE extname = 'vector'")" == "0" ]] \
    || fail "vector extension should not be required"
  [[ "$(psql_admin user_db "SELECT count(*) FROM private.flyway_schema_history")" == "1" ]] \
    || fail "user flyway history missing"
  echo "database PostgreSQL 17 integration passed"
}

static_checks
if docker_available; then
  trap cleanup EXIT
  integration_checks
else
  if [[ "$REQUIRE_DOCKER" == "true" ]]; then
    fail "Docker is required for PostgreSQL 17 migrate checks"
  fi
  echo "UNVERIFIED: Docker is not available for PostgreSQL 17 migrate checks"
fi
