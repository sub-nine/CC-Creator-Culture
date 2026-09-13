#!/usr/bin/env bash
# ECS digest register, rollout wait, autoscale, rollback.
# Sourced by envctl.sh. Not a standalone entrypoint.

environment_fingerprint() {
  jq -c '{cluster, msk_arn, redis_id, db_endpoints, target_group_arn}' "$CONFIG"
}

service_sg() { jq -er --arg s "$1" '.security_groups[$s]' "$CONFIG"; }
logical_ecs() { jq -er --arg s "$1" '.services[$s]' "$CONFIG"; }
logical_family() { jq -er --arg s "$1" '.service_task_families[$s]' "$CONFIG"; }

autoscale_resource() { printf 'service/%s/%s' "$CLUSTER" "$(logical_ecs "$1")"; }

max_for() {
  case "$1" in
    config-server|eureka-server) echo 1 ;;
    order-service) echo 6 ;;
    *) echo 4 ;;
  esac
}

autoscale_set() {
  local logical="$1" min_c="$2" max_c="$3"
  run_aws application-autoscaling register-scalable-target \
    --service-namespace ecs \
    --resource-id "$(autoscale_resource "$logical")" \
    --scalable-dimension ecs:service:DesiredCount \
    --min-capacity "$min_c" \
    --max-capacity "$max_c" \
    --output json >/dev/null
}

service_taskdef() {
  run_aws ecs describe-services --cluster "$CLUSTER" --services "$1" --output json \
    | jq -er '.services[0].taskDefinition'
}

register_digest() {
  local source="$1" image="$2" container="$3" config_sha="$4" tmp registered described
  tmp="$(mktemp)"
  described="$(run_aws ecs describe-task-definition --task-definition "$source" --output json)"
  printf '%s' "$described" | python3 "$SCHEMA_PY" rewrite-taskdef "$image" "$container" "$config_sha" > "$tmp"
  registered="$(run_aws ecs register-task-definition --cli-input-json "file://${tmp}" --output json)"
  rm -f "$tmp"
  jq -er '.taskDefinition.family + ":" + (.taskDefinition.revision|tostring)' <<<"$registered"
}

primary_rollout_ok() {
  local ecs_name="$1" want="$2"
  run_aws ecs describe-services --cluster "$CLUSTER" --services "$ecs_name" --output json \
    | jq -e --arg want "$want" '
      .services[0].deployments[]
      | select(.status == "PRIMARY")
      | ((.taskDefinition | endswith($want)) or .taskDefinition == $want)
        and .rolloutState == "COMPLETED"
        and .runningCount >= .desiredCount
        and .desiredCount > 0
    ' >/dev/null
}

ecs_update() {
  local logical="$1" taskdef="$2" desired="$3" ecs_name
  ecs_name="$(logical_ecs "$logical")"
  run_aws ecs update-service --cluster "$CLUSTER" --service "$ecs_name" \
    --task-definition "$taskdef" --desired-count "$desired" \
    --deployment-configuration 'deploymentCircuitBreaker={enable=true,rollback=false},maximumPercent=200,minimumHealthyPercent=100' \
    --output json >/dev/null
  wait_until "rollout $logical $taskdef" primary_rollout_ok "$ecs_name" "$taskdef"
}

ecs_set_desired() {
  local logical="$1" desired="$2" ecs_name
  ecs_name="$(logical_ecs "$logical")"
  run_aws ecs update-service --cluster "$CLUSTER" --service "$ecs_name" --desired-count "$desired" --output json >/dev/null
  if [[ "$desired" == "0" ]]; then
    run_aws ecs wait services-stable --cluster "$CLUSTER" --services "$ecs_name"
  fi
}

gateway_healthy() {
  run_aws elbv2 describe-target-health --target-group-arn "$TG_ARN" --output json \
    | jq -e '[.TargetHealthDescriptions[].TargetHealth.State] | any(. == "healthy")' >/dev/null
}

deploy_one() {
  local logical="$1" image ecs_name source rev
  image="$(jq -er --arg s "$logical" '.images[$s]' "$RELEASE")"
  ecs_name="$(logical_ecs "$logical")"
  source="$(service_taskdef "$ecs_name")"
  rev="$(register_digest "$source" "$image" "$logical" "$(jq -er '.config_sha' "$RELEASE")")"
  TOUCHED="$TOUCHED $logical"
  TASKDEFS_JSON="$(jq --arg k "$logical" --arg v "$rev" '. + {($k): $v}' <<<"$TASKDEFS_JSON")"
  autoscale_set "$logical" 1 "$(max_for "$logical")"
  ecs_update "$logical" "$rev" 1
  if [[ "$logical" == "gateway" ]]; then
    wait_until "gateway target healthy" gateway_healthy
  fi
}

migration_version() {
  jq -r '.migrations.version // empty' "$1"
}

compatible_auto_rollback() {
  local prev ver
  [[ -n "${CURRENT_JSON:-}" && -f "${CURRENT_JSON:-}" ]] || return 1
  prev="$(jq -r '.release.migrations.version // empty' "$CURRENT_JSON")"
  ver="$(migration_version "$RELEASE")"
  [[ -n "$prev" && -n "$ver" && "$prev" == "$ver" ]] || return 1
  [[ "$(jq -c '.environment // empty' "$CURRENT_JSON")" == "$(environment_fingerprint)" ]]
}

rollback_or_cleanup() {
  local logical prev_td prev_desired prev_ver ver failed=0
  if compatible_auto_rollback; then
    echo "Deploy failed. Restoring the last successful task definitions (same migrations.version)." >&2
    for logical in $APP_SERVICES; do
      prev_td="$(jq -er --arg s "$logical" '.task_definitions[$s]' "$CURRENT_JSON")" || { failed=1; continue; }
      prev_desired="$(jq -r --arg s "$logical" '.desired_counts[$s] // 1' "$CURRENT_JSON")"
      if ! ecs_update "$logical" "$prev_td" "$prev_desired"; then
        echo "Rollback failed for $logical." >&2
        failed=1
      fi
    done
    [[ "$failed" -eq 0 ]] || return 1
    return 0
  fi
  if [[ -n "${CURRENT_JSON:-}" && -f "${CURRENT_JSON:-}" ]]; then
    prev_ver="$(jq -r '.release.migrations.version // empty' "$CURRENT_JSON")"
    ver="$(migration_version "$RELEASE")"
    echo "Deploy failed after migrations.version $prev_ver -> $ver. Automatic app rollback is skipped. Recover previous task definitions manually." >&2
    return 1
  fi
  echo "Deployment failed and no previous cc-service release is available." >&2
  for logical in $TOUCHED; do
    [[ -n "$logical" ]] || continue
    if ! autoscale_set "$logical" 0 0 || ! ecs_set_desired "$logical" 0; then
      echo "Cleanup desired=0 failed for $logical." >&2
      failed=1
    fi
  done
  [[ "$failed" -eq 0 ]] || return 1
  return 1
}

publish_success() {
  local current_out desired_json release_id logical
  current_out="$(mktemp)"
  desired_json="$(jq -n '{}')"
  for logical in $APP_SERVICES; do
    desired_json="$(jq --arg k "$logical" '. + {($k): 1}' <<<"$desired_json")"
  done
  release_id="$(json_get '.release_id' "$RELEASE")"
  if [[ -n "${CURRENT_JSON:-}" && -f "${CURRENT_JSON:-}" ]]; then
    s3_put runtime/previous.json "$CURRENT_JSON" || fail "failed to write runtime/previous.json"
  fi
  jq -n --arg id "$release_id" --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --argjson release "$(jq -c . "$RELEASE")" --argjson task_definitions "$TASKDEFS_JSON" \
    --argjson desired_counts "$desired_json" \
    --argjson environment "$(environment_fingerprint)" \
    '{release_id:$id,at:$ts,release:$release,task_definitions:$task_definitions,desired_counts:$desired_counts,environment:$environment}' \
    > "$current_out"
  s3_put runtime/current.json "$current_out" || fail "failed to write runtime/current.json; deploy is not confirmed"
  rm -f "$current_out"
}


start_last_success() {
  local logical td desired image ecs_name source rev live saved
  live="$(environment_fingerprint)"
  saved="$(jq -c '.environment // empty' "$CURRENT_JSON")"
  if [[ -n "$saved" && "$saved" == "$live" ]]; then
    for logical in $APP_SERVICES; do
      td="$(jq -er --arg s "$logical" '.task_definitions[$s]' "$CURRENT_JSON")"
      desired="$(jq -r --arg s "$logical" '.desired_counts[$s] // 1' "$CURRENT_JSON")"
      autoscale_set "$logical" "$desired" "$(max_for "$logical")"
      ecs_update "$logical" "$td" "$desired"
    done
    echo "start reused last successful task definitions."
    return 0
  fi
  echo "Runtime endpoints changed. Rebinding last successful images onto current task definitions." >&2
  jq -e '.release.images' "$CURRENT_JSON" >/dev/null || fail "current.json has no release images to rebind; run deploy." 65
  for logical in $APP_SERVICES; do
    image="$(jq -er --arg s "$logical" '.release.images[$s]' "$CURRENT_JSON")"
    ecs_name="$(logical_ecs "$logical")"
    source="$(service_taskdef "$ecs_name")"
    rev="$(register_digest "$source" "$image" "$logical" "$(jq -er '.release.config_sha' "$CURRENT_JSON")")"
    desired="$(jq -r --arg s "$logical" '.desired_counts[$s] // 1' "$CURRENT_JSON")"
    autoscale_set "$logical" "$desired" "$(max_for "$logical")"
    ecs_update "$logical" "$rev" "$desired"
  done
}
