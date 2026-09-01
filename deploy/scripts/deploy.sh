#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 --manifest <candidate.json> --compose <compose.dev.yml> --base-images <base-images.lock> --state-dir <directory> [--retained-images-output <file>]" >&2
  exit 64
}

MANIFEST=""
COMPOSE_FILE=""
BASE_IMAGES_FILE=""
STATE_DIR=""
RETAINED_IMAGES_OUTPUT=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --manifest) MANIFEST="$2"; shift 2 ;;
    --compose) COMPOSE_FILE="$2"; shift 2 ;;
    --base-images) BASE_IMAGES_FILE="$2"; shift 2 ;;
    --state-dir) STATE_DIR="$2"; shift 2 ;;
    --retained-images-output) RETAINED_IMAGES_OUTPUT="$2"; shift 2 ;;
    *) usage ;;
  esac
done

[[ -n "$MANIFEST" && -n "$COMPOSE_FILE" && -n "$BASE_IMAGES_FILE" && -n "$STATE_DIR" ]] || usage
[[ -f "$MANIFEST" && -f "$COMPOSE_FILE" && -f "$BASE_IMAGES_FILE" ]] || usage

: "${DEV_DOMAIN:?DEV_DOMAIN is required}"
: "${USER_DB_ADMIN_PASSWORD_SECRET_OCID:?USER_DB_ADMIN_PASSWORD_SECRET_OCID is required}"
: "${PRODUCT_DB_ADMIN_PASSWORD_SECRET_OCID:?PRODUCT_DB_ADMIN_PASSWORD_SECRET_OCID is required}"
: "${ORDER_DB_ADMIN_PASSWORD_SECRET_OCID:?ORDER_DB_ADMIN_PASSWORD_SECRET_OCID is required}"
: "${USER_DB_PASSWORD_SECRET_OCID:?USER_DB_PASSWORD_SECRET_OCID is required}"
: "${PRODUCT_DB_PASSWORD_SECRET_OCID:?PRODUCT_DB_PASSWORD_SECRET_OCID is required}"
: "${ORDER_DB_PASSWORD_SECRET_OCID:?ORDER_DB_PASSWORD_SECRET_OCID is required}"
: "${GRAFANA_ADMIN_PASSWORD_SECRET_OCID:?GRAFANA_ADMIN_PASSWORD_SECRET_OCID is required}"
: "${OCIR_REGISTRY:?OCIR_REGISTRY is required}"

ENABLE_MESSAGING_PROFILE="${ENABLE_MESSAGING_PROFILE:-true}"
ENABLE_OBSERVABILITY_PROFILE="${ENABLE_OBSERVABILITY_PROFILE:-true}"
FORCE_DEPLOY="${FORCE_DEPLOY:-false}"
DEPLOY_TIMEOUT_SECONDS="${DEPLOY_TIMEOUT_SECONDS:-900}"
PULL_TIMEOUT_SECONDS="${PULL_TIMEOUT_SECONDS:-600}"
ROLLBACK_TIMEOUT_SECONDS="${ROLLBACK_TIMEOUT_SECONDS:-600}"

for value in "$ENABLE_MESSAGING_PROFILE" "$ENABLE_OBSERVABILITY_PROFILE" "$FORCE_DEPLOY"; do
  [[ "$value" == "true" || "$value" == "false" ]] || {
    echo "Boolean deployment flags must be true or false." >&2
    exit 65
  }
done

for value in "$DEPLOY_TIMEOUT_SECONDS" "$PULL_TIMEOUT_SECONDS" "$ROLLBACK_TIMEOUT_SECONDS"; do
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || {
    echo "Deployment timeout values must be positive integers." >&2
    exit 65
  }
done

command -v timeout >/dev/null
command -v docker-credential-ocir >/dev/null || {
  echo "docker-credential-ocir is required before deployment." >&2
  exit 69
}
printf '%s\n' "$OCIR_REGISTRY" | timeout --foreground 30s docker-credential-ocir get >/dev/null || {
  echo "OCIR instance-principal credential preflight failed." >&2
  exit 69
}

umask 077
mkdir -p "$STATE_DIR/releases" "$STATE_DIR/runtime"

candidate_sha="$(jq -er '.commit_sha' "$MANIFEST")"
config_sha="$(jq -er '.config_sha' "$MANIFEST")"
if [[ ! "$candidate_sha" =~ ^[0-9a-f]{40}$ || "$config_sha" != "$candidate_sha" ]]; then
  echo "Manifest commit_sha and config_sha must be the same full lowercase Git SHA." >&2
  exit 65
fi

required_services=(config-server eureka-server gateway user-service product-service order-service)
database_containers=(user-postgres product-postgres order-postgres)
for service in "${required_services[@]}"; do
  jq -e --arg service "$service" '
    .images[$service]
    | strings
    | test("@sha256:[0-9a-f]{64}$")
  ' "$MANIFEST" >/dev/null
done
[[ "$(jq '.images | length' "$MANIFEST")" -eq "${#required_services[@]}" ]] || {
  echo "Manifest must contain exactly six service images." >&2
  exit 65
}

read_locked_image() {
  local key="$1"
  local count value
  count="$(grep -c "^${key}=" "$BASE_IMAGES_FILE" || true)"
  [[ "$count" == "1" ]] || {
    echo "$BASE_IMAGES_FILE must define $key exactly once." >&2
    return 1
  }
  value="$(grep "^${key}=" "$BASE_IMAGES_FILE" | cut -d= -f2-)"
  [[ "$value" =~ ^[^[:space:]]+@sha256:[0-9a-f]{64}$ ]] || {
    echo "$key must use an immutable sha256 image digest." >&2
    return 1
  }
  printf '%s' "$value"
}

base_image_keys=(POSTGRES_IMAGE REDIS_IMAGE KAFKA_IMAGE KAFKA_UI_IMAGE PROMETHEUS_IMAGE GRAFANA_IMAGE ZIPKIN_IMAGE CADDY_IMAGE)
LOCKED_POSTGRES_IMAGE="$(read_locked_image POSTGRES_IMAGE)"
LOCKED_REDIS_IMAGE="$(read_locked_image REDIS_IMAGE)"
LOCKED_KAFKA_IMAGE="$(read_locked_image KAFKA_IMAGE)"
LOCKED_KAFKA_UI_IMAGE="$(read_locked_image KAFKA_UI_IMAGE)"
LOCKED_PROMETHEUS_IMAGE="$(read_locked_image PROMETHEUS_IMAGE)"
LOCKED_GRAFANA_IMAGE="$(read_locked_image GRAFANA_IMAGE)"
LOCKED_ZIPKIN_IMAGE="$(read_locked_image ZIPKIN_IMAGE)"
LOCKED_CADDY_IMAGE="$(read_locked_image CADDY_IMAGE)"

locked_image_value() {
  case "$1" in
    POSTGRES_IMAGE) printf '%s' "$LOCKED_POSTGRES_IMAGE" ;;
    REDIS_IMAGE) printf '%s' "$LOCKED_REDIS_IMAGE" ;;
    KAFKA_IMAGE) printf '%s' "$LOCKED_KAFKA_IMAGE" ;;
    KAFKA_UI_IMAGE) printf '%s' "$LOCKED_KAFKA_UI_IMAGE" ;;
    PROMETHEUS_IMAGE) printf '%s' "$LOCKED_PROMETHEUS_IMAGE" ;;
    GRAFANA_IMAGE) printf '%s' "$LOCKED_GRAFANA_IMAGE" ;;
    ZIPKIN_IMAGE) printf '%s' "$LOCKED_ZIPKIN_IMAGE" ;;
    CADDY_IMAGE) printf '%s' "$LOCKED_CADDY_IMAGE" ;;
    *) return 1 ;;
  esac
}

current_env="$STATE_DIR/runtime/current.env"
previous_env="$STATE_DIR/runtime/previous.env"
candidate_env="$STATE_DIR/runtime/candidate.env"
candidate_env_tmp=""

cleanup_temp_files() {
  if [[ -n "$candidate_env_tmp" && -f "$candidate_env_tmp" ]]; then
    rm -f "$candidate_env_tmp"
  fi
}
trap cleanup_temp_files EXIT

env_value() {
  local key="$1"
  local file="$2"
  awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$file"
}

service_env_key() {
  printf '%s_IMAGE' "$(printf '%s' "$1" | tr '[:lower:]-' '[:upper:]_')"
}

image_digest_from_env() {
  local service="$1"
  local file="$2"
  local image
  image="$(env_value "$(service_env_key "$service")" "$file")"
  [[ "$image" =~ @(sha256:[0-9a-f]{64})$ ]] || return 1
  printf '%s' "${BASH_REMATCH[1]}"
}

write_retained_images() {
  local target="$1"
  local target_tmp json service current_digest previous_digest digests
  target_tmp="$(mktemp "${target}.XXXXXX")"
  json='{"services":{}}'

  for service in "${required_services[@]}"; do
    current_digest="$(image_digest_from_env "$service" "$current_env")" || return 1
    previous_digest=""
    if [[ -f "$previous_env" ]]; then
      previous_digest="$(image_digest_from_env "$service" "$previous_env")" || return 1
    fi
    digests="$(jq -cn --arg current "$current_digest" --arg previous "$previous_digest" \
      '[$current, $previous] | map(select(length > 0)) | unique')"
    json="$(jq -c --arg service "$service" --argjson digests "$digests" \
      '.services[$service] = $digests' <<<"$json")"
  done

  jq -e '.services | length == 6 and all(.[]; length >= 1 and length <= 2)' <<<"$json" >/dev/null
  printf '%s\n' "$json" > "$target_tmp"
  chmod 0600 "$target_tmp"
  mv "$target_tmp" "$target"
}

cleanup_old_releases() {
  local current_sha previous_sha directory name
  current_sha="$(env_value CANDIDATE_SHA "$current_env")"
  previous_sha=""
  if [[ -f "$previous_env" ]]; then
    previous_sha="$(env_value CANDIDATE_SHA "$previous_env")"
  fi

  while IFS= read -r directory; do
    name="$(basename "$directory")"
    [[ "$name" =~ ^[0-9a-f]{40}$ ]] || continue
    if [[ "$name" != "$current_sha" && "$name" != "$previous_sha" ]]; then
      rm -rf -- "$directory"
    fi
  done < <(find "$STATE_DIR/releases" -mindepth 1 -maxdepth 1 -type d -print)
}

if [[ "$FORCE_DEPLOY" != "true" && -f "$current_env" ]] \
  && [[ "$(env_value CANDIDATE_SHA "$current_env")" == "$candidate_sha" ]] \
  && [[ "$(env_value ENABLE_MESSAGING_PROFILE "$current_env")" == "$ENABLE_MESSAGING_PROFILE" ]] \
  && [[ "$(env_value ENABLE_OBSERVABILITY_PROFILE "$current_env")" == "$ENABLE_OBSERVABILITY_PROFILE" ]]; then
  cleanup_old_releases
  if [[ -n "$RETAINED_IMAGES_OUTPUT" ]]; then
    mkdir -p "$(dirname "$RETAINED_IMAGES_OUTPUT")"
    write_retained_images "$RETAINED_IMAGES_OUTPUT"
  fi
  echo "No-op: candidate $candidate_sha is already deployed."
  exit 0
fi

repository_root="$(cd "$(dirname "$COMPOSE_FILE")/.." && pwd)"
release_root="$STATE_DIR/releases/$candidate_sha"
release_source="$release_root/source"
mkdir -p "$release_source/deploy"
cp "$COMPOSE_FILE" "$release_source/deploy/compose.dev.yml"
cp "$repository_root/deploy/Caddyfile" "$release_source/deploy/Caddyfile"
cp -R "$repository_root/deploy/prometheus" "$release_source/deploy/"
cp -R "$repository_root/deploy/grafana" "$release_source/deploy/"
cp -R "$repository_root/deploy/postgres" "$release_source/deploy/"
cp "$MANIFEST" "$release_root/manifest.json"

secret_value() {
  local secret_ocid="$1"
  OCI_CLI_AUTH=instance_principal \
    oci secrets secret-bundle get \
      --secret-id "$secret_ocid" \
      --query 'data."secret-bundle-content".content' \
      --raw-output \
    | base64 --decode
}

read_secret_value() {
  local name="$1"
  local secret_ocid="$2"
  local value

  if ! value="$(secret_value "$secret_ocid")"; then
    echo "Failed to read OCI Vault Secret: $name" >&2
    return 1
  fi
  if [[ -z "$value" || ! "$value" =~ ^[A-Za-z0-9_+./=@%-]+$ ]]; then
    echo "OCI Vault Secret must be a non-empty Compose-safe single-line value: $name" >&2
    return 1
  fi
  printf '%s' "$value"
}

write_release_env() {
  local target="$1"
  local user_db_admin_password product_db_admin_password order_db_admin_password
  local user_db_password product_db_password order_db_password grafana_admin_password
  local key

  user_db_admin_password="$(read_secret_value USER_DB_ADMIN_PASSWORD "$USER_DB_ADMIN_PASSWORD_SECRET_OCID")" || return 1
  product_db_admin_password="$(read_secret_value PRODUCT_DB_ADMIN_PASSWORD "$PRODUCT_DB_ADMIN_PASSWORD_SECRET_OCID")" || return 1
  order_db_admin_password="$(read_secret_value ORDER_DB_ADMIN_PASSWORD "$ORDER_DB_ADMIN_PASSWORD_SECRET_OCID")" || return 1
  user_db_password="$(read_secret_value USER_DB_PASSWORD "$USER_DB_PASSWORD_SECRET_OCID")" || return 1
  product_db_password="$(read_secret_value PRODUCT_DB_PASSWORD "$PRODUCT_DB_PASSWORD_SECRET_OCID")" || return 1
  order_db_password="$(read_secret_value ORDER_DB_PASSWORD "$ORDER_DB_PASSWORD_SECRET_OCID")" || return 1
  grafana_admin_password="$(read_secret_value GRAFANA_ADMIN_PASSWORD "$GRAFANA_ADMIN_PASSWORD_SECRET_OCID")" || return 1

  {
    printf 'CANDIDATE_SHA=%s\n' "$candidate_sha"
    printf 'DEV_DOMAIN=%s\n' "$DEV_DOMAIN"
    printf 'ENABLE_MESSAGING_PROFILE=%s\n' "$ENABLE_MESSAGING_PROFILE"
    printf 'ENABLE_OBSERVABILITY_PROFILE=%s\n' "$ENABLE_OBSERVABILITY_PROFILE"
    printf 'USER_DB_ADMIN_PASSWORD=%s\n' "$user_db_admin_password"
    printf 'PRODUCT_DB_ADMIN_PASSWORD=%s\n' "$product_db_admin_password"
    printf 'ORDER_DB_ADMIN_PASSWORD=%s\n' "$order_db_admin_password"
    printf 'USER_DB_PASSWORD=%s\n' "$user_db_password"
    printf 'PRODUCT_DB_PASSWORD=%s\n' "$product_db_password"
    printf 'ORDER_DB_PASSWORD=%s\n' "$order_db_password"
    printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$grafana_admin_password"
    for key in "${base_image_keys[@]}"; do
      printf '%s=%s\n' "$key" "$(locked_image_value "$key")"
    done
    jq -r '
      .images
      | to_entries[]
      | (.key | ascii_upcase | gsub("-"; "_") + "_IMAGE") + "=" + .value
    ' "$MANIFEST"
  } > "$target"
  chmod 0600 "$target"
}

candidate_env_tmp="$(mktemp "$STATE_DIR/runtime/candidate.XXXXXX")"
write_release_env "$candidate_env_tmp"
mv "$candidate_env_tmp" "$candidate_env"
candidate_env_tmp=""

ACTIVE_ENV_FILE="$candidate_env"
ACTIVE_SOURCE_DIR="$release_source"
compose() {
  docker compose \
    --project-directory "$ACTIVE_SOURCE_DIR" \
    --env-file "$ACTIVE_ENV_FILE" \
    -f "$ACTIVE_SOURCE_DIR/deploy/compose.dev.yml" \
    "$@"
}

compose_timed() {
  local timeout_seconds="$1"
  shift
  timeout --foreground "${timeout_seconds}s" \
    docker compose \
      --project-directory "$ACTIVE_SOURCE_DIR" \
      --env-file "$ACTIVE_ENV_FILE" \
      -f "$ACTIVE_SOURCE_DIR/deploy/compose.dev.yml" \
      "$@"
}

DEPLOY_DEADLINE=0

wait_healthy() {
  local service="$1"
  while (( SECONDS < DEPLOY_DEADLINE )); do
    local container health
    container="$(compose ps -q "$service")"
    if [[ -n "$container" ]]; then
      health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
      if [[ "$health" == "healthy" || "$health" == "running" ]]; then
        return 0
      fi
      if [[ "$health" == "unhealthy" || "$health" == "exited" || "$health" == "dead" ]]; then
        echo "$service is $health" >&2
        return 1
      fi
    fi
    sleep 5
  done
  echo "Timed out waiting for $service" >&2
  return 1
}

reconcile_database_credentials() {
  local container
  for container in "${database_containers[@]}"; do
    # Variables are expanded inside each PostgreSQL container.
    # shellcheck disable=SC2016
    compose exec -T "$container" sh -euc '
      psql --username "$POSTGRES_USER" --dbname "$SERVICE_DB_NAME"
    ' < "$ACTIVE_SOURCE_DIR/deploy/postgres/reconcile-credentials.sql" || return 1
  done
}

verify_databases() {
  local container target
  for container in "${database_containers[@]}"; do
    # Variables are expanded inside each PostgreSQL container.
    # shellcheck disable=SC2016
    compose exec -T "$container" sh -euc '
      host="$1"
      PGCONNECT_TIMEOUT=5 PGPASSWORD="$POSTGRES_PASSWORD" psql --host "$host" --username "$POSTGRES_USER" --dbname "$SERVICE_DB_NAME" --tuples-only --no-align --command "SELECT 1" | grep -Fqx 1
      PGCONNECT_TIMEOUT=5 PGPASSWORD="$SERVICE_DB_PASSWORD" psql --host "$host" --username "$SERVICE_DB_USERNAME" --dbname "$SERVICE_DB_NAME" --tuples-only --no-align --command "SELECT 1" | grep -Fqx 1
    ' sh "$container" || return 1
  done

  for pair in "user-postgres:product-postgres" "product-postgres:order-postgres" "order-postgres:user-postgres"; do
    container="${pair%%:*}"
    target="${pair##*:}"
    # A service credential must not reach or authenticate to another service database.
    # shellcheck disable=SC2016
    if compose exec -T "$container" sh -euc '
      target="$1"
      PGCONNECT_TIMEOUT=3 PGPASSWORD="$SERVICE_DB_PASSWORD" psql --host "$target" --username "$SERVICE_DB_USERNAME" --dbname "$SERVICE_DB_NAME" --command "SELECT 1"
    ' sh "$target" >/dev/null 2>&1; then
      echo "$container unexpectedly connected to $target." >&2
      return 1
    fi
  done
}

verify_kafka() {
  local topic="cc-deploy-${candidate_sha:0:12}"
  local status=0

  if ! compose --profile messaging exec -T kafka \
    /opt/kafka/bin/kafka-topics.sh \
      --bootstrap-server kafka:19092 \
      --create \
      --if-not-exists \
      --topic "$topic" \
      --partitions 1 \
      --replication-factor 1 >/dev/null; then
    status=1
  elif ! printf '%s\n' "$candidate_sha" | compose --profile messaging exec -T kafka \
    /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server kafka:19092 \
      --topic "$topic" >/dev/null; then
    status=1
  elif ! compose --profile messaging exec -T kafka \
    /opt/kafka/bin/kafka-console-consumer.sh \
      --bootstrap-server kafka:19092 \
      --topic "$topic" \
      --from-beginning \
      --max-messages 1 \
      --timeout-ms 10000 \
    | grep -Fqx "$candidate_sha"; then
    status=1
  fi

  compose --profile messaging exec -T kafka \
    /opt/kafka/bin/kafka-topics.sh \
      --bootstrap-server kafka:19092 \
      --delete \
      --topic "$topic" >/dev/null 2>&1 || true

  return "$status"
}

verify_prometheus() {
  local targets
  while (( SECONDS < DEPLOY_DEADLINE )); do
    targets="$(compose --profile observability exec -T prometheus \
      wget -T 5 -qO- http://localhost:9090/api/v1/targets 2>/dev/null)" || true
    [[ -n "$targets" ]] || targets='{}'
    if jq -e '
      [
        .data.activeTargets[]?
        | select(.labels.job as $job | ["config-server", "eureka-server", "gateway", "user-service", "product-service", "order-service"] | index($job))
      ] as $targets
      | ($targets | map(.labels.job) | unique | sort) == ["config-server", "eureka-server", "gateway", "order-service", "product-service", "user-service"]
      and ($targets | all(.health == "up"))
    ' <<<"$targets" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  echo "Prometheus targets did not become healthy before the deployment deadline." >&2
  return 1
}

verify_eureka() {
  local applications
  while (( SECONDS < DEPLOY_DEADLINE )); do
    applications="$(compose exec -T gateway curl --connect-timeout 2 --max-time 5 -fsS \
      -H 'Accept: application/json' \
      http://eureka-server:8761/eureka/apps 2>/dev/null)" || true
    [[ -n "$applications" ]] || applications='{}'
    if jq -e '
      def arrayify: if type == "array" then . else [.] end;
      [
        .applications.application
        | arrayify[]
        | select(.name as $name | ["USER-SERVICE", "PRODUCT-SERVICE", "ORDER-SERVICE"] | index($name))
        | .instance
        | arrayify[]
        | select(.status == "UP")
        | .app
      ]
      | unique
      | sort == ["ORDER-SERVICE", "PRODUCT-SERVICE", "USER-SERVICE"]
    ' <<<"$applications" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  echo "Eureka did not report all application services as UP." >&2
  return 1
}

verify_grafana() {
  local health datasource password curl_config
  password="$(env_value GRAFANA_ADMIN_PASSWORD "$ACTIVE_ENV_FILE")"
  while (( SECONDS < DEPLOY_DEADLINE )); do
    health="$(compose exec -T gateway curl --connect-timeout 2 --max-time 5 -fsS http://grafana:3000/api/health 2>/dev/null)" || true
    curl_config="$(printf 'silent\nshow-error\nfail\nconnect-timeout = 2\nmax-time = 5\nuser = "admin:%s"\nurl = "http://grafana:3000/api/datasources/uid/prometheus/health"\n' "$password")"
    datasource="$(printf '%s' "$curl_config" | compose exec -T gateway curl --config - 2>/dev/null)" || true
    [[ -n "$health" ]] || health='{}'
    [[ -n "$datasource" ]] || datasource='{}'
    if jq -e '.database == "ok"' <<<"$health" >/dev/null 2>&1 \
      && jq -e '.status == "OK"' <<<"$datasource" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  echo "Grafana health or Prometheus datasource verification failed." >&2
  return 1
}

verify_zipkin() {
  local services
  compose exec -T gateway curl --connect-timeout 2 --max-time 5 -fsS http://user-service:8081/actuator/health >/dev/null || return 1
  compose exec -T gateway curl --connect-timeout 2 --max-time 5 -fsS http://product-service:8082/actuator/health >/dev/null || return 1
  compose exec -T gateway curl --connect-timeout 2 --max-time 5 -fsS http://order-service:8083/actuator/health >/dev/null || return 1

  while (( SECONDS < DEPLOY_DEADLINE )); do
    services="$(compose exec -T gateway curl --connect-timeout 2 --max-time 5 -fsS http://zipkin:9411/api/v2/services 2>/dev/null)" || true
    [[ -n "$services" ]] || services='[]'
    if jq -e '
      (["gateway", "order-service", "product-service", "user-service"] - .) | length == 0
    ' <<<"$services" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  echo "Zipkin did not report traces for Gateway and all application services." >&2
  return 1
}

disable_profile_services() {
  local profile="$1"
  shift
  compose --profile "$profile" stop -t 30 "$@" >/dev/null 2>&1 || true
  compose --profile "$profile" rm -sf "$@" >/dev/null 2>&1 || true
}

stop_legacy_postgres() {
  local containers container
  containers="$(docker ps -q \
    --filter label=com.docker.compose.project=cc-dev \
    --filter label=com.docker.compose.service=postgres)"
  [[ -n "$containers" ]] || return 0

  while IFS= read -r container; do
    [[ -n "$container" ]] || continue
    docker stop --time 30 "$container" >/dev/null || return 1
    echo "Stopped legacy PostgreSQL container without removing its data volume: $container"
  done <<<"$containers"
}

verify_container_runtime() {
  local runtime_services=(user-postgres product-postgres order-postgres zipkin config-server eureka-server user-service product-service order-service gateway caddy)
  local details container detail service
  if [[ "$ENABLE_MESSAGING_PROFILE" == "true" ]]; then
    runtime_services+=(redis kafka kafka-ui)
  fi
  if [[ "$ENABLE_OBSERVABILITY_PROFILE" == "true" ]]; then
    runtime_services+=(prometheus grafana)
  fi

  details=""
  for service in "${runtime_services[@]}"; do
    container="$(compose ps -q "$service")"
    if [[ -z "$container" || "$container" == *$'\n'* ]]; then
      echo "Expected exactly one running container for $service." >&2
      return 1
    fi
    detail="$(docker inspect --format '{{.Name}}|{{.RestartCount}}|{{.State.OOMKilled}}|{{.State.Status}}' "$container")" || return 1
    details+="${detail}"$'\n'
  done
  if awk -F'|' 'NF == 4 && ($2 != 0 || $3 != "false" || $4 != "running") {print; failed=1} END {exit failed}' <<<"$details"; then
    return 0
  fi
  echo "Container restart, OOM, or runtime state verification failed." >&2
  return 1
}

wait_https() {
  while (( SECONDS < DEPLOY_DEADLINE )); do
    if curl --fail --silent --show-error \
      --connect-timeout 2 \
      --max-time 5 \
      --resolve "${DEV_DOMAIN}:443:127.0.0.1" \
      "https://${DEV_DOMAIN}/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  echo "Timed out waiting for the public HTTPS health endpoint." >&2
  return 1
}

deploy_release() {
  case "${ENABLE_MESSAGING_PROFILE}:${ENABLE_OBSERVABILITY_PROFILE}" in
    true:true) compose_timed "$PULL_TIMEOUT_SECONDS" --profile messaging --profile observability pull || return 1 ;;
    true:false) compose_timed "$PULL_TIMEOUT_SECONDS" --profile messaging pull || return 1 ;;
    false:true) compose_timed "$PULL_TIMEOUT_SECONDS" --profile observability pull || return 1 ;;
    false:false) compose_timed "$PULL_TIMEOUT_SECONDS" pull || return 1 ;;
  esac
  DEPLOY_DEADLINE=$((SECONDS + DEPLOY_TIMEOUT_SECONDS))

  compose up -d user-postgres product-postgres order-postgres zipkin || return 1
  local database_container
  for database_container in "${database_containers[@]}"; do
    wait_healthy "$database_container" || return 1
  done
  wait_healthy zipkin || return 1
  reconcile_database_credentials || return 1
  verify_databases || return 1

  compose up -d config-server eureka-server || return 1
  wait_healthy config-server || return 1
  wait_healthy eureka-server || return 1

  local service
  for service in user-service product-service order-service; do
    compose up -d "$service" || return 1
    wait_healthy "$service" || return 1
  done

  compose up -d gateway || return 1
  wait_healthy gateway || return 1
  verify_eureka || return 1

  compose up -d caddy || return 1
  wait_healthy caddy || return 1
  wait_https || return 1
  verify_zipkin || return 1

  if [[ "$ENABLE_MESSAGING_PROFILE" == "true" ]]; then
    compose --profile messaging up -d redis kafka kafka-ui || return 1
    wait_healthy redis || return 1
    wait_healthy kafka || return 1
    wait_healthy kafka-ui || return 1
    verify_kafka || return 1
  else
    disable_profile_services messaging redis kafka kafka-ui kafka-volume-init
  fi

  if [[ "$ENABLE_OBSERVABILITY_PROFILE" == "true" ]]; then
    compose --profile observability up -d prometheus grafana || return 1
    wait_healthy prometheus || return 1
    wait_healthy grafana || return 1
    verify_prometheus || return 1
    verify_grafana || return 1
  else
    disable_profile_services observability prometheus grafana
  fi

  verify_container_runtime || return 1
  stop_legacy_postgres || return 1
}

ROLLBACK_ENV=""
ROLLBACK_SOURCE=""
if [[ -f "$current_env" ]]; then
  cp "$current_env" "$previous_env"
  rollback_sha="$(env_value CANDIDATE_SHA "$current_env")"
  if [[ "$rollback_sha" =~ ^[0-9a-f]{40}$ ]]; then
    ROLLBACK_ENV="$previous_env"
    ROLLBACK_SOURCE="$STATE_DIR/releases/$rollback_sha/source"
  fi
else
  rm -f "$previous_env"
fi

rollback() {
  if [[ -z "$ROLLBACK_ENV" || ! -f "$ROLLBACK_ENV" || ! -d "$ROLLBACK_SOURCE" ]]; then
    echo "Deployment failed and no previous cc-service release is available." >&2
    return 1
  fi

  echo "Deployment failed. Restoring the previous cc-service release." >&2
  ACTIVE_ENV_FILE="$ROLLBACK_ENV"
  ACTIVE_SOURCE_DIR="$ROLLBACK_SOURCE"
  local command_timeout=$((ROLLBACK_TIMEOUT_SECONDS / 2))
  (( command_timeout > 0 )) || command_timeout=1
  case "$(env_value ENABLE_MESSAGING_PROFILE "$ROLLBACK_ENV"):$(env_value ENABLE_OBSERVABILITY_PROFILE "$ROLLBACK_ENV")" in
    true:true)
      compose_timed "$command_timeout" --profile messaging --profile observability pull || return 1
      compose_timed "$command_timeout" --profile messaging --profile observability up -d --remove-orphans || return 1
      ;;
    true:false)
      compose_timed "$command_timeout" --profile messaging pull || return 1
      compose_timed "$command_timeout" --profile messaging up -d --remove-orphans || return 1
      ;;
    false:true)
      compose_timed "$command_timeout" --profile observability pull || return 1
      compose_timed "$command_timeout" --profile observability up -d --remove-orphans || return 1
      ;;
    false:false)
      compose_timed "$command_timeout" pull || return 1
      compose_timed "$command_timeout" up -d --remove-orphans || return 1
      ;;
  esac
  cp "$ROLLBACK_ENV" "$current_env" || return 1
  echo "Rollback completed successfully." >&2
}

if ! deploy_release; then
  rollback || true
  rm -f "$candidate_env"
  exit 1
fi

mv "$candidate_env" "$current_env"
chmod 0600 "$current_env"
cleanup_old_releases
if [[ -n "$RETAINED_IMAGES_OUTPUT" ]]; then
  mkdir -p "$(dirname "$RETAINED_IMAGES_OUTPUT")"
  write_retained_images "$RETAINED_IMAGES_OUTPUT"
fi
echo "Deployment completed: $candidate_sha"
