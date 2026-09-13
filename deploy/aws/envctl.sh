#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC2034 # globals consumed by sourced helpers

# Terraform owns cluster, service identities, networking, RDS, MSK, Redis, ALB,
# observation instance, and the initial task definition.
# envctl owns later revisions, desired count, autoscaling capacity, RDS/obs power,
# and runtime/current.json as the only success pointer.

usage() {
  echo "Usage: $0 --action create|start|deploy|stop|teardown|bootstrap-roles --config <file> [options]" >&2
  echo "  --release <file> | --release-id <id>" >&2
  echo "  --plan <file|s3-uri> --terraform-dir <dir>" >&2
  echo "  --confirm-data-preserved true|false" >&2
  echo "  --confirm-no-unprocessed-events true|false" >&2
  echo "  --seed-run-id <id>" >&2
  exit 64
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SCHEMA_PY="$SCRIPT_DIR/schema.py"
RELEASE_VALIDATOR="$REPO_ROOT/.github/scripts/validate-aws-release-record.sh"
SAFE_PLAN="$REPO_ROOT/infra/terraform/aws/scripts/assert-safe-plan.sh"
AWS_BIN="${AWS_BIN:-aws}"
TF_BIN="${TF_BIN:-terraform}"
WAIT_ATTEMPTS="${ENVCTL_WAIT_ATTEMPTS:-60}"
WAIT_SLEEP="${ENVCTL_WAIT_SLEEP:-5}"
OPERATION_ID="${OPERATION_ID:-${GITHUB_RUN_ID:-$$}}"
RECORDED_RESULT=""

ACTION="" CONFIG="" RELEASE="" RELEASE_ID="" PLAN="" TERRAFORM_DIR=""
CONFIRM_DATA="false" CONFIRM_EVENTS="false" SEED_RUN_ID=""
CURRENT_JSON="" TASKDEFS_JSON="{}" TOUCHED="" MIGRATION_STARTED=false

APP_SERVICES="config-server eureka-server gateway user-service product-service order-service"
PLATFORM_SERVICES="config-server eureka-server"
DOMAIN_SERVICES="user-service product-service order-service"
DB_SERVICES="user product order"

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --action) ACTION="$2"; shift 2 ;;
    --config) CONFIG="$2"; shift 2 ;;
    --release) RELEASE="$2"; shift 2 ;;
    --release-id) RELEASE_ID="$2"; shift 2 ;;
    --plan) PLAN="$2"; shift 2 ;;
    --terraform-dir) TERRAFORM_DIR="$2"; shift 2 ;;
    --confirm-data-preserved) CONFIRM_DATA="$2"; shift 2 ;;
    --confirm-no-unprocessed-events) CONFIRM_EVENTS="$2"; shift 2 ;;
    --seed-run-id) SEED_RUN_ID="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) usage ;;
  esac
done

[[ "$ACTION" == "create" || "$ACTION" == "start" || "$ACTION" == "deploy" || "$ACTION" == "stop" || "$ACTION" == "teardown" || "$ACTION" == "bootstrap-roles" ]] || usage
command -v jq >/dev/null && command -v python3 >/dev/null
[[ -f "$SCHEMA_PY" && -f "$RELEASE_VALIDATOR" ]]

fail() { echo "$1" >&2; exit "${2:-1}"; }
require_live() { [[ "${ENVCTL_ALLOW_LIVE:-false}" == "true" ]] || fail "Refusing live AWS/Terraform mutation. Set ENVCTL_ALLOW_LIVE=true in GitHub production." 78; }
run_aws() { "$AWS_BIN" "$@"; }
run_tf() { "$TF_BIN" "$@"; }
json_get() { jq -er "$1" "$2"; }
require_file() { [[ -n "$1" && -f "$1" ]] || fail "$2" 64; }
bool() { [[ "$1" == "true" || "$1" == "false" ]] || fail "Boolean flags must be true or false." 65; }
bool "$CONFIRM_DATA"
bool "$CONFIRM_EVENTS"

identity_ok() {
  local caller account
  : "${AWS_ACCOUNT_ID:?AWS_ACCOUNT_ID is required}"
  caller="$(run_aws sts get-caller-identity --output json)"
  account="$(jq -er '.Account' <<<"$caller")"
  [[ "$account" == "$AWS_ACCOUNT_ID" ]] || fail "AWS account $account does not match AWS_ACCOUNT_ID." 65
}

load_config() {
  require_file "$CONFIG" "--config is required"
  python3 "$SCHEMA_PY" config "$CONFIG"
  CLUSTER="$(json_get '.cluster' "$CONFIG")"
  REGION="$(json_get '.region' "$CONFIG")"
  BUCKET="$(json_get '.release_bucket' "$CONFIG")"
  OBS_ID="$(json_get '.observation_instance_id' "$CONFIG")"
  MSK_ARN="$(json_get '.msk_arn' "$CONFIG")"
  REDIS_ID="$(json_get '.redis_id' "$CONFIG")"
  TG_ARN="$(json_get '.target_group_arn' "$CONFIG")"
  if [[ -n "${AWS_REGION:-}" && "$AWS_REGION" != "$REGION" ]]; then
    fail "AWS_REGION $AWS_REGION does not match deployment_config.region $REGION." 65
  fi
  export AWS_REGION="$REGION" AWS_DEFAULT_REGION="$REGION"
}

wait_until() {
  local label="$1" attempt=1
  shift
  while (( attempt <= WAIT_ATTEMPTS )); do
    if "$@"; then return 0; fi
    sleep "$WAIT_SLEEP"
    attempt=$((attempt + 1))
  done
  echo "Timed out waiting for $label." >&2
  return 1
}

s3_key_exists() {
  local out
  [[ -n "${BUCKET:-}" ]] || return 1
  out="$(run_aws s3api list-objects-v2 --bucket "$BUCKET" --prefix "$1" --output json)"     || fail "S3 list-objects-v2 failed for s3://$BUCKET/$1"
  jq -e --arg k "$1" 'any(.Contents[]?; .Key == $k)' <<<"$out" >/dev/null
}
s3_get() {
  [[ -n "${BUCKET:-}" ]] || fail "release_bucket is required." 65
  run_aws s3api get-object --bucket "$BUCKET" --key "$1" "$2" >/dev/null
}
s3_put() {
  [[ -n "${BUCKET:-}" ]] || { echo "Skipping S3 write; release_bucket is not loaded." >&2; return 0; }
  run_aws s3api put-object --bucket "$BUCKET" --key "$1" --body "$2" --content-type application/json >/dev/null
}

record_json() {
  local dest key="$1"
  dest="$(mktemp)"
  cat > "$dest"
  s3_put "$key" "$dest" || true
  rm -f "$dest"
}

record_failure() {
  [[ -z "$RECORDED_RESULT" ]] || return 0
  RECORDED_RESULT=failed
  jq -n --arg id "$OPERATION_ID" --arg action "$ACTION" --arg reason "${1:-failed}" --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{operation_id:$id,action:$action,result:"failed",reason:$reason,at:$ts}' | record_json "operations/failures/${OPERATION_ID}.json"
}

record_success() {
  [[ -z "$RECORDED_RESULT" ]] || fail "refusing to record success after $RECORDED_RESULT"
  RECORDED_RESULT=succeeded
  jq -n --arg id "$OPERATION_ID" --arg action "$ACTION" --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{operation_id:$id,action:$action,result:"succeeded",at:$ts}' | record_json "operations/${OPERATION_ID}.json"
}

rds_ids() { jq -r '.rds_instances | to_entries[] | .value' "$CONFIG"; }
rds_status() { run_aws rds describe-db-instances --db-instance-identifier "$1" --output json | jq -er '.DBInstances[0].DBInstanceStatus'; }
ec2_state() { run_aws ec2 describe-instances --instance-ids "$OBS_ID" --output json | jq -er '.Reservations[0].Instances[0].State.Name'; }
rds_is() { [[ "$(rds_status "$1")" == "$2" ]]; }
ec2_is() { [[ "$(ec2_state)" == "$1" ]]; }

rds_ensure_available() {
  local id status
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    status="$(rds_status "$id")"
    if [[ "$status" == "stopping" ]]; then
      WAIT_ATTEMPTS=360 wait_until "rds stopped $id" rds_is "$id" stopped
      status=stopped
    fi
    if [[ "$status" == "stopped" ]]; then
      run_aws rds start-db-instance --db-instance-identifier "$id" --output json >/dev/null
    fi
    WAIT_ATTEMPTS=360 wait_until "rds available $id" rds_is "$id" available
  done < <(rds_ids)
}

rds_stop_all() {
  local id status
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    status="$(rds_status "$id")"
    if [[ "$status" == "available" ]]; then
      run_aws rds stop-db-instance --db-instance-identifier "$id" --output json >/dev/null
    fi
    WAIT_ATTEMPTS=360 wait_until "rds stopped $id" rds_is "$id" stopped
  done < <(rds_ids)
}

rds_has_snapshot() {
  local id count
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    count="$(run_aws rds describe-db-snapshots --db-instance-identifier "$id" --output json | jq '[.DBSnapshots[] | select(.Status=="available")] | length')"
    [[ "$count" -gt 0 ]] || return 1
  done < <(rds_ids)
}

ec2_ensure_running() {
  local state; state="$(ec2_state)"
  if [[ "$state" == "stopping" ]]; then
    wait_until "obs stopped" ec2_is stopped
    state=stopped
  fi
  if [[ "$state" == "stopped" ]]; then
    run_aws ec2 start-instances --instance-ids "$OBS_ID" --output json >/dev/null
  fi
  wait_until "obs running" ec2_is running
}

ec2_stop() {
  local state; state="$(ec2_state)"
  if [[ "$state" == "running" ]]; then
    run_aws ec2 stop-instances --instance-ids "$OBS_ID" --output json >/dev/null
  fi
  wait_until "obs stopped" ec2_is stopped
}

# shellcheck source=ecs-deploy.sh
source "$SCRIPT_DIR/ecs-deploy.sh"
# shellcheck source=db-tasks.sh
source "$SCRIPT_DIR/db-tasks.sh"
# shellcheck source=obs-tasks.sh
source "$SCRIPT_DIR/obs-tasks.sh"

autoscale_parked() {
  local logical="$1"
  run_aws application-autoscaling describe-scalable-targets --service-namespace ecs --resource-ids "$(autoscale_resource "$logical")" --output json \
    | jq -e '((.ScalableTargets[0].MinCapacity|tonumber) == 0) and ((.ScalableTargets[0].MaxCapacity|tonumber) == 0)' >/dev/null
}

fetch_current() {
  CURRENT_JSON="$(mktemp)"
  if s3_key_exists runtime/current.json; then
    s3_get runtime/current.json "$CURRENT_JSON"
    return 0
  fi
  rm -f "$CURRENT_JSON"
  CURRENT_JSON=""
  return 1
}

resolve_release() {
  if [[ -n "$RELEASE" ]]; then
    require_file "$RELEASE" "--release must be a file"
    return 0
  fi
  [[ -n "$RELEASE" || -n "$RELEASE_ID" ]] || fail "deploy/bootstrap-roles requires --release or --release-id." 64
  if [[ -z "$RELEASE" ]]; then
    RELEASE="$(mktemp)"
    s3_get "releases/${RELEASE_ID}.json" "$RELEASE"
  fi
}

validate_release_for_deploy() {
  bash "$RELEASE_VALIDATOR" "$RELEASE"
  jq -e '.validation.verification_result == "passed"' "$RELEASE" >/dev/null || fail "deploy requires validation.verification_result passed." 65
}

metric_start() { date -u -v-10M +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '10 minutes ago' +%Y-%m-%dT%H:%M:%SZ; }

assert_no_kafka_lag() {
  local name found=0 line query lag
  name="$(run_aws kafka describe-cluster --cluster-arn "$MSK_ARN" --output json | jq -er '.ClusterInfo.ClusterName')"
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    found=1
    query="$(jq -n --argjson metric "$line" --arg start "$(metric_start)" --arg end "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '{StartTime:$start,EndTime:$end,MetricDataQueries:[{Id:"lag",MetricStat:{Metric:{Namespace:"AWS/Kafka",MetricName:"SumOffsetLag",Dimensions:$metric.Dimensions},Period:300,Stat:"Maximum"},ReturnData:true}]}')"
    lag="$(run_aws cloudwatch get-metric-data --output json --cli-input-json "$query")"
    jq -e '.MetricDataResults[0].Values | length > 0 and all(. == 0)' <<<"$lag" >/dev/null \
      || fail "teardown refused: unprocessed Kafka events remain." 65
  done < <(run_aws cloudwatch list-metrics --namespace AWS/Kafka --metric-name SumOffsetLag --output json | jq -c --arg n "$name" '.Metrics[] | select(any(.Dimensions[]; .Name=="Cluster Name" and .Value==$n))')
  [[ "$found" -eq 1 ]] || fail "teardown refused: MSK consumer lag metrics are unavailable." 65
}

cmd_create() {
  identity_ok
  require_live
  [[ -n "$PLAN" ]] || fail "--plan is required for create." 64
  [[ -n "$TERRAFORM_DIR" && -d "$TERRAFORM_DIR" ]] || fail "--terraform-dir is required for create." 64
  if [[ "$PLAN" == s3://* ]]; then local dest; dest="$(mktemp)"; run_aws s3 cp "$PLAN" "$dest"; PLAN="$dest"; fi
  [[ -f "$PLAN" ]] || fail "Saved Terraform plan file is required." 64
  local shown; shown="$(mktemp)"
  run_tf -chdir="$TERRAFORM_DIR" show -json "$PLAN" > "$shown"
  bash "$SAFE_PLAN" "$shown"
  run_tf -chdir="$TERRAFORM_DIR" apply -input=false -auto-approve "$PLAN"
  if run_tf -chdir="$TERRAFORM_DIR" output -json deployment_config > /tmp/cc-envctl-config.json; then
    python3 "$SCHEMA_PY" config /tmp/cc-envctl-config.json
    CONFIG=/tmp/cc-envctl-config.json
    load_config
    s3_put config/deployment_config.json "$CONFIG"
  else
    echo "Terraform output deployment_config is unavailable. Runtime stack is not ready." >&2
  fi
  record_success
  echo "create completed from saved plan."
}

cmd_start() {
  load_config; identity_ok; require_live
  fetch_current || fail "start requires runtime/current.json from the last successful deploy." 65
  rds_ensure_available
  run_observation_bootstrap
  start_last_success
  record_success
  echo "start restored the last successful version."
  echo "start does not initialize RDS or Redis and does not run seed."
}

cmd_deploy() {
  load_config; identity_ok; require_live
  resolve_release
  validate_release_for_deploy
  fetch_current || true
  rds_ensure_available
  run_observation_bootstrap
  run_migrations
  run_seed_if_requested
  local logical
  for logical in $PLATFORM_SERVICES; do deploy_one "$logical"; done
  for logical in $DOMAIN_SERVICES; do deploy_one "$logical"; done
  deploy_one gateway
  publish_success
  record_success
  echo "deploy completed: $(json_get '.release_id' "$RELEASE")"
}

cmd_stop() {
  load_config; identity_ok; require_live
  local logical
  for logical in $APP_SERVICES; do
    autoscale_set "$logical" 0 0
    wait_until "autoscale parked $logical" autoscale_parked "$logical"
    ecs_set_desired "$logical" 0
  done
  rds_stop_all
  ec2_stop
  record_success
  echo "stop completed. Fargate desired count is 0 and autoscaling is parked at 0."
  echo "MSK ($MSK_ARN) and Redis ($REDIS_ID) are left running and continue to accrue cost."
  echo "RDS auto-starts after 7 days if left stopped."
}

cmd_teardown() {
  load_config; identity_ok; require_live
  [[ "$CONFIRM_DATA" == "true" && "$CONFIRM_EVENTS" == "true" ]] || fail "teardown requires --confirm-data-preserved true and --confirm-no-unprocessed-events true." 65
  local logical desired shown dest
  for logical in $APP_SERVICES; do
    desired="$(run_aws ecs describe-services --cluster "$CLUSTER" --services "$(logical_ecs "$logical")" --output json | jq -er '.services[0].desiredCount')"
    [[ "$desired" == "0" ]] || fail "teardown requires stop first (desired count 0)." 65
  done
  rds_has_snapshot || fail "teardown refused: RDS snapshot evidence is missing." 65
  assert_no_kafka_lag
  [[ -n "$PLAN" ]] || fail "--plan is required for teardown." 64
  [[ -n "$TERRAFORM_DIR" && -d "$TERRAFORM_DIR" ]] || fail "--terraform-dir is required for teardown." 64
  if [[ "$PLAN" == s3://* ]]; then dest="$(mktemp)"; run_aws s3 cp "$PLAN" "$dest"; PLAN="$dest"; fi
  [[ -f "$PLAN" ]] || fail "Saved Terraform plan file is required." 64
  shown="$(mktemp)"
  run_tf -chdir="$TERRAFORM_DIR" show -json "$PLAN" > "$shown"
  bash "$SAFE_PLAN" "$shown"
  run_tf -chdir="$TERRAFORM_DIR" apply -input=false -auto-approve "$PLAN"
  record_success
  echo "teardown applied the saved plan. Persistent data deletes remain blocked."
}

cmd_bootstrap_roles() {
  load_config; identity_ok; require_live
  resolve_release
  rds_ensure_available
  run_bootstrap_roles
  record_success
  echo "bootstrap-roles completed for user, product, and order."
}

trap_deploy() {
  local status=$?
  trap - EXIT
  if (( status != 0 )); then
    if [[ -n "${TOUCHED:-}" || "$MIGRATION_STARTED" == true ]]; then
      rollback_or_cleanup || true
    fi
    record_failure "deploy failed" || true
  fi
  exit "$status"
}

case "$ACTION" in
  create) cmd_create ;;
  start) cmd_start ;;
  deploy) trap trap_deploy EXIT; cmd_deploy; trap - EXIT ;;
  stop) cmd_stop ;;
  teardown) cmd_teardown ;;
  bootstrap-roles) cmd_bootstrap_roles ;;
esac
