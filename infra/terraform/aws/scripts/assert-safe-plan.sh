#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 <terraform-plan.json>" >&2
  exit 64
fi

PLAN_JSON="$1"

if ! jq -e '
  type == "object"
  and (.format_version | type == "string" and length > 0)
  and (.resource_changes | type == "array")
' "$PLAN_JSON" >/dev/null; then
  echo "Terraform plan JSON must include format_version and resource_changes." >&2
  exit 1
fi

blocked_addresses="$(jq -r '
  def has_action($action): (.change.actions // []) | index($action) != null;
  def runtime_dns:
    .address == "cloudflare_dns_record.api"
    or (.address | startswith("cloudflare_dns_record.acm_validation"));
  def protected:
    .type == "aws_s3_bucket" or
    .type == "aws_s3_object" or
    .type == "aws_ecr_repository" or
    .type == "aws_iam_openid_connect_provider" or
    .type == "aws_db_instance" or
    .type == "aws_ebs_volume" or
    .type == "aws_vpc" or
    .type == "aws_kms_key" or
    .type == "aws_instance" or
    .type == "aws_secretsmanager_secret" or
    (.type == "cloudflare_dns_record" and (runtime_dns | not));

  .resource_changes[]
  | select(protected and (has_action("delete")))
  | .address
' "$PLAN_JSON")"

if [[ -n "$blocked_addresses" ]]; then
  echo "Unsafe Terraform actions detected:" >&2
  echo "$blocked_addresses" >&2
  exit 1
fi

echo "Terraform plan passed protected-resource checks."
