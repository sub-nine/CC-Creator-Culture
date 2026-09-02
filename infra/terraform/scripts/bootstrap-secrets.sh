#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "Usage: $0 <compartment-ocid> <vault-ocid> <key-ocid>" >&2
  exit 64
fi

COMPARTMENT_OCID="$1"
VAULT_OCID="$2"
KEY_OCID="$3"

create_secret() {
  local output_name="$1"
  local secret_name="$2"
  local existing_ocid value encoded

  existing_ocid="$(oci vault secret list \
    --compartment-id "$COMPARTMENT_OCID" \
    --vault-id "$VAULT_OCID" \
    --name "$secret_name" \
    --lifecycle-state ACTIVE \
    --all \
    --query 'data[0].id' \
    --raw-output)"

  if [[ -n "$existing_ocid" && "$existing_ocid" != "null" ]]; then
    printf '%s=%s\n' "$output_name" "$existing_ocid"
    return 0
  fi

  value="$(openssl rand -base64 36 | tr -d '\n')"
  encoded="$(printf '%s' "$value" | base64 | tr -d '\n')"
  printf '%s=%s\n' "$output_name" "$(oci vault secret create-base64 \
    --compartment-id "$COMPARTMENT_OCID" \
    --vault-id "$VAULT_OCID" \
    --key-id "$KEY_OCID" \
    --secret-name "$secret_name" \
    --secret-content-content "$encoded" \
    --query 'data.id' \
    --raw-output)"
}

create_secret USER_DB_ADMIN_PASSWORD_SECRET_OCID cc-dev-user-db-admin-password
create_secret PRODUCT_DB_ADMIN_PASSWORD_SECRET_OCID cc-dev-product-db-admin-password
create_secret ORDER_DB_ADMIN_PASSWORD_SECRET_OCID cc-dev-order-db-admin-password
create_secret USER_DB_PASSWORD_SECRET_OCID cc-dev-user-db-password
create_secret PRODUCT_DB_PASSWORD_SECRET_OCID cc-dev-product-db-password
create_secret ORDER_DB_PASSWORD_SECRET_OCID cc-dev-order-db-password
create_secret GRAFANA_ADMIN_PASSWORD_SECRET_OCID cc-dev-grafana-admin-password
