#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AWS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DB_DIR="$AWS_DIR/database"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/fixtures.env"
POSTGRES_IMAGE="docker.io/library/postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73"
TEST_ID="cc-aws-seed-$$"
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
  sh -n "$SCRIPT_DIR/scripts/seed.sh"
  [[ -f "$SCRIPT_DIR/fixtures.env" ]] || fail "fixtures.env missing"
  [[ -n "${FIXTURE_PASSWORD_HASH:-}" ]] || fail "FIXTURE_PASSWORD_HASH missing from fixtures.env"
  if ! printf '%s\n' "$FIXTURE_PASSWORD_HASH" | grep -Eq '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'; then
    fail "FIXTURE_PASSWORD_HASH must be a BCrypt hash for local tests"
  fi
  for service in "${SERVICES[@]}"; do
    [[ -f "$SCRIPT_DIR/sql/$service.sql" ]] || fail "missing $service seed"
  done
  if grep -RE "TRUNCATE|DROP TABLE|DELETE FROM" "$SCRIPT_DIR/scripts" "$SCRIPT_DIR/sql"; then
    fail "seed must not truncate or delete rows"
  fi
  if grep -RE "hooks.slack.com|api.tosspayments.com|api.iamport" "$SCRIPT_DIR/scripts" "$SCRIPT_DIR/sql"; then
    fail "seed must not call external payment or Slack"
  fi
  echo "seed static checks passed"
}

cleanup() {
  if [[ "${KEEP_TEST_ROOT:-false}" == "true" ]]; then
    echo "Seed test resources retained with prefix: $TEST_ID" >&2
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

run_seed() {
  local service="$1"
  local run_id="$2"
  docker run --rm --network "$TEST_ID-net" \
    -e SERVICE="$service" \
    -e SEED_RUN_ID="$run_id" \
    -e DB_HOST=postgres \
    -e DB_NAME="${service}_db" \
    -e DB_USER="${service}_app" \
    -e DB_PASSWORD="$service-app" \
    -e PGSSLMODE=disable \
    -e SEED_PASSWORD_HASH="$FIXTURE_PASSWORD_HASH" \
    "$SEED_IMAGE"
}

integration_checks() {

  if [[ "$SKIP_IMAGE_BUILD" == "true" ]]; then
    ensure_image "$MIGRATE_IMAGE"
    ensure_image "$SEED_IMAGE"
  else
    docker build -f "$DB_DIR/Dockerfile" --build-arg RELEASE_SHA=test -t "$MIGRATE_IMAGE" "$AWS_DIR" >/dev/null
    docker build -f "$SCRIPT_DIR/Dockerfile" -t "$SEED_IMAGE" "$AWS_DIR" >/dev/null
  fi


  if docker run --rm --entrypoint /scripts/seed.sh \
    -e SERVICE=user \
    -e SEED_PASSWORD_HASH="$FIXTURE_PASSWORD_HASH" \
    "$SEED_IMAGE" 2>/dev/null; then
    fail "seed without SEED_RUN_ID must fail"
  fi
  if docker run --rm --entrypoint /scripts/seed.sh \
    -e SERVICE=user \
    -e SEED_RUN_ID=demo-1 \
    -e DB_HOST=postgres \
    -e DB_NAME=user_db \
    -e DB_USER=user_app \
    -e DB_PASSWORD=x \
    "$SEED_IMAGE" 2>/dev/null; then
    fail "seed without SEED_PASSWORD_HASH must fail"
  fi

  docker network create "$TEST_ID-net" >/dev/null
  docker run -d --name "$TEST_ID-postgres" --network "$TEST_ID-net" --network-alias postgres \
    -e POSTGRES_PASSWORD=admin-pass \
    "$POSTGRES_IMAGE" >/dev/null
  wait_postgres

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
      "$MIGRATE_IMAGE"
  done

  for service in "${SERVICES[@]}"; do
    run_seed "$service" demo-1
  done
  for service in "${SERVICES[@]}"; do
    run_seed "$service" demo-1
    run_seed "$service" demo-2
  done

  [[ "$(psql_admin user_db "SELECT count(*) FROM private.p_users")" == "4" ]] || fail "expected 4 fixture users"
  [[ "$(psql_admin user_db "SELECT role FROM private.p_users WHERE email = 'creator@aws-fixture.example'")" == "CREATOR" ]] \
    || fail "creator fixture missing"
  [[ "$(psql_admin user_db "SELECT approval_status FROM private.p_creators")" == "APPROVED" ]] \
    || fail "creator is not approved"
  [[ "$(psql_admin product_db "SELECT creator_id::text FROM p_products")" == "01a096d8-433d-72f6-9a16-71a86765d205" ]] \
    || fail "product creator_id must be the creator user id"
  [[ "$(psql_admin product_db "SELECT quantity FROM p_stocks")" == "50" ]] || fail "stock fixture missing"
  [[ "$(psql_admin order_db "SELECT count(*) FROM p_coupons")" == "1" ]] || fail "coupon fixture missing"
  [[ "$(psql_admin order_db "SELECT created_by::text FROM p_coupons")" == "01a096d8-433d-72f6-9a16-71a6b4f4a94b" ]] \
    || fail "coupon created_by must be master user id"
  [[ "$(psql_admin user_db "SELECT count(*) FROM ops.seed_runs")" == "2" ]] || fail "seed run ids were not recorded"
  echo "seed PostgreSQL 17 integration passed"
}

static_checks
if docker_available; then
  trap cleanup EXIT
  integration_checks
else
  if [[ "$REQUIRE_DOCKER" == "true" ]]; then
    fail "Docker is required for PostgreSQL 17 seed checks"
  fi
  echo "UNVERIFIED: Docker is not available for PostgreSQL 17 seed checks"
fi
