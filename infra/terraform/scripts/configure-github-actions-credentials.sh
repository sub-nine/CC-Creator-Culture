#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 4 ]]; then
  echo "Usage: $0 <github-repository> <ci-user-ocid> <cc-compartment-ocid> <ocir-namespace>" >&2
  exit 64
fi

GITHUB_REPOSITORY="$1"
CI_USER_OCID="$2"
CC_COMPARTMENT_OCID="$3"
OCIR_NAMESPACE="$4"
OCI_REGION="${OCI_REGION:-ap-tokyo-1}"
OCIR_REGISTRY="${OCIR_REGISTRY:-nrt.ocir.io}"
AUTH_TOKEN_DESCRIPTION="cc-service GitHub Actions OCIR"

for command in base64 docker gh jq oci openssl; do
  command -v "$command" >/dev/null || {
    echo "$command is required." >&2
    exit 69
  }
done

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LOCAL_SETUP_DIR="$REPOSITORY_ROOT/deploy/local-setup"
PRIVATE_KEY_FILE="$LOCAL_SETUP_DIR/github-actions-oci-api-key.pem"
PUBLIC_KEY_FILE="$LOCAL_SETUP_DIR/github-actions-oci-api-key-public.pem"
TEMP_ROOT="$(mktemp -d)"
TEMP_OCI_CONFIG="$TEMP_ROOT/oci-config"
TEMP_DOCKER_CONFIG="$TEMP_ROOT/docker"
AUTH_TOKEN=""
AUTH_TOKEN_ID=""

cleanup() {
  unset AUTH_TOKEN
  rm -rf "$TEMP_ROOT"
}
trap cleanup EXIT

umask 077
install -d -m 0700 "$LOCAL_SETUP_DIR" "$TEMP_DOCKER_CONFIG"

oci iam user update-user-capabilities \
  --user-id "$CI_USER_OCID" \
  --can-use-api-keys true \
  --can-use-auth-tokens true \
  --can-use-console-password false \
  --can-use-smtp-credentials false \
  --can-use-db-credentials false \
  --can-use-customer-secret-keys false \
  --can-use-o-auth2-client-credentials false >/dev/null

if [[ ! -f "$PRIVATE_KEY_FILE" ]]; then
  openssl genrsa -out "$PRIVATE_KEY_FILE" 2048 >/dev/null 2>&1
fi
chmod 0600 "$PRIVATE_KEY_FILE"
openssl rsa -in "$PRIVATE_KEY_FILE" -pubout -out "$PUBLIC_KEY_FILE" >/dev/null 2>&1
chmod 0600 "$PUBLIC_KEY_FILE"

FINGERPRINT="$(
  openssl rsa -in "$PRIVATE_KEY_FILE" -pubout -outform DER 2>/dev/null \
    | openssl dgst -md5 -c \
    | awk '{print $2}'
)"
[[ "$FINGERPRINT" =~ ^([0-9a-f]{2}:){15}[0-9a-f]{2}$ ]] || {
  echo "Unable to derive OCI API key fingerprint." >&2
  exit 65
}

API_KEYS="$(oci iam user api-key list --user-id "$CI_USER_OCID" --all --output json)"
if ! jq -e --arg fingerprint "$FINGERPRINT" '.data | any(.fingerprint == $fingerprint)' <<<"$API_KEYS" >/dev/null; then
  UPLOAD_FINGERPRINT="$(
    oci iam user api-key upload \
      --user-id "$CI_USER_OCID" \
      --key-file "$PUBLIC_KEY_FILE" \
      --query 'data.fingerprint' \
      --raw-output
  )"
  [[ "$UPLOAD_FINGERPRINT" == "$FINGERPRINT" ]] || {
    echo "Uploaded OCI API key fingerprint did not match the local key." >&2
    exit 65
  }
fi

CI_USER="$(oci iam user get --user-id "$CI_USER_OCID" --output json)"
CI_USER_NAME="$(jq -er '.data.name' <<<"$CI_USER")"
CI_USER_EMAIL="$(jq -er '.data.email' <<<"$CI_USER")"
TENANCY_OCID="$(jq -er '.data."compartment-id"' <<<"$CI_USER")"
IDENTITY_DOMAINS="$(oci iam domain list --compartment-id "$TENANCY_OCID" --all --output json)"
IDENTITY_DOMAIN_NAME="$(jq -er '[.data[] | select(."lifecycle-state" == "ACTIVE")][0]."display-name"' <<<"$IDENTITY_DOMAINS")"
OCIR_USERNAME="$OCIR_NAMESPACE/$IDENTITY_DOMAIN_NAME/$CI_USER_EMAIL"

cat > "$TEMP_OCI_CONFIG" <<EOF
[DEFAULT]
user=$CI_USER_OCID
fingerprint=$FINGERPRINT
tenancy=$TENANCY_OCID
region=$OCI_REGION
key_file=$PRIVATE_KEY_FILE
EOF
chmod 0600 "$TEMP_OCI_CONFIG"

for _ in $(seq 1 12); do
  if SUPPRESS_LABEL_WARNING=True oci \
    --config-file "$TEMP_OCI_CONFIG" \
    artifacts container repository list \
    --compartment-id "$CC_COMPARTMENT_OCID" \
    --all >/dev/null 2>&1 \
    && SUPPRESS_LABEL_WARNING=True oci \
      --config-file "$TEMP_OCI_CONFIG" \
      os bucket list \
      --compartment-id "$CC_COMPARTMENT_OCID" \
      --namespace-name "$OCIR_NAMESPACE" \
      --all >/dev/null 2>&1; then
    break
  fi
  sleep 5
done

SUPPRESS_LABEL_WARNING=True oci \
  --config-file "$TEMP_OCI_CONFIG" \
  artifacts container repository list \
  --compartment-id "$CC_COMPARTMENT_OCID" \
  --all >/dev/null
SUPPRESS_LABEL_WARNING=True oci \
  --config-file "$TEMP_OCI_CONFIG" \
  os bucket list \
  --compartment-id "$CC_COMPARTMENT_OCID" \
  --namespace-name "$OCIR_NAMESPACE" \
  --all >/dev/null

GITHUB_SECRET_NAMES="$(gh secret list --repo "$GITHUB_REPOSITORY" --json name)"
if ! jq -e 'any(.name == "OCIR_AUTH_TOKEN")' <<<"$GITHUB_SECRET_NAMES" >/dev/null; then
  EXISTING_TOKEN_COUNT="$(
    oci iam auth-token list \
      --user-id "$CI_USER_OCID" \
      --all \
      --output json \
      | jq --arg description "$AUTH_TOKEN_DESCRIPTION" '[.data[] | select(.description == $description)] | length'
  )"
  [[ "$EXISTING_TOKEN_COUNT" -eq 0 ]] || {
    echo "An OCI auth token already exists for this workflow, but GitHub has no OCIR_AUTH_TOKEN Secret. Rotate it manually before retrying." >&2
    exit 65
  }

  AUTH_TOKEN_RESPONSE="$(
    oci iam auth-token create \
      --user-id "$CI_USER_OCID" \
      --description "$AUTH_TOKEN_DESCRIPTION" \
      --output json
  )"
  AUTH_TOKEN="$(jq -er '.data.token' <<<"$AUTH_TOKEN_RESPONSE")"
  AUTH_TOKEN_ID="$(jq -er '.data.id' <<<"$AUTH_TOKEN_RESPONSE")"
  [[ -n "$AUTH_TOKEN" && "$AUTH_TOKEN" != "null" ]]

  login_succeeded=false
  for attempt in $(seq 1 36); do
    for candidate in \
      "$OCIR_NAMESPACE/$IDENTITY_DOMAIN_NAME/$CI_USER_EMAIL" \
      "$OCIR_NAMESPACE/$CI_USER_EMAIL" \
      "$OCIR_NAMESPACE/$IDENTITY_DOMAIN_NAME/$CI_USER_NAME" \
      "$OCIR_NAMESPACE/$CI_USER_NAME"; do
      if printf '%s' "$AUTH_TOKEN" \
        | DOCKER_CONFIG="$TEMP_DOCKER_CONFIG" docker login \
          "$OCIR_REGISTRY" \
          --username "$candidate" \
          --password-stdin >/dev/null 2>&1; then
        OCIR_USERNAME="$candidate"
        login_succeeded=true
        break 2
      fi
    done
    if (( attempt % 6 == 0 )); then
      echo "Waiting for OCI Auth Token propagation: $((attempt * 5)) seconds"
    fi
    sleep 5
  done

  if [[ "$login_succeeded" != "true" ]]; then
    oci iam auth-token delete \
      --user-id "$CI_USER_OCID" \
      --auth-token-id "$AUTH_TOKEN_ID" \
      --force >/dev/null
    AUTH_TOKEN=""
    AUTH_TOKEN_ID=""
    echo "OCIR login failed. The generated auth token was deleted." >&2
    exit 1
  fi

  printf '%s' "$AUTH_TOKEN" | gh secret set OCIR_AUTH_TOKEN --repo "$GITHUB_REPOSITORY"
fi

printf '%s' "$TENANCY_OCID" | gh secret set OCI_CLI_TENANCY --repo "$GITHUB_REPOSITORY"
printf '%s' "$CI_USER_OCID" | gh secret set OCI_CLI_USER --repo "$GITHUB_REPOSITORY"
printf '%s' "$FINGERPRINT" | gh secret set OCI_CLI_FINGERPRINT --repo "$GITHUB_REPOSITORY"
base64 < "$PRIVATE_KEY_FILE" | tr -d '\n' | gh secret set OCI_PRIVATE_KEY_B64 --repo "$GITHUB_REPOSITORY"
printf '%s' "$OCIR_USERNAME" | gh secret set OCIR_USERNAME --repo "$GITHUB_REPOSITORY"

echo "Configured dedicated cc-service GitHub Actions OCI credentials without exposing credential values."
