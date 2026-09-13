#!/usr/bin/env bash
# Builds the db-seed image and applies it once to a throwaway PostgreSQL 17 container.
# The schema below is the minimum the seed SQL touches; the real schema is owned by Flyway in each service.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/fixtures.env"
POSTGRES_IMAGE="docker.io/library/postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73"
TEST_ID="cc-aws-seed-$$"
SEED_IMAGE="cc-db-seed:$TEST_ID"
REQUIRE_DOCKER="${REQUIRE_DOCKER:-false}"
RUN_ID="seed-test-$$"

fail() { echo "$*" >&2; exit 1; }

cleanup() {
  docker rm -f "$TEST_ID-postgres" >/dev/null 2>&1 || true
  docker network rm "$TEST_ID-net" >/dev/null 2>&1 || true
  docker rmi "$SEED_IMAGE" >/dev/null 2>&1 || true
}

psql_admin() {
  docker exec -i -e PGPASSWORD=admin-pass "$TEST_ID-postgres" \
    psql --username postgres --dbname seed_test --tuples-only --no-align --quiet --set=ON_ERROR_STOP=1 "$@"
}

sh -n "$SCRIPT_DIR/scripts/seed.sh"
if grep -RE "TRUNCATE|DROP TABLE|DELETE FROM" "$SCRIPT_DIR/scripts" "$SCRIPT_DIR/sql"; then
  fail "seed must not truncate or delete rows"
fi

if ! docker info >/dev/null 2>&1; then
  [[ "$REQUIRE_DOCKER" != "true" ]] || fail "Docker is required for the seed check"
  echo "UNVERIFIED: Docker is not available for the seed check"
  exit 0
fi
trap cleanup EXIT

docker build -f "$SCRIPT_DIR/Dockerfile" --build-arg RELEASE_SHA=test -t "$SEED_IMAGE" "$SCRIPT_DIR" >/dev/null
docker network create "$TEST_ID-net" >/dev/null
docker run -d --name "$TEST_ID-postgres" --network "$TEST_ID-net" --network-alias postgres \
  -e POSTGRES_PASSWORD=admin-pass -e POSTGRES_DB=seed_test "$POSTGRES_IMAGE" >/dev/null
# pg_isready is not enough: the image's entrypoint starts a socket-only temporary server before it creates
# POSTGRES_DB, so check over TCP against the target database itself.
ready=false
for _ in $(seq 1 30); do
  if docker exec -e PGPASSWORD=admin-pass "$TEST_ID-postgres" \
    psql --host 127.0.0.1 --username postgres --dbname seed_test --quiet --command "SELECT 1" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == "true" ]] || fail "PostgreSQL did not become ready with database seed_test within 30s"

psql_admin <<'SQL'
CREATE SCHEMA private;
CREATE TABLE private.p_users (id uuid PRIMARY KEY, email text, password text, nickname text, phone text, address text,
  slack_id text, role text, created_at timestamptz, created_by uuid, updated_at timestamptz, updated_by uuid);
CREATE TABLE private.p_creators (id uuid PRIMARY KEY, user_id uuid, creator_name text, business_registration_number text,
  approval_status text, approved_at timestamp, approved_by uuid, created_at timestamptz, created_by uuid, updated_at timestamptz, updated_by uuid);
CREATE TABLE p_categories (id uuid PRIMARY KEY, merged_category_id uuid, name text, description text, status text,
  unique_version uuid, created_at timestamptz, updated_at timestamptz, created_by uuid, updated_by uuid);
CREATE TABLE p_hashtags (id uuid PRIMARY KEY, name text, usage_count int, unique_version uuid, version bigint,
  created_at timestamptz, updated_at timestamptz, created_by uuid, updated_by uuid);
CREATE TABLE p_categories_hashtags (id uuid PRIMARY KEY, category_id uuid, hashtag_id uuid, match_type text, status text,
  similarity_score numeric, unique_version uuid, created_at timestamptz, updated_at timestamptz, created_by uuid, updated_by uuid);
CREATE TABLE p_products (id uuid PRIMARY KEY, creator_id uuid, name text, content text, view_count bigint, average_rating numeric,
  review_count int, status text, created_at timestamptz, updated_at timestamptz, created_by uuid, updated_by uuid);
CREATE TABLE p_skus (id uuid PRIMARY KEY, product_id uuid, name text, price bigint, is_default boolean,
  created_at timestamptz, updated_at timestamptz, created_by uuid, updated_by uuid);
CREATE TABLE p_stocks (id uuid PRIMARY KEY, sku_id uuid, quantity int, updated_at timestamptz);
CREATE TABLE p_hashtags_products (id uuid PRIMARY KEY, hashtag_id uuid, product_id uuid, unique_version uuid,
  created_at timestamptz, updated_at timestamptz, created_by uuid, updated_by uuid);
CREATE TABLE p_coupons (id uuid PRIMARY KEY, coupon_name text, discount_rate int, total_quantity int, issued_quantity int,
  started_at timestamptz, expired_at timestamptz, created_at timestamptz, created_by uuid, updated_at timestamptz);
SQL

for service in user product order; do
  docker run --rm --network "$TEST_ID-net" \
    -e SERVICE="$service" -e SEED_RUN_ID="$RUN_ID" -e PGSSLMODE=disable \
    -e DB_HOST=postgres -e DB_NAME=seed_test -e DB_USER=postgres -e DB_PASSWORD=admin-pass \
    -e SEED_PASSWORD_HASH="$FIXTURE_PASSWORD_HASH" "$SEED_IMAGE"
done

[[ "$(psql_admin --command "SELECT count(*) FROM ops.seed_runs WHERE run_id = '$RUN_ID'")" == "3" ]] \
  || fail "ops.seed_runs must record the run for user, product, and order"
[[ "$(psql_admin --command "SELECT count(*) FROM private.p_users")" == "4" ]] || fail "expected 4 fixture users"
echo "seed PostgreSQL 17 check passed"
