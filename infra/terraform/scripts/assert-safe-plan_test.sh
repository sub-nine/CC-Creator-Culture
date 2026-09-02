#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSERT_SCRIPT="$SCRIPT_DIR/assert-safe-plan.sh"
TEST_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

jq -n '{
  resource_changes: [
    {address: "oci_artifacts_container_repository.service", type: "oci_artifacts_container_repository", change: {actions: ["create"]}},
    {address: "data.oci_core_instance.existing", type: "oci_core_instance", mode: "data", change: {actions: ["read"]}}
  ]
}' > "$TEST_ROOT/allowed.json"

bash "$ASSERT_SCRIPT" "$TEST_ROOT/allowed.json" >/dev/null

jq -n '{
  resource_changes: [
    {address: "oci_core_instance.forbidden", type: "oci_core_instance", change: {actions: ["create"]}}
  ]
}' > "$TEST_ROOT/instance-create.json"

if bash "$ASSERT_SCRIPT" "$TEST_ROOT/instance-create.json" >/dev/null 2>&1; then
  echo "Expected instance creation to be blocked." >&2
  exit 1
fi

jq -n '{
  resource_changes: [
    {address: "oci_core_public_ip.dev", type: "oci_core_public_ip", change: {actions: ["delete", "create"]}}
  ]
}' > "$TEST_ROOT/public-ip-replace.json"

if bash "$ASSERT_SCRIPT" "$TEST_ROOT/public-ip-replace.json" >/dev/null 2>&1; then
  echo "Expected public IP replacement to be blocked." >&2
  exit 1
fi

echo "assert-safe-plan.sh regression tests passed."
