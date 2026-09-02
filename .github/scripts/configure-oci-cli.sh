#!/usr/bin/env bash
set -Eeuo pipefail

: "${OCI_CLI_TENANCY:?OCI_CLI_TENANCY is required}"
: "${OCI_CLI_USER:?OCI_CLI_USER is required}"
: "${OCI_CLI_FINGERPRINT:?OCI_CLI_FINGERPRINT is required}"
: "${OCI_CLI_REGION:?OCI_CLI_REGION is required}"
: "${OCI_PRIVATE_KEY_B64:?OCI_PRIVATE_KEY_B64 is required}"

OCI_CONFIG_DIR="$HOME/.oci"
install -d -m 0700 "$OCI_CONFIG_DIR"
printf '%s' "$OCI_PRIVATE_KEY_B64" | base64 --decode > "$OCI_CONFIG_DIR/oci_api_key.pem"
chmod 0600 "$OCI_CONFIG_DIR/oci_api_key.pem"
printf '%s\n' \
  '[DEFAULT]' \
  "user=${OCI_CLI_USER}" \
  "fingerprint=${OCI_CLI_FINGERPRINT}" \
  "tenancy=${OCI_CLI_TENANCY}" \
  "region=${OCI_CLI_REGION}" \
  "key_file=${OCI_CONFIG_DIR}/oci_api_key.pem" \
  > "$OCI_CONFIG_DIR/config"
chmod 0600 "$OCI_CONFIG_DIR/config"
