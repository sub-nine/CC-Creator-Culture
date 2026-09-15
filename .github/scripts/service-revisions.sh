#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 <commit-sha>" >&2
  exit 64
}

[[ "$#" -eq 1 ]] || usage
SHA="$1"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || {
  echo "Revision must be a full lowercase Git SHA." >&2
  exit 65
}

repo="$(git rev-parse --show-toplevel)"
git -C "$repo" rev-parse --verify --quiet "${SHA}^{commit}" >/dev/null || {
  echo "Unknown commit ${SHA}." >&2
  exit 65
}

sha256_hex() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum -a 256 | awk '{print $1}'
  fi
}

object_id() {
  local path="$1"
  local object
  object="$(git -C "$repo" rev-parse "${SHA}:${path}" 2>/dev/null)" || {
    echo "Missing ${path} at ${SHA}." >&2
    return 1
  }
  printf '%s' "$object"
}

image_tag() {
  local path object
  {
    for path in "$@"; do
      object="$(object_id "$path")"
      printf '%s %s\n' "$path" "$object"
    done
  } | LC_ALL=C sort | sha256_hex
}

config_label() {
  local service="$1"
  local label path
  local paths=()
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    paths+=("$path")
  done < <(config_paths_for "$service")
  label="$(git -C "$repo" log -1 --format=%H "$SHA" -- "${paths[@]}")"
  [[ "$label" =~ ^[0-9a-f]{40}$ ]] || {
    echo "No config history at ${SHA} for ${service}." >&2
    return 1
  }
  printf '%s' "$label"
}

common_image_paths=(
  build.gradle
  settings.gradle
  gradle
  gradlew
  Dockerfile
  .dockerignore
)
app_extra_paths=(
  libs/common
)

config_paths_for() {
  local service="$1"
  if [[ "$service" == "config-server" ]]; then
    printf '%s\n' config-repo
    return 0
  fi
  printf '%s\n' \
    config-repo/application.yaml \
    "config-repo/application-*.yaml" \
    "config-repo/${service}.yaml" \
    "config-repo/${service}-*.yaml"
}

java_image_tag() {
  local module="$1"
  shift
  image_tag "$module" "$@" "${common_image_paths[@]}"
}

config_server_image="$(java_image_tag infra/config-server)"
eureka_server_image="$(java_image_tag infra/eureka-server)"
gateway_image="$(java_image_tag infra/gateway)"
user_service_image="$(java_image_tag apps/user-service "${app_extra_paths[@]}")"
product_service_image="$(java_image_tag apps/product-service "${app_extra_paths[@]}")"
order_service_image="$(java_image_tag apps/order-service "${app_extra_paths[@]}")"
db_seed_image="$(image_tag deploy/aws/seed)"

config_server_label="$(config_label config-server)"
eureka_server_label="$(config_label eureka-server)"
gateway_label="$(config_label gateway)"
user_service_label="$(config_label user-service)"
product_service_label="$(config_label product-service)"
order_service_label="$(config_label order-service)"

jq -nc \
  --arg config_server_image "$config_server_image" \
  --arg config_server_label "$config_server_label" \
  --arg eureka_server_image "$eureka_server_image" \
  --arg eureka_server_label "$eureka_server_label" \
  --arg gateway_image "$gateway_image" \
  --arg gateway_label "$gateway_label" \
  --arg user_service_image "$user_service_image" \
  --arg user_service_label "$user_service_label" \
  --arg product_service_image "$product_service_image" \
  --arg product_service_label "$product_service_label" \
  --arg order_service_image "$order_service_image" \
  --arg order_service_label "$order_service_label" \
  --arg db_seed_image "$db_seed_image" \
  '{
    "config-server": {"image_tag": $config_server_image, "config_label": $config_server_label},
    "eureka-server": {"image_tag": $eureka_server_image, "config_label": $eureka_server_label},
    "gateway": {"image_tag": $gateway_image, "config_label": $gateway_label},
    "user-service": {"image_tag": $user_service_image, "config_label": $user_service_label},
    "product-service": {"image_tag": $product_service_image, "config_label": $product_service_label},
    "order-service": {"image_tag": $order_service_image, "config_label": $order_service_label},
    "db-seed": {"image_tag": $db_seed_image}
  }'
