#!/usr/bin/env bash
# Observation EC2 SSM bootstrap. Sourced by envctl.sh.

observation_document() {
  jq -r '.observation_bootstrap_document.name // empty' "$CONFIG"
}

observation_document_version() {
  jq -r '.observation_bootstrap_document.version // empty' "$CONFIG"
}

ssm_online() {
  run_aws ssm describe-instance-information --filters "Key=InstanceIds,Values=${OBS_ID}" --output json \
    | jq -e '.InstanceInformationList[0].PingStatus == "Online"' >/dev/null
}

ssm_command_ok() {
  run_aws ssm get-command-invocation --command-id "$1" --instance-id "$OBS_ID" --output json \
    | jq -e '.Status == "Success"' >/dev/null
}

run_observation_bootstrap() {
  local doc version cmd_id
  ec2_ensure_running
  doc="$(observation_document)"
  if [[ -z "$doc" ]]; then
    fail "observation_bootstrap_document is required before start/deploy." 65
  fi
  wait_until "ssm online $OBS_ID" ssm_online
  version="$(observation_document_version)"
  if [[ -n "$version" ]]; then
    cmd_id="$(run_aws ssm send-command --instance-ids "$OBS_ID" --document-name "$doc" --document-version "$version" --output json | jq -er '.Command.CommandId')"
  else
    cmd_id="$(run_aws ssm send-command --instance-ids "$OBS_ID" --document-name "$doc" --output json | jq -er '.Command.CommandId')"
  fi
  wait_until "ssm command $cmd_id" ssm_command_ok "$cmd_id"
}
