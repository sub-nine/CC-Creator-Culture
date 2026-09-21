#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/service-revisions.sh"
TEST_ROOT="$(mktemp -d)"
cleanup() { rm -rf "$TEST_ROOT"; }
trap cleanup EXIT

REPO="$TEST_ROOT/repo"
mkdir -p "$REPO"
git -C "$REPO" init -q
git -C "$REPO" checkout -q -b main
git -C "$REPO" config user.name test
git -C "$REPO" config user.email test@example.com

write_tree() {
  mkdir -p \
    "$REPO/apps/user-service" \
    "$REPO/apps/product-service" \
    "$REPO/apps/order-service" \
    "$REPO/infra/config-server" \
    "$REPO/infra/eureka-server" \
    "$REPO/infra/gateway" \
    "$REPO/libs/common" \
    "$REPO/gradle" \
    "$REPO/config-repo" \
    "$REPO/deploy/aws/seed" \
    "$REPO/apps/embedding-service" \
    "$REPO/load-test"
  printf 'root\n' > "$REPO/build.gradle"
  printf 'include\n' > "$REPO/settings.gradle"
  printf 'wrapper\n' > "$REPO/gradle/wrapper.properties"
  printf '#!/bin/sh\n' > "$REPO/gradlew"
  printf 'FROM scratch\n' > "$REPO/Dockerfile"
  printf 'ignore\n' > "$REPO/.dockerignore"
  printf 'user\n' > "$REPO/apps/user-service/src.txt"
  printf 'product\n' > "$REPO/apps/product-service/src.txt"
  printf 'order\n' > "$REPO/apps/order-service/src.txt"
  printf 'config\n' > "$REPO/infra/config-server/src.txt"
  printf 'eureka\n' > "$REPO/infra/eureka-server/src.txt"
  printf 'gateway\n' > "$REPO/infra/gateway/src.txt"
  printf 'common\n' > "$REPO/libs/common/src.txt"
  printf 'seed\n' > "$REPO/deploy/aws/seed/Dockerfile"
  printf 'dockerfile\n' > "$REPO/apps/embedding-service/Dockerfile"
  printf 'app\n' > "$REPO/apps/embedding-service/app.py"
  printf 'smoke\n' > "$REPO/apps/embedding-service/smoke.py"
  printf 'requirements\n' > "$REPO/apps/embedding-service/requirements.txt"
  printf 'k6\n' > "$REPO/load-test/Dockerfile.cloud"
  printf 'scenario\n' > "$REPO/load-test/scenario.js"
  printf 'app: {}\n' > "$REPO/config-repo/application.yaml"
  printf 'app-dev: {}\n' > "$REPO/config-repo/application-dev.yaml"
  printf 'user: {}\n' > "$REPO/config-repo/user-service.yaml"
  printf 'user-dev: {}\n' > "$REPO/config-repo/user-service-dev.yaml"
  printf 'product: {}\n' > "$REPO/config-repo/product-service.yaml"
  printf 'product-dev: {}\n' > "$REPO/config-repo/product-service-dev.yaml"
  printf 'order-dev: {}\n' > "$REPO/config-repo/order-service-dev.yaml"
  printf 'gateway-dev: {}\n' > "$REPO/config-repo/gateway-dev.yaml"
}

commit() {
  local message="$1"
  git -C "$REPO" add -A
  git -C "$REPO" commit -q -m "$message"
  git -C "$REPO" rev-parse HEAD
}

revisions_at() {
  local sha="$1"
  git -C "$REPO" rev-parse --show-toplevel >/dev/null
  (cd "$REPO" && bash "$SCRIPT" "$sha")
}

assert_changed() {
  local before="$1"
  local after="$2"
  local service="$3"
  local field="$4"
  jq -ne --argjson before "$before" --argjson after "$after" --arg service "$service" --arg field "$field" \
    '($before | .[$service][$field]) != ($after | .[$service][$field])' >/dev/null || {
    echo "Expected ${service}.${field} to change." >&2
    exit 1
  }
}

assert_same() {
  local before="$1"
  local after="$2"
  local service="$3"
  local field="$4"
  jq -ne --argjson before "$before" --argjson after "$after" --arg service "$service" --arg field "$field" \
    '($before | .[$service][$field]) == ($after | .[$service][$field])' >/dev/null || {
    echo "Expected ${service}.${field} to stay the same." >&2
    exit 1
  }
}

write_tree
base_sha="$(commit "base")"
base="$(revisions_at "$base_sha")"
jq -e '
  ([keys[]] | sort) == ["config-server","db-seed","embedding-service","eureka-server","gateway","k6","order-service","product-service","user-service"]
  and all(to_entries[]; .value.image_tag | test("^[0-9a-f]{64}$"))
  and all(to_entries[] | select(.value.config_label != null); .value.config_label | test("^[0-9a-f]{40}$"))
  and all(.["db-seed"], .["embedding-service"], .["k6"]; has("config_label") | not)
' <<<"$base" >/dev/null

printf 'user-changed\n' > "$REPO/apps/user-service/src.txt"
user_sha="$(commit "user-service only")"
user="$(revisions_at "$user_sha")"
assert_changed "$base" "$user" user-service image_tag
for service in config-server eureka-server gateway product-service order-service db-seed embedding-service k6; do
  assert_same "$base" "$user" "$service" image_tag
done
for service in config-server eureka-server gateway user-service product-service order-service; do
  assert_same "$base" "$user" "$service" config_label
done

printf 'common-changed\n' > "$REPO/libs/common/src.txt"
common_sha="$(commit "libs/common")"
common="$(revisions_at "$common_sha")"
assert_changed "$user" "$common" user-service image_tag
assert_changed "$user" "$common" product-service image_tag
assert_changed "$user" "$common" order-service image_tag
for service in config-server eureka-server gateway db-seed embedding-service k6; do
  assert_same "$user" "$common" "$service" image_tag
done

printf 'FROM alpine\n' > "$REPO/Dockerfile"
docker_sha="$(commit "Dockerfile")"
docker="$(revisions_at "$docker_sha")"
for service in config-server eureka-server gateway user-service product-service order-service; do
  assert_changed "$common" "$docker" "$service" image_tag
done
for service in db-seed embedding-service k6; do
  assert_same "$common" "$docker" "$service" image_tag
done

printf 'embedding-changed\n' > "$REPO/apps/embedding-service/app.py"
embedding_sha="$(commit "embedding-service only")"
embedding="$(revisions_at "$embedding_sha")"
assert_changed "$docker" "$embedding" embedding-service image_tag
for service in config-server eureka-server gateway user-service product-service order-service db-seed k6; do
  assert_same "$docker" "$embedding" "$service" image_tag
done

printf 'k6-changed\n' > "$REPO/load-test/scenario.js"
k6_sha="$(commit "load-test scenario")"
k6="$(revisions_at "$k6_sha")"
assert_changed "$embedding" "$k6" k6 image_tag
for service in config-server eureka-server gateway user-service product-service order-service db-seed embedding-service; do
  assert_same "$embedding" "$k6" "$service" image_tag
done

printf 'order-dev-changed: {}\n' > "$REPO/config-repo/order-service-dev.yaml"
order_cfg_sha="$(commit "order-service-dev.yaml")"
order_cfg="$(revisions_at "$order_cfg_sha")"
assert_changed "$docker" "$order_cfg" order-service config_label
assert_changed "$docker" "$order_cfg" config-server config_label
for service in eureka-server gateway user-service product-service; do
  assert_same "$docker" "$order_cfg" "$service" config_label
done
for service in config-server eureka-server gateway user-service product-service order-service db-seed; do
  assert_same "$k6" "$order_cfg" "$service" image_tag
done

echo "service-revisions.sh regression tests passed."
