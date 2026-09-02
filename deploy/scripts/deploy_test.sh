#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOY_SCRIPT="$SCRIPT_DIR/deploy.sh"
COMPOSE_FILE="$REPOSITORY_ROOT/deploy/compose.dev.yml"
BASE_IMAGES_FILE="$REPOSITORY_ROOT/deploy/base-images.lock"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
DOCKER_LOG="$TEST_ROOT/docker.log"
CURL_LOG="$TEST_ROOT/curl.log"
OLD_SHA="1111111111111111111111111111111111111111"
NEW_SHA="2222222222222222222222222222222222222222"
OLD_DIGEST="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
NEW_DIGEST="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
PUBLIC_CADDY_ID="eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
CC_CADDY_ID="cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

cleanup() {
  if [[ "${KEEP_TEST_ROOT:-false}" == "true" ]]; then
    echo "Test artifacts retained at $TEST_ROOT" >&2
  else
    rm -rf "$TEST_ROOT"
  fi
}
trap cleanup EXIT

mkdir -p "$FAKE_BIN"

create_manifest() {
  local target="$1"
  jq -n --arg sha "$NEW_SHA" --arg digest "$NEW_DIGEST" '
    {
      commit_sha: $sha,
      config_sha: $sha,
      ci_url: "https://example.test/actions/1",
      images: {
        "config-server": ("registry.example/config-server@" + $digest),
        "eureka-server": ("registry.example/eureka-server@" + $digest),
        "gateway": ("registry.example/gateway@" + $digest),
        "user-service": ("registry.example/user-service@" + $digest),
        "product-service": ("registry.example/product-service@" + $digest),
        "order-service": ("registry.example/order-service@" + $digest)
      }
    }
  ' > "$target"
}

write_current_env() {
  local target="$1"
  {
    printf 'CANDIDATE_SHA=%s\n' "$OLD_SHA"
    printf 'DEV_DOMAIN=dev.example.com\n'
    printf 'ENABLE_MESSAGING_PROFILE=true\n'
    printf 'ENABLE_OBSERVABILITY_PROFILE=true\n'
    printf 'POSTGRES_ADMIN_PASSWORD=old-secret\n'
    printf 'USER_DB_PASSWORD=old-secret\n'
    printf 'PRODUCT_DB_PASSWORD=old-secret\n'
    printf 'ORDER_DB_PASSWORD=old-secret\n'
    printf 'GRAFANA_ADMIN_PASSWORD=old-secret\n'
    while IFS='=' read -r key _; do
      [[ -n "$key" && "$key" != \#* ]] || continue
      printf '%s=registry.example/%s@%s\n' "$key" "$key" "$OLD_DIGEST"
    done < "$BASE_IMAGES_FILE"
    local service
    for service in CONFIG_SERVER EUREKA_SERVER GATEWAY USER_SERVICE PRODUCT_SERVICE ORDER_SERVICE; do
      printf '%s_IMAGE=registry.example/%s@%s\n' "$service" "$service" "$OLD_DIGEST"
    done
  } > "$target"
}

write_fake_commands() {
  cat > "$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

printf '%s\n' "$*" >> "$DOCKER_LOG"
if [[ "$1" == "ps" ]]; then
  args="$*"
  if [[ "$args" == *"label=com.docker.compose.service=postgres"* ]] \
    && [[ "${LEGACY_POSTGRES_RUNNING:-false}" == "true" ]]; then
    printf 'legacy-postgres-container\n'
  elif [[ "$args" == *"label=com.docker.compose.service=caddy"* ]] \
    && [[ "$args" == *"publish=80"* ]] \
    && [[ "$args" == *"publish=443"* ]] \
    && [[ -f "${EXTERNAL_CADDY_STATE_FILE:-/nonexistent}" ]]; then
    printf '%s\n' "${PUBLIC_CADDY_ID:-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee}"
  elif [[ "$args" == *"label=com.docker.compose.project=cc-dev"* ]] \
    && [[ "$args" == *"label=com.docker.compose.service=caddy"* ]] \
    && [[ -f "${CC_CADDY_STATE_FILE:-/nonexistent}" ]]; then
    printf '%s\n' "${CC_CADDY_ID:-cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc}"
  elif [[ "$args" == *"label=com.docker.compose.project=cc-dev"* ]] \
    && [[ "$args" != *"label=com.docker.compose.service="* ]] \
    && [[ -f "${CC_PROJECT_STATE_FILE:-/nonexistent}" ]]; then
    printf '%s\n' "${CC_PROJECT_ID:-dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd}"
  fi
  exit 0
fi

if [[ "$1" == "stop" ]]; then
  container="${!#}"
  if [[ "$container" == "${PUBLIC_CADDY_ID:-unset}" ]]; then
    if [[ -n "${CUTOVER_STATE_PATH:-}" ]]; then
      if stat -c '%a' "$CUTOVER_STATE_PATH" >/dev/null 2>&1; then
        mode="$(stat -c '%a' "$CUTOVER_STATE_PATH")"
      else
        mode="$(stat -f '%Lp' "$CUTOVER_STATE_PATH")"
      fi
      [[ "$mode" == "600" ]] || exit 1
    fi
    rm -f "${EXTERNAL_CADDY_STATE_FILE:-/nonexistent}"
    if [[ "${TERM_AFTER_EXTERNAL_STOP:-false}" == "true" ]]; then
      kill -TERM "$PPID"
      sleep 0.1
    fi
    exit 0
  fi
  [[ "${FAIL_LEGACY_STOP:-false}" == "true" ]] && exit 1
  exit 0
fi

if [[ "$1" == "start" ]]; then
  container="${!#}"
  [[ "$container" == "${PUBLIC_CADDY_ID:-unset}" ]] || exit 1
  : > "$EXTERNAL_CADDY_STATE_FILE"
  exit 0
fi

if [[ "$1" == "rm" ]]; then
  rm -f "${CC_CADDY_STATE_FILE:-/nonexistent}"
  exit 0
fi

if [[ "$1" == "compose" ]]; then
  shift
  env_file=""
  compose_file=""
  command=""
  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --project-directory|--env-file|-f)
        [[ "$1" == "--env-file" ]] && env_file="$2"
        [[ "$1" == "-f" ]] && compose_file="$2"
        shift 2
        ;;
      --profile)
        shift 2
        ;;
      pull|up|ps|exec|stop|rm|run|down)
        command="$1"
        shift
        break
        ;;
      *) shift ;;
    esac
  done

  candidate="$(awk -F= '$1 == "CANDIDATE_SHA" {print $2; exit}' "$env_file")"
  printf 'candidate=%s command=%s args=%s\n' "$candidate" "$command" "$*" >> "$DOCKER_LOG"
  if grep -Eq '^  postgres:' "$compose_file"; then
    printf 'candidate=%s topology=legacy command=%s\n' "$candidate" "$command" >> "$DOCKER_LOG"
  else
    printf 'candidate=%s topology=isolated command=%s\n' "$candidate" "$command" >> "$DOCKER_LOG"
  fi
  case "$command" in
    up)
      : > "$CC_PROJECT_STATE_FILE"
      [[ " $* " == *" caddy "* ]] && : > "$CC_CADDY_STATE_FILE"
      ;;
    run)
      [[ "${FAIL_CADDY_CONFIG:-false}" == "true" ]] && exit 1
      ;;
    down)
      rm -f "$CC_PROJECT_STATE_FILE" "$CC_CADDY_STATE_FILE"
      ;;
    ps)
      if [[ "$*" == "-q" ]]; then
        for service in config-server eureka-server gateway order-service order-postgres product-service product-postgres user-service user-postgres zipkin redis kafka kafka-ui prometheus grafana caddy; do
          printf '%s-%s-container\n' "$candidate" "$service"
        done
      else
        service="${!#}"
        printf '%s-%s-container\n' "$candidate" "$service"
      fi
      ;;
    exec)
      args="$*"
      if [[ "$args" == *"PGCONNECT_TIMEOUT=3"* ]]; then
        exit 1
      elif [[ "$args" == *"kafka-console-consumer.sh"* ]]; then
        printf '%s\n' "$candidate"
      elif [[ "$args" == *"/api/v1/targets"* ]]; then
        attempt=1
        if [[ -n "${PROM_ATTEMPT_FILE:-}" ]]; then
          [[ -f "$PROM_ATTEMPT_FILE" ]] && attempt=$(( $(cat "$PROM_ATTEMPT_FILE") + 1 ))
          printf '%s' "$attempt" > "$PROM_ATTEMPT_FILE"
        fi
        if (( attempt < ${PROM_READY_AFTER:-1} )); then
          health="unknown"
        else
          health="up"
        fi
        jq -n --arg health "$health" '{data:{activeTargets:[
          {labels:{job:"config-server"},health:$health},
          {labels:{job:"eureka-server"},health:$health},
          {labels:{job:"gateway"},health:$health},
          {labels:{job:"user-service"},health:$health},
          {labels:{job:"product-service"},health:$health},
          {labels:{job:"order-service"},health:$health}
        ]}}'
      elif [[ "$args" == *"eureka/apps"* ]]; then
        if [[ "${FAIL_EUREKA:-false}" == "true" ]]; then
          printf '%s\n' '{"applications":{"application":[]}}'
        else
          printf '%s\n' '{"applications":{"application":[
            {"name":"USER-SERVICE","instance":{"app":"USER-SERVICE","status":"UP"}},
            {"name":"PRODUCT-SERVICE","instance":{"app":"PRODUCT-SERVICE","status":"UP"}},
            {"name":"ORDER-SERVICE","instance":{"app":"ORDER-SERVICE","status":"UP"}}
          ]}}'
        fi
      elif [[ "$args" == *"grafana:3000/api/health"* ]]; then
        [[ "${FAIL_GRAFANA:-false}" == "true" ]] && printf '%s\n' '{"database":"failed"}' || printf '%s\n' '{"database":"ok"}'
      elif [[ "$args" == *"curl --config -"* ]]; then
        cat >/dev/null
        [[ "${FAIL_GRAFANA:-false}" == "true" ]] && printf '%s\n' '{"status":"ERROR"}' || printf '%s\n' '{"status":"OK"}'
      elif [[ "$args" == *"zipkin:9411/api/v2/services"* ]]; then
        [[ "${FAIL_ZIPKIN:-false}" == "true" ]] && printf '%s\n' '[]' || printf '%s\n' '["gateway","order-service","product-service","user-service"]'
      fi
      ;;
  esac
  exit 0
fi

if [[ "$1" == "inspect" ]]; then
  container="${!#}"
  if [[ "$container" == "${PUBLIC_CADDY_ID:-unset}" ]]; then
    if [[ "$*" == *"com.docker.compose.service"* ]]; then
      printf 'caddy\n'
    elif [[ "$*" == *"com.docker.compose.project"* ]]; then
      printf '%s\n' "${PUBLIC_CADDY_PROJECT:-external-project}"
    elif [[ "$*" == *"{{.Name}}"* ]]; then
      printf '/external-public-caddy\n'
    elif [[ "$*" == *"State.Running"* ]]; then
      [[ -f "${EXTERNAL_CADDY_STATE_FILE:-/nonexistent}" ]] && printf 'true\n' || printf 'false\n'
    else
      exit 1
    fi
  elif [[ "$*" == *"RestartCount"* ]]; then
    printf '/%s|0|false|running\n' "$container"
  elif [[ "$container" == "${FAIL_CANDIDATE_SHA:-unset}-${FAIL_HEALTH_SERVICE:-unset}-container" ]]; then
    printf 'unhealthy\n'
  else
    printf 'healthy\n'
  fi
  exit 0
fi

exit 1
EOF

  cat > "$FAKE_BIN/oci" <<'EOF'
#!/usr/bin/env bash
if [[ "${FAIL_SECRET:-false}" == "true" ]]; then
  exit 1
fi
if [[ "${EMPTY_SECRET:-false}" == "true" ]]; then
  exit 0
fi
printf 'dGVzdC1zZWNyZXQ='
EOF

  cat > "$FAKE_BIN/base64" <<'EOF'
#!/usr/bin/env bash
input="$(cat)"
if [[ -n "$input" ]]; then
  if [[ "${UNSAFE_SECRET:-false}" == "true" ]]; then
    printf 'unsafe$secret'
  else
    printf 'test-secret'
  fi
fi
exit 0
EOF

  cat > "$FAKE_BIN/docker-credential-ocir" <<'EOF'
#!/usr/bin/env bash
cat >/dev/null
printf '%s\n' '{"Username":"instance-principal","Secret":"ephemeral"}'
EOF

  cat > "$FAKE_BIN/timeout" <<'EOF'
#!/usr/bin/env bash
[[ "${1:-}" == "--foreground" ]] && shift
shift
exec "$@"
EOF

  cat > "$FAKE_BIN/curl" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CURL_LOG"
exit 0
EOF

  cat > "$FAKE_BIN/sleep" <<'EOF'
#!/usr/bin/env bash
/bin/sleep 0.05
EOF

  chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/oci" "$FAKE_BIN/base64" "$FAKE_BIN/docker-credential-ocir" "$FAKE_BIN/timeout" "$FAKE_BIN/curl" "$FAKE_BIN/sleep"
}

prepare_previous_source() {
  local state_dir="$1"
  local source_dir="$state_dir/releases/$OLD_SHA/source"
  mkdir -p "$source_dir/deploy"
  cat > "$source_dir/deploy/compose.dev.yml" <<'EOF'
name: cc-dev
services:
  postgres:
    image: ${POSTGRES_IMAGE:?POSTGRES_IMAGE is required}
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_ADMIN_PASSWORD:?POSTGRES_ADMIN_PASSWORD is required}
volumes:
  postgres-data:
EOF
  cp "$REPOSITORY_ROOT/deploy/Caddyfile" "$source_dir/deploy/Caddyfile"
  cp -R "$REPOSITORY_ROOT/deploy/prometheus" "$source_dir/deploy/"
  cp -R "$REPOSITORY_ROOT/deploy/grafana" "$source_dir/deploy/"
  cp -R "$REPOSITORY_ROOT/deploy/postgres" "$source_dir/deploy/"
}

run_deploy() {
  local manifest="$1"
  local state_dir="$2"
  local cc_project_state_file="$state_dir/fake-cc-project"
  local cc_caddy_state_file="$state_dir/fake-cc-caddy"
  local external_caddy_state_file="$state_dir/fake-external-caddy"

  mkdir -p "$state_dir"
  if [[ "${STALE_CC_CONTAINERS:-false}" == "true" ]]; then
    : > "$cc_project_state_file"
  fi
  if [[ "${PUBLIC_CADDY_RUNNING:-false}" == "true" ]]; then
    : > "$external_caddy_state_file"
  fi

  DEV_DOMAIN=dev.example.com \
  USER_DB_ADMIN_PASSWORD_SECRET_OCID=user-admin \
  PRODUCT_DB_ADMIN_PASSWORD_SECRET_OCID=product-admin \
  ORDER_DB_ADMIN_PASSWORD_SECRET_OCID=order-admin \
  USER_DB_PASSWORD_SECRET_OCID=user \
  PRODUCT_DB_PASSWORD_SECRET_OCID=product \
  ORDER_DB_PASSWORD_SECRET_OCID=order \
  GRAFANA_ADMIN_PASSWORD_SECRET_OCID=grafana \
  OCIR_REGISTRY=nrt.ocir.io \
  ENABLE_MESSAGING_PROFILE="${TEST_ENABLE_MESSAGING_PROFILE:-true}" \
  ENABLE_OBSERVABILITY_PROFILE="${TEST_ENABLE_OBSERVABILITY_PROFILE:-true}" \
  FORCE_DEPLOY="${TEST_FORCE_DEPLOY:-false}" \
  ALLOW_FIRST_CUTOVER="${TEST_ALLOW_FIRST_CUTOVER:-false}" \
  DEPLOY_TIMEOUT_SECONDS="${TEST_DEPLOY_TIMEOUT_SECONDS:-5}" \
  PULL_TIMEOUT_SECONDS=5 \
  ROLLBACK_TIMEOUT_SECONDS=5 \
  PROM_ATTEMPT_FILE="${PROM_ATTEMPT_FILE:-}" \
  PROM_READY_AFTER="${PROM_READY_AFTER:-1}" \
  DOCKER_LOG="$DOCKER_LOG" \
  CURL_LOG="$CURL_LOG" \
  CC_PROJECT_STATE_FILE="$cc_project_state_file" \
  CC_CADDY_STATE_FILE="$cc_caddy_state_file" \
  EXTERNAL_CADDY_STATE_FILE="$external_caddy_state_file" \
  CUTOVER_STATE_PATH="$state_dir/runtime/public-cutover.json" \
  PUBLIC_CADDY_ID="$PUBLIC_CADDY_ID" \
  PUBLIC_CADDY_PROJECT="${PUBLIC_CADDY_PROJECT:-external-project}" \
  CC_CADDY_ID="$CC_CADDY_ID" \
  PATH="$FAKE_BIN:$PATH" \
    bash "$DEPLOY_SCRIPT" \
      --manifest "$manifest" \
      --compose "$COMPOSE_FILE" \
      --base-images "$BASE_IMAGES_FILE" \
      --state-dir "$state_dir" \
      --retained-images-output "$state_dir/retained-images.json"
}

assert_file_line() {
  local expected="$1"
  local file="$2"
  grep -Fqx "$expected" "$file" || {
    echo "Expected line not found in $file: $expected" >&2
    exit 1
  }
}

file_mode() {
  local file="$1"
  if stat -c '%a' "$file" >/dev/null 2>&1; then
    stat -c '%a' "$file"
  else
    stat -f '%Lp' "$file"
  fi
}

assert_file_mode() {
  local expected="$1"
  local file="$2"
  local actual
  actual="$(file_mode "$file")"
  [[ "$actual" == "$expected" ]] || {
    echo "Expected mode $expected for $file, got $actual." >&2
    exit 1
  }
}

write_fake_commands
manifest="$TEST_ROOT/candidate.json"
create_manifest "$manifest"

failure_state="$TEST_ROOT/failure-state"
mkdir -p "$failure_state/runtime"
write_current_env "$failure_state/runtime/current.env"
prepare_previous_source "$failure_state"
cp "$failure_state/runtime/current.env" "$TEST_ROOT/original-current.env"
: > "$DOCKER_LOG"
export FAIL_CANDIDATE_SHA="$NEW_SHA"
export FAIL_HEALTH_SERVICE="product-service"

if run_deploy "$manifest" "$failure_state" > "$TEST_ROOT/failure.out" 2>&1; then
  echo "Expected the unhealthy candidate deployment to fail." >&2
  exit 1
fi

cmp "$TEST_ROOT/original-current.env" "$failure_state/runtime/current.env"
assert_file_line "candidate=$NEW_SHA command=up args=-d product-service" "$DOCKER_LOG"
if grep -Fq "candidate=$NEW_SHA command=up args=-d order-service" "$DOCKER_LOG"; then
  echo "Deployment continued after the failed health check." >&2
  exit 1
fi
grep -Fq "candidate=$OLD_SHA command=up" "$DOCKER_LOG"
grep -Fq "Rollback completed successfully." "$TEST_ROOT/failure.out"

success_state="$TEST_ROOT/success-state"
mkdir -p "$success_state/runtime"
write_current_env "$success_state/runtime/current.env"
prepare_previous_source "$success_state"
mkdir -p "$success_state/releases/3333333333333333333333333333333333333333"
: > "$DOCKER_LOG"
: > "$CURL_LOG"
unset FAIL_CANDIDATE_SHA FAIL_HEALTH_SERVICE
export PROM_ATTEMPT_FILE="$TEST_ROOT/prometheus-attempt"
export PROM_READY_AFTER=3
export LEGACY_POSTGRES_RUNNING=true
run_deploy "$manifest" "$success_state" > "$TEST_ROOT/success.out" 2>&1
unset PROM_ATTEMPT_FILE PROM_READY_AFTER LEGACY_POSTGRES_RUNNING

assert_file_line "CANDIDATE_SHA=$NEW_SHA" "$success_state/runtime/current.env"
assert_file_line "CANDIDATE_SHA=$OLD_SHA" "$success_state/runtime/previous.env"
assert_file_mode 600 "$success_state/runtime/current.env"
assert_file_mode 600 "$success_state/releases/$NEW_SHA/source/deploy/compose.dev.yml"
assert_file_mode 644 "$success_state/releases/$NEW_SHA/source/deploy/Caddyfile"
assert_file_mode 644 "$success_state/releases/$NEW_SHA/source/deploy/prometheus/prometheus.yml"
assert_file_mode 644 "$success_state/releases/$NEW_SHA/source/deploy/grafana/provisioning/datasources/prometheus.yml"
assert_file_mode 755 "$success_state/releases/$NEW_SHA/source/deploy/postgres/init-service-database.sh"
assert_file_mode 600 "$success_state/releases/$NEW_SHA/source/deploy/postgres/reconcile-credentials.sql"
[[ ! -d "$success_state/releases/3333333333333333333333333333333333333333" ]]
jq -e '.services | length == 6 and all(.[]; length == 2)' "$success_state/retained-images.json" >/dev/null
grep -Fq -- '--resolve dev.example.com:443:127.0.0.1' "$CURL_LOG"
grep -Fq 'stop --time 30 legacy-postgres-container' "$DOCKER_LOG"

: > "$DOCKER_LOG"
rm -f "$success_state/retained-images.json"
run_deploy "$manifest" "$success_state" > "$TEST_ROOT/no-op.out" 2>&1
grep -Fq "No-op: candidate $NEW_SHA is already deployed." "$TEST_ROOT/no-op.out"
jq -e '.services | length == 6 and all(.[]; length == 2)' "$success_state/retained-images.json" >/dev/null
[[ ! -s "$DOCKER_LOG" ]] || {
  echo "No-op deployment unexpectedly invoked Docker." >&2
  exit 1
}

: > "$DOCKER_LOG"
export TEST_FORCE_DEPLOY=true
export TEST_ENABLE_MESSAGING_PROFILE=false
export TEST_ENABLE_OBSERVABILITY_PROFILE=false
run_deploy "$manifest" "$success_state" > "$TEST_ROOT/profile-off.out" 2>&1
unset TEST_FORCE_DEPLOY TEST_ENABLE_MESSAGING_PROFILE TEST_ENABLE_OBSERVABILITY_PROFILE
grep -Fq "command=stop args=-t 30 redis kafka kafka-ui kafka-volume-init" "$DOCKER_LOG"
grep -Fq "command=rm args=-sf redis kafka kafka-ui kafka-volume-init" "$DOCKER_LOG"
grep -Fq "command=stop args=-t 30 prometheus grafana" "$DOCKER_LOG"
grep -Fq "command=rm args=-sf prometheus grafana" "$DOCKER_LOG"

secret_failure_state="$TEST_ROOT/secret-failure-state"
: > "$DOCKER_LOG"
export FAIL_SECRET=true
if run_deploy "$manifest" "$secret_failure_state" > "$TEST_ROOT/secret-failure.out" 2>&1; then
  echo "Expected OCI Secret failure to stop deployment." >&2
  exit 1
fi
unset FAIL_SECRET
grep -Fq "Failed to read OCI Vault Secret" "$TEST_ROOT/secret-failure.out"
[[ ! -f "$secret_failure_state/runtime/current.env" ]]
[[ ! -s "$DOCKER_LOG" ]]

empty_secret_state="$TEST_ROOT/empty-secret-state"
: > "$DOCKER_LOG"
export EMPTY_SECRET=true
if run_deploy "$manifest" "$empty_secret_state" > "$TEST_ROOT/empty-secret.out" 2>&1; then
  echo "Expected empty OCI Secret to stop deployment." >&2
  exit 1
fi
unset EMPTY_SECRET
grep -Fq "non-empty Compose-safe single-line value" "$TEST_ROOT/empty-secret.out"
[[ ! -s "$DOCKER_LOG" ]]

unsafe_secret_state="$TEST_ROOT/unsafe-secret-state"
: > "$DOCKER_LOG"
export UNSAFE_SECRET=true
if run_deploy "$manifest" "$unsafe_secret_state" > "$TEST_ROOT/unsafe-secret.out" 2>&1; then
  echo "Expected a Compose-unsafe OCI Secret to stop deployment." >&2
  exit 1
fi
unset UNSAFE_SECRET
grep -Fq "non-empty Compose-safe single-line value" "$TEST_ROOT/unsafe-secret.out"
[[ ! -s "$DOCKER_LOG" ]]

assert_semantic_failure() {
  local name="$1"
  local flag="$2"
  local state_dir="$TEST_ROOT/$name-state"
  mkdir -p "$state_dir/runtime"
  write_current_env "$state_dir/runtime/current.env"
  prepare_previous_source "$state_dir"
  : > "$DOCKER_LOG"
  export "$flag=true"
  export TEST_DEPLOY_TIMEOUT_SECONDS=1
  if run_deploy "$manifest" "$state_dir" > "$TEST_ROOT/$name.out" 2>&1; then
    echo "Expected $name verification failure." >&2
    exit 1
  fi
  unset "$flag" TEST_DEPLOY_TIMEOUT_SECONDS
  grep -Fq "Rollback completed successfully." "$TEST_ROOT/$name.out"
}

assert_semantic_failure eureka FAIL_EUREKA
assert_semantic_failure grafana FAIL_GRAFANA
assert_semantic_failure zipkin FAIL_ZIPKIN

first_cutover_block_state="$TEST_ROOT/first-cutover-block-state"
: > "$DOCKER_LOG"
export STALE_CC_CONTAINERS=true
export PUBLIC_CADDY_RUNNING=true
if run_deploy "$manifest" "$first_cutover_block_state" > "$TEST_ROOT/first-cutover-block.out" 2>&1; then
  echo "Expected an unapproved first public cutover to fail." >&2
  exit 1
fi
unset STALE_CC_CONTAINERS PUBLIC_CADDY_RUNNING
grep -Fq "command=down args=--remove-orphans" "$DOCKER_LOG"
if grep -Fq -- '--volumes' "$DOCKER_LOG"; then
  echo "Incomplete deployment cleanup attempted to remove named volumes." >&2
  exit 1
fi
if grep -Fq "stop --time 30 $PUBLIC_CADDY_ID" "$DOCKER_LOG"; then
  echo "The external Caddy was stopped without first-cutover approval." >&2
  exit 1
fi
[[ ! -f "$first_cutover_block_state/runtime/current.env" ]]
[[ -f "$first_cutover_block_state/fake-external-caddy" ]]

first_cutover_success_state="$TEST_ROOT/first-cutover-success-state"
: > "$DOCKER_LOG"
export PUBLIC_CADDY_RUNNING=true
export TEST_ALLOW_FIRST_CUTOVER=true
run_deploy "$manifest" "$first_cutover_success_state" > "$TEST_ROOT/first-cutover-success.out" 2>&1
unset PUBLIC_CADDY_RUNNING TEST_ALLOW_FIRST_CUTOVER
grep -Fq "stop --time 30 $PUBLIC_CADDY_ID" "$DOCKER_LOG"
if grep -Fq "start $PUBLIC_CADDY_ID" "$DOCKER_LOG"; then
  echo "A successful first cutover unexpectedly restarted the previous Caddy." >&2
  exit 1
fi
[[ ! -f "$first_cutover_success_state/runtime/public-cutover.json" ]]
[[ ! -f "$first_cutover_success_state/fake-external-caddy" ]]
assert_file_line "CANDIDATE_SHA=$NEW_SHA" "$first_cutover_success_state/runtime/current.env"

config_line="$(grep -nF "candidate=$NEW_SHA command=run" "$DOCKER_LOG" | head -n 1 | cut -d: -f1)"
cutover_line="$(grep -nF "stop --time 30 $PUBLIC_CADDY_ID" "$DOCKER_LOG" | head -n 1 | cut -d: -f1)"
caddy_line="$(grep -nF "candidate=$NEW_SHA command=up args=-d caddy" "$DOCKER_LOG" | head -n 1 | cut -d: -f1)"
[[ -n "$config_line" && -n "$cutover_line" && -n "$caddy_line" ]]
(( config_line < cutover_line && cutover_line < caddy_line )) || {
  echo "The public cutover did not occur after Caddy validation and before candidate Caddy startup." >&2
  exit 1
}

first_cutover_failure_state="$TEST_ROOT/first-cutover-failure-state"
: > "$DOCKER_LOG"
export PUBLIC_CADDY_RUNNING=true
export TEST_ALLOW_FIRST_CUTOVER=true
export FAIL_CANDIDATE_SHA="$NEW_SHA"
export FAIL_HEALTH_SERVICE=caddy
if run_deploy "$manifest" "$first_cutover_failure_state" > "$TEST_ROOT/first-cutover-failure.out" 2>&1; then
  echo "Expected a candidate Caddy failure after first cutover." >&2
  exit 1
fi
unset PUBLIC_CADDY_RUNNING TEST_ALLOW_FIRST_CUTOVER FAIL_CANDIDATE_SHA FAIL_HEALTH_SERVICE
grep -Fq "stop --time 30 $PUBLIC_CADDY_ID" "$DOCKER_LOG"
grep -Fq "start $PUBLIC_CADDY_ID" "$DOCKER_LOG"
[[ -f "$first_cutover_failure_state/fake-external-caddy" ]]
[[ ! -f "$first_cutover_failure_state/runtime/public-cutover.json" ]]
[[ ! -f "$first_cutover_failure_state/runtime/current.env" ]]

same_project_caddy_state="$TEST_ROOT/same-project-caddy-state"
: > "$DOCKER_LOG"
export PUBLIC_CADDY_RUNNING=true
export PUBLIC_CADDY_PROJECT=cc-dev
run_deploy "$manifest" "$same_project_caddy_state" > "$TEST_ROOT/same-project-caddy.out" 2>&1
unset PUBLIC_CADDY_RUNNING PUBLIC_CADDY_PROJECT
if grep -Fq "stop --time 30 $PUBLIC_CADDY_ID" "$DOCKER_LOG"; then
  echo "The current cc-service Caddy was mistaken for an external first-cutover target." >&2
  exit 1
fi

signal_cutover_state="$TEST_ROOT/signal-cutover-state"
: > "$DOCKER_LOG"
export PUBLIC_CADDY_RUNNING=true
export TEST_ALLOW_FIRST_CUTOVER=true
export TERM_AFTER_EXTERNAL_STOP=true
if run_deploy "$manifest" "$signal_cutover_state" > "$TEST_ROOT/signal-cutover.out" 2>&1; then
  echo "Expected the deployment to stop after the simulated TERM signal." >&2
  exit 1
fi
unset PUBLIC_CADDY_RUNNING TEST_ALLOW_FIRST_CUTOVER TERM_AFTER_EXTERNAL_STOP
grep -Fq "stop --time 30 $PUBLIC_CADDY_ID" "$DOCKER_LOG"
grep -Fq "start $PUBLIC_CADDY_ID" "$DOCKER_LOG"
grep -Fq "command=down args=--remove-orphans" "$DOCKER_LOG"
[[ -f "$signal_cutover_state/fake-external-caddy" ]]
[[ ! -f "$signal_cutover_state/runtime/public-cutover.json" ]]

interrupted_cutover_state="$TEST_ROOT/interrupted-cutover-state"
mkdir -p "$interrupted_cutover_state/runtime"
jq -n \
  --arg container_id "$PUBLIC_CADDY_ID" \
  --arg candidate_sha "$NEW_SHA" \
  '{container_id: $container_id, container_name: "external-public-caddy", candidate_sha: $candidate_sha}' \
  > "$interrupted_cutover_state/runtime/public-cutover.json"
chmod 0600 "$interrupted_cutover_state/runtime/public-cutover.json"
: > "$interrupted_cutover_state/fake-cc-project"
: > "$interrupted_cutover_state/fake-cc-caddy"
: > "$DOCKER_LOG"
export FAIL_SECRET=true
if run_deploy "$manifest" "$interrupted_cutover_state" > "$TEST_ROOT/interrupted-cutover.out" 2>&1; then
  echo "Expected the interrupted-cutover recovery fixture to stop on Secret retrieval." >&2
  exit 1
fi
unset FAIL_SECRET
grep -Fq "rm -f $CC_CADDY_ID" "$DOCKER_LOG"
grep -Fq "start $PUBLIC_CADDY_ID" "$DOCKER_LOG"
[[ -f "$interrupted_cutover_state/fake-external-caddy" ]]
[[ ! -f "$interrupted_cutover_state/runtime/public-cutover.json" ]]

legacy_stop_failure_state="$TEST_ROOT/legacy-stop-failure-state"
mkdir -p "$legacy_stop_failure_state/runtime"
write_current_env "$legacy_stop_failure_state/runtime/current.env"
prepare_previous_source "$legacy_stop_failure_state"
: > "$DOCKER_LOG"
export LEGACY_POSTGRES_RUNNING=true
export FAIL_LEGACY_STOP=true
if run_deploy "$manifest" "$legacy_stop_failure_state" > "$TEST_ROOT/legacy-stop-failure.out" 2>&1; then
  echo "Expected a legacy PostgreSQL stop failure to roll back." >&2
  exit 1
fi
unset LEGACY_POSTGRES_RUNNING FAIL_LEGACY_STOP
grep -Fq 'stop --time 30 legacy-postgres-container' "$DOCKER_LOG"
grep -Fq "candidate=$OLD_SHA topology=legacy command=up" "$DOCKER_LOG"
grep -Fq "Rollback completed successfully." "$TEST_ROOT/legacy-stop-failure.out"

echo "deploy.sh regression tests passed."
