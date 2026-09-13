#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 --candidate <candidate.json> --registry <ecr-registry> --output <ecr-images.json> [--repository-prefix <prefix/>]" >&2
  exit 64
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CANDIDATE=""
REGISTRY=""
OUTPUT=""
PREFIX="${AWS_ECR_REPOSITORY_PREFIX:-cc-test/}"
SKOPEO_BIN="${SKOPEO:-skopeo}"

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --candidate) CANDIDATE="$2"; shift 2 ;;
    --registry) REGISTRY="$2"; shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    --repository-prefix) PREFIX="$2"; shift 2 ;;
    *) usage ;;
  esac
done

[[ -n "$CANDIDATE" && -n "$REGISTRY" && -n "$OUTPUT" ]] || usage
[[ -f "$CANDIDATE" ]] || usage
command -v "$SKOPEO_BIN" >/dev/null || {
  echo "skopeo is required to copy images without rebuilding." >&2
  exit 69
}

[[ "$REGISTRY" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com$ ]] || {
  echo "ECR registry must be a canonical account.dkr.ecr.region.amazonaws.com host." >&2
  exit 65
}
[[ "$PREFIX" =~ ^[a-z0-9._-]+(/[a-z0-9._-]+)*/$ ]] || {
  echo "ECR repository prefix must be lowercase path segments with a trailing slash." >&2
  exit 65
}

bash "$SCRIPT_DIR/validate-candidate-manifest.sh" "$CANDIDATE"

fail() {
  echo "$1" >&2
  exit 65
}

digest_from_raw_file() {
  local raw_file="$1"
  local digest=""
  digest="$("$SKOPEO_BIN" manifest-digest "$raw_file" 2>/dev/null || true)"
  if [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    printf '%s' "$digest"
    return 0
  fi
  if command -v sha256sum >/dev/null; then
    digest="$(sha256sum "$raw_file" | awk '{print $1}')"
  else
    digest="$(shasum -a 256 "$raw_file" | awk '{print $1}')"
  fi
  [[ "$digest" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf 'sha256:%s' "$digest"
}

is_missing_image_error() {
  local err="$1"
  if grep -Eiq 'unauthorized|denied|forbidden|authentication required|access denied|permission|connection refused|i/o timeout|timed out|temporary failure|network is unreachable|no such host|tls handshake|certificate|too many requests|throttl|reset by peer' <<<"$err"; then
    return 1
  fi
  grep -Eiq 'manifest unknown|name unknown|requested image not found|repository .+ does not exist|no such manifest' <<<"$err"
}

# Top-level manifest digest only. Never use host-arch inspect.
inspect_top_digest() {
  local ref="$1"
  local raw_file err_file status
  raw_file="$(mktemp)"
  err_file="$(mktemp)"
  set +e
  "$SKOPEO_BIN" inspect --raw "docker://${ref}" >"$raw_file" 2>"$err_file"
  status=$?
  set -e
  if [[ "$status" -eq 0 ]]; then
    local digest
    digest="$(digest_from_raw_file "$raw_file")" || {
      rm -f "$raw_file" "$err_file"
      fail "Unable to compute top-level digest for ${ref}."
    }
    rm -f "$raw_file" "$err_file"
    printf '%s' "$digest"
    return 0
  fi
  local err
  err="$(cat "$err_file")"
  rm -f "$raw_file" "$err_file"
  if is_missing_image_error "$err"; then
    return 0
  fi
  echo "Unable to inspect docker://${ref}:" >&2
  echo "$err" >&2
  return 1
}

SHA="$(jq -er '.commit_sha' "$CANDIDATE")"
services=(config-server eureka-server gateway order-service product-service user-service)
images_json='{}'

for service in "${services[@]}"; do
  src="$(jq -er --arg service "$service" '.images[$service]' "$CANDIDATE")"
  want="${src##*@}"
  [[ "$want" =~ ^sha256:[0-9a-f]{64}$ ]] || fail "Source image for $service is not an immutable digest."
  dest="${REGISTRY}/${PREFIX}${service}:${SHA}"
  existing="$(inspect_top_digest "$dest")" || exit 1
  if [[ -n "$existing" ]]; then
    [[ "$existing" == "$want" ]] || fail "Immutable tag $dest already has $existing, not $want."
    echo "No-op: $dest already has $want"
  else
    "$SKOPEO_BIN" copy --all --preserve-digests "docker://${src}" "docker://${dest}"
    got="$(inspect_top_digest "$dest")" || exit 1
    [[ "$got" == "$want" ]] || fail "Digest-preserving copy failed for $service (source $want, destination ${got:-missing})."
  fi
  canonical="${REGISTRY}/${PREFIX}${service}@${want}"
  images_json="$(jq -c --arg service "$service" --arg ref "$canonical" '. + {($service): $ref}' <<<"$images_json")"
done

jq -n --arg source_sha "$SHA" --arg config_sha "$SHA" --argjson images "$images_json" \
  '{source_sha: $source_sha, config_sha: $config_sha, images: $images}' > "$OUTPUT"

