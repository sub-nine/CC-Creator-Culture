#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 --sha <git-sha> --registry <ecr-registry> --output <db-artifacts.json> [--repository-prefix <prefix/>] [--context <dir>]" >&2
  exit 64
}

SHA=""
REGISTRY=""
OUTPUT=""
PREFIX="${AWS_ECR_REPOSITORY_PREFIX:-cc-test/}"
CONTEXT=""
SKOPEO_BIN="${SKOPEO:-skopeo}"
DOCKER_BIN="${DOCKER:-docker}"

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --sha) SHA="$2"; shift 2 ;;
    --registry) REGISTRY="$2"; shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    --repository-prefix) PREFIX="$2"; shift 2 ;;
    --context) CONTEXT="$2"; shift 2 ;;
    *) usage ;;
  esac
done

[[ -n "$SHA" && -n "$REGISTRY" && -n "$OUTPUT" ]] || usage
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "sha must be a full lowercase Git SHA." >&2; exit 65; }
[[ "$REGISTRY" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com$ ]] || {
  echo "ECR registry must be a canonical account.dkr.ecr.region.amazonaws.com host." >&2
  exit 65
}
[[ "$PREFIX" =~ ^[a-z0-9._-]+(/[a-z0-9._-]+)*/$ ]] || {
  echo "ECR repository prefix must be lowercase path segments with a trailing slash." >&2
  exit 65
}
if [[ -z "$CONTEXT" ]]; then
  CONTEXT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../deploy/aws" && pwd)"
fi
[[ -d "$CONTEXT" ]] || { echo "build context is missing: $CONTEXT" >&2; exit 65; }
command -v "$SKOPEO_BIN" >/dev/null || { echo "skopeo is required." >&2; exit 69; }
command -v "$DOCKER_BIN" >/dev/null || { echo "docker is required." >&2; exit 69; }

fail() { echo "$1" >&2; exit 65; }

digest_from_raw_file() {
  local raw_file="$1" digest=""
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

inspect_top_digest() {
  local ref="$1" raw_file err_file status digest err
  raw_file="$(mktemp)"
  err_file="$(mktemp)"
  set +e
  "$SKOPEO_BIN" inspect --raw "docker://${ref}" >"$raw_file" 2>"$err_file"
  status=$?
  set -e
  if [[ "$status" -eq 0 ]]; then
    digest="$(digest_from_raw_file "$raw_file")" || {
      rm -f "$raw_file" "$err_file"
      fail "Unable to compute top-level digest for ${ref}."
    }
    rm -f "$raw_file" "$err_file"
    printf '%s' "$digest"
    return 0
  fi
  err="$(cat "$err_file")"
  rm -f "$raw_file" "$err_file"
  if is_missing_image_error "$err"; then
    return 0
  fi
  echo "Unable to inspect docker://${ref}:" >&2
  echo "$err" >&2
  return 1
}

revision_of() {
  local ref="$1" err_file status out
  err_file="$(mktemp)"
  set +e
  out="$("$SKOPEO_BIN" inspect --override-os linux --override-arch arm64 "docker://${ref}" 2>"$err_file")"
  status=$?
  set -e
  if [[ "$status" -ne 0 ]]; then
    echo "Unable to read revision for ${ref}:" >&2
    cat "$err_file" >&2
    rm -f "$err_file"
    return 1
  fi
  rm -f "$err_file"
  jq -er '.Labels["org.opencontainers.image.revision"] // empty' <<<"$out"
}

publish_one() {
  local name="$1" dockerfile="$2"
  local dest="${REGISTRY}/${PREFIX}${name}:${SHA}"
  local existing revision local_tag digest
  [[ -f "$CONTEXT/$dockerfile" ]] || fail "Dockerfile missing: $CONTEXT/$dockerfile"
  existing="$(inspect_top_digest "$dest")" || return 1
  if [[ -n "$existing" ]]; then
    revision="$(revision_of "${REGISTRY}/${PREFIX}${name}@${existing}")" || return 1
    [[ "$revision" == "$SHA" ]] || fail "Immutable tag $dest revision is ${revision:-missing}, not $SHA."
    echo "No-op: $dest already has $existing for $SHA" >&2
    printf '%s' "${REGISTRY}/${PREFIX}${name}@${existing}"
    return 0
  fi
  local_tag="cc-${name}:${SHA}"
  "$DOCKER_BIN" buildx build --platform linux/arm64 --load \
    -f "$CONTEXT/$dockerfile" \
    --build-arg RELEASE_SHA="$SHA" \
    -t "$local_tag" \
    "$CONTEXT" >&2
  "$DOCKER_BIN" tag "$local_tag" "$dest" >&2
  "$DOCKER_BIN" push "$dest" >&2
  digest="$(inspect_top_digest "$dest")" || return 1
  [[ -n "$digest" ]] || fail "Pushed $dest but digest is missing."
  revision="$(revision_of "${REGISTRY}/${PREFIX}${name}@${digest}")" || return 1
  [[ "$revision" == "$SHA" ]] || fail "Pushed $dest revision is ${revision:-missing}, not $SHA."
  echo "Published ${REGISTRY}/${PREFIX}${name}@${digest}" >&2
  printf '%s' "${REGISTRY}/${PREFIX}${name}@${digest}"
}

migrate_ref="$(publish_one db-migrate database/Dockerfile)"
seed_ref="$(publish_one db-seed seed/Dockerfile)"

jq -n --arg source_sha "$SHA" --arg config_sha "$SHA" --arg migrate "$migrate_ref" --arg seed "$seed_ref" \
  '{source_sha: $source_sha, config_sha: $config_sha, images: {"db-migrate": $migrate, "db-seed": $seed}}' > "$OUTPUT"
