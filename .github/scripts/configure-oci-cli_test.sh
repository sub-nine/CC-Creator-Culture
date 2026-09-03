#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

file_mode() {
  local file="$1"
  if stat -c '%a' "$file" >/dev/null 2>&1; then
    stat -c '%a' "$file"
  else
    stat -f '%Lp' "$file"
  fi
}

encode_key() {
  printf '%s' "$1" | base64 | tr -d '\n'
}

run_configure() {
  local private_key="$1"
  HOME="$TEST_ROOT/home" \
  OCI_CLI_TENANCY=ocid1.tenancy.test \
  OCI_CLI_USER=ocid1.user.test \
  OCI_CLI_FINGERPRINT=00:11:22:33 \
  OCI_CLI_REGION=ap-tokyo-1 \
  OCI_PRIVATE_KEY_B64="$(encode_key "$private_key")" \
    bash "$SCRIPT_DIR/configure-oci-cli.sh"
}

private_key_without_label=$'-----BEGIN PRIVATE KEY-----\ntest-key\n-----END PRIVATE KEY-----\n'
run_configure "$private_key_without_label"

key_file="$TEST_ROOT/home/.oci/oci_api_key.pem"
config_file="$TEST_ROOT/home/.oci/config"
[[ "$(tail -n 1 "$key_file")" == "OCI_API_KEY" ]]
[[ "$(grep -c '^OCI_API_KEY$' "$key_file")" == "1" ]]
[[ "$(file_mode "$key_file")" == "600" ]]
[[ "$(file_mode "$config_file")" == "600" ]]

private_key_with_label=$'-----BEGIN PRIVATE KEY-----\ntest-key\n-----END PRIVATE KEY-----\nOCI_API_KEY\n'
run_configure "$private_key_with_label"
[[ "$(tail -n 1 "$key_file")" == "OCI_API_KEY" ]]
[[ "$(grep -c '^OCI_API_KEY$' "$key_file")" == "1" ]]

echo "OCI CLI key labeling tests passed."
