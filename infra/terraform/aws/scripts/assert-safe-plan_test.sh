#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSERT_SCRIPT="$SCRIPT_DIR/assert-safe-plan.sh"
TEST_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

echo '{}' > "$TEST_ROOT/empty.json"
if bash "$ASSERT_SCRIPT" "$TEST_ROOT/empty.json" >/dev/null 2>&1; then
  echo "Expected empty object to be rejected." >&2
  exit 1
fi

jq -n '{format_version: "1.2"}' > "$TEST_ROOT/missing-changes.json"
if bash "$ASSERT_SCRIPT" "$TEST_ROOT/missing-changes.json" >/dev/null 2>&1; then
  echo "Expected missing resource_changes to be rejected." >&2
  exit 1
fi

jq -n '{format_version: "1.2", resource_changes: []}' > "$TEST_ROOT/empty-plan.json"
bash "$ASSERT_SCRIPT" "$TEST_ROOT/empty-plan.json" >/dev/null

jq -n --arg addr 'aws_ecr_repository.service["gateway"]' '{
  format_version: "1.2",
  resource_changes: [
    {address: "aws_iam_role.plan_read", type: "aws_iam_role", change: {actions: ["create"]}},
    {address: $addr, type: "aws_ecr_repository", change: {actions: ["create"]}}
  ]
}' > "$TEST_ROOT/allowed.json"
bash "$ASSERT_SCRIPT" "$TEST_ROOT/allowed.json" >/dev/null

jq -n '{
  format_version: "1.2",
  resource_changes: [
    {address: "aws_s3_bucket.state", type: "aws_s3_bucket", change: {actions: ["delete"]}}
  ]
}' > "$TEST_ROOT/state-delete.json"
if bash "$ASSERT_SCRIPT" "$TEST_ROOT/state-delete.json" >/dev/null 2>&1; then
  echo "Expected state bucket deletion to be blocked." >&2
  exit 1
fi

jq -n '{
  format_version: "1.2",
  resource_changes: [
    {address: "aws_instance.observation", type: "aws_instance", change: {actions: ["delete"]}}
  ]
}' > "$TEST_ROOT/ec2-delete.json"
if bash "$ASSERT_SCRIPT" "$TEST_ROOT/ec2-delete.json" >/dev/null 2>&1; then
  echo "Expected EC2 deletion to be blocked." >&2
  exit 1
fi

jq -n '{
  format_version: "1.2",
  resource_changes: [
    {address: "aws_secretsmanager_secret.jwt", type: "aws_secretsmanager_secret", change: {actions: ["delete"]}}
  ]
}' > "$TEST_ROOT/secret-delete.json"
if bash "$ASSERT_SCRIPT" "$TEST_ROOT/secret-delete.json" >/dev/null 2>&1; then
  echo "Expected secret deletion to be blocked." >&2
  exit 1
fi

jq -n --arg addr 'aws_db_instance.service["user-service"]' '{
  format_version: "1.2",
  resource_changes: [
    {address: $addr, type: "aws_db_instance", change: {actions: ["delete"]}}
  ]
}' > "$TEST_ROOT/rds-delete.json"
if bash "$ASSERT_SCRIPT" "$TEST_ROOT/rds-delete.json" >/dev/null 2>&1; then
  echo "Expected RDS deletion to be blocked." >&2
  exit 1
fi

jq -n '{
  format_version: "1.2",
  resource_changes: [
    {address: "cloudflare_dns_record.api", type: "cloudflare_dns_record", change: {actions: ["delete"]}}
  ]
}' > "$TEST_ROOT/api-dns-delete.json"
bash "$ASSERT_SCRIPT" "$TEST_ROOT/api-dns-delete.json" >/dev/null

jq -n '{
  format_version: "1.2",
  resource_changes: [
    {address: "cloudflare_dns_record.dev", type: "cloudflare_dns_record", change: {actions: ["delete"]}}
  ]
}' > "$TEST_ROOT/dev-dns-delete.json"
if bash "$ASSERT_SCRIPT" "$TEST_ROOT/dev-dns-delete.json" >/dev/null 2>&1; then
  echo "Expected non-api DNS deletion to be blocked." >&2
  exit 1
fi

echo "assert-safe-plan.sh regression tests passed."
