#!/usr/bin/env bash
# shellcheck disable=SC2034 # MIGRATION_STARTED is read by envctl trap
# DB one-off RunTask helpers. Sourced by envctl.sh.
# bootstrap-roles uses rds_master secrets. migrate/seed use app secrets only.

db_logical() {
  case "$1" in
    user) echo user-service ;;
    product) echo product-service ;;
    order) echo order-service ;;
    *) echo "$1" ;;
  esac
}

db_host() { jq -er --arg s "$1" '.db_endpoints[$s]' "$CONFIG"; }

db_name() {
  local logical="$1" svc="$2" value
  value="$(jq -r --arg s "$logical" '.db_names[$s] // ""' "$CONFIG")"
  [[ -n "$value" && "$value" != null ]] || value="${svc}_db"
  printf '%s' "$value"
}

db_app_user() {
  local logical="$1" svc="$2" value
  value="$(jq -r --arg s "$logical" '.db_app_users[$s] // ""' "$CONFIG")"
  [[ -n "$value" && "$value" != null ]] || value="${svc}_app"
  printf '%s' "$value"
}

db_admin_user() {
  jq -r --arg s "$1" '.db_admin_users[$s] // "postgres"' "$CONFIG"
}

app_secret() { jq -er --arg s "$1" '.secret_arns.app[$s]' "$CONFIG"; }
master_secret() { jq -er --arg s "$1" '.secret_arns.rds_master[$s]' "$CONFIG"; }
migration_sg() { jq -er '.security_groups.migration' "$CONFIG"; }

seed_image() {
  local image=""
  if [[ -n "${RELEASE:-}" && -f "${RELEASE:-}" ]]; then
    image="$(jq -r '.seed.artifact // empty' "$RELEASE")"
    if [[ -z "$image" || "$image" == null ]]; then
      image="$(jq -r '.migrations.images["db-seed"] // empty' "$RELEASE")"
    fi
  fi
  if [[ -z "$image" ]]; then
    image="$(jq -r '.artifact_images["db-seed"] // empty' "$CONFIG")"
  fi
  [[ -n "$image" && "$image" != null ]] || fail "db-seed digest image is required. Set seed.artifact from migrations.json images.db-seed." 65
  [[ "$image" =~ @sha256:[0-9a-f]{64}$ ]] || fail "db-seed image must be an immutable digest ref, not a rewritten migrate digest." 65
  printf '%s' "$image"
}

register_oneoff() {
  local source="$1" image="$2" spec="$3" tmp registered described
  tmp="$(mktemp)"
  described="$(run_aws ecs describe-task-definition --task-definition "$source" --output json)"
  printf '%s' "$described" | python3 "$SCHEMA_PY" oneoff-taskdef "$image" "$spec" > "$tmp"
  registered="$(run_aws ecs register-task-definition --cli-input-json "file://${tmp}" --output json)"
  rm -f "$tmp"
  jq -er '.taskDefinition.family + ":" + (.taskDefinition.revision|tostring)' <<<"$registered"
}

run_oneoff() {
  local taskdef="$1" label="$2" net subnets sg task_arn exit_code
  subnets="$(jq -r '.subnets | join(",")' "$CONFIG")"
  sg="$(migration_sg)"
  net="awsvpcConfiguration={subnets=[${subnets}],securityGroups=[${sg}],assignPublicIp=DISABLED}"
  task_arn="$(run_aws ecs run-task --cluster "$CLUSTER" --task-definition "$taskdef" --count 1 --capacity-provider-strategy 'capacityProvider=FARGATE,weight=1' --network-configuration "$net" --output json | jq -er '.tasks[0].taskArn')"
  run_aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$task_arn"
  exit_code="$(run_aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$task_arn" --output json | jq -er '.tasks[0].containers[0].exitCode')"
  [[ "$exit_code" == "0" ]] || fail "$label exited $exit_code."
}

write_db_spec() {
  local svc="$1" kind="$2" logical host name user admin app_arn master_arn seed_arn spec
  logical="$(db_logical "$svc")"
  host="$(db_host "$logical")"
  name="$(db_name "$logical" "$svc")"
  user="$(db_app_user "$logical" "$svc")"
  admin="$(db_admin_user "$logical")"
  app_arn="$(app_secret "$logical"):password::"
  spec="$(mktemp)"
  case "$kind" in
    migrate)
      jq -n --arg svc "$svc" --arg host "$host" --arg name "$name" --arg user "$user" --arg sha "$(jq -r '.source_sha' "$RELEASE")" --arg secret "$app_arn" '{
        environment: {SERVICE:$svc, DB_HOST:$host, DB_NAME:$name, DB_USER:$user, FLYWAY_USER:$user, FLYWAY_SSLMODE:"verify-full", RELEASE_SHA:$sha},
        secrets: {FLYWAY_PASSWORD:$secret, DB_PASSWORD:$secret}
      }' > "$spec"
      ;;
    bootstrap)
      master_arn="$(master_secret "$logical"):password::"
      jq -n --arg svc "$svc" --arg host "$host" --arg name "$name" --arg user "$user" --arg admin "$admin" --arg app "$app_arn" --arg master "$master_arn" '{
        environment: {SERVICE:$svc, DB_HOST:$host, DB_NAME:$name, ADMIN_USER:$admin, APP_USER:$user, PGSSLMODE:"verify-full"},
        secrets: {ADMIN_PASSWORD:$master, APP_PASSWORD:$app},
        entrypoint: ["/scripts/bootstrap-roles.sh"]
      }' > "$spec"
      ;;
    seed)
      seed_arn="$(jq -r '.secret_arns.seed // empty' "$CONFIG")"
      [[ -n "$seed_arn" && "$seed_arn" != null ]] || fail "--seed-run-id requires secret_arns.seed" 65
      jq -n --arg svc "$svc" --arg host "$host" --arg name "$name" --arg user "$user" --arg run "$SEED_RUN_ID" --arg secret "$app_arn" --arg hash "${seed_arn}:password_hash::" '{
        environment: {SERVICE:$svc, SEED_RUN_ID:$run, DB_HOST:$host, DB_NAME:$name, DB_USER:$user, PGSSLMODE:"verify-full"},
        secrets: {DB_PASSWORD:$secret, SEED_PASSWORD_HASH:$hash}
      }' > "$spec"
      ;;
    *) fail "unknown one-off $kind" ;;
  esac
  printf '%s' "$spec"
}

migrate_one() {
  local svc="$1" artifact family spec taskdef
  artifact="$(json_get '.migrations.artifact' "$RELEASE")"
  family="$(jq -r --arg s "$(db_logical "$svc")" '.migrate_task_families[$s] // empty' "$CONFIG")"
  [[ -n "$family" ]] || family="db-migrate"
  spec="$(write_db_spec "$svc" migrate)"
  taskdef="$(register_oneoff "$family" "$artifact" "$spec")"
  rm -f "$spec"
  run_oneoff "$taskdef" "Migration $svc"
}

bootstrap_roles_one() {
  local svc="$1" image family spec taskdef
  image="$(seed_image)"
  family="$(jq -r '.bootstrap_task_family // "db-seed"' "$CONFIG")"
  spec="$(write_db_spec "$svc" bootstrap)"
  taskdef="$(register_oneoff "$family" "$image" "$spec")"
  rm -f "$spec"
  run_oneoff "$taskdef" "bootstrap-roles $svc"
}

seed_one() {
  local svc="$1" image family spec taskdef
  image="$(seed_image)"
  family="$(jq -r '.seed_task_family // "db-seed"' "$CONFIG")"
  spec="$(write_db_spec "$svc" seed)"
  taskdef="$(register_oneoff "$family" "$image" "$spec")"
  rm -f "$spec"
  run_oneoff "$taskdef" "seed $svc"
}

roles_bootstrapped() { s3_key_exists runtime/roles-bootstrapped.json; }

mark_roles_bootstrapped() {
  local dest
  dest="$(mktemp)"
  jq -n --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '{at:$ts}' > "$dest"
  s3_put runtime/roles-bootstrapped.json "$dest"
  rm -f "$dest"
}

run_bootstrap_roles() {
  local svc
  jq -e '.db_endpoints and .secret_arns.app and .secret_arns.rds_master' "$CONFIG" >/dev/null \
    || fail "bootstrap-roles requires db_endpoints and secret_arns."
  for svc in $DB_SERVICES; do
    bootstrap_roles_one "$svc"
  done
  mark_roles_bootstrapped
}

run_migrations() {
  local svc
  jq -e '.db_endpoints' "$CONFIG" >/dev/null || fail "migrate requires deployment_config.db_endpoints."
  jq -e '.secret_arns.app' "$CONFIG" >/dev/null || fail "migrate requires secret_arns.app."
  MIGRATION_STARTED=true
  for svc in $DB_SERVICES; do
    migrate_one "$svc"
  done
}

run_seed_if_requested() {
  local svc
  [[ -n "$SEED_RUN_ID" ]] || return 0
  jq -er '.secret_arns.seed | type == "string" and startswith("arn:aws:secretsmanager:")' "$CONFIG" >/dev/null     || fail "--seed-run-id requires secret_arns.seed ARN" 65
  for svc in $DB_SERVICES; do
    seed_one "$svc"
  done
}
