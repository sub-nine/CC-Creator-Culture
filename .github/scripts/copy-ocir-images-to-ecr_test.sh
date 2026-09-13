#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/copy-ocir-images-to-ecr.sh"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
SKOPEO_LOG="$TEST_ROOT/skopeo.log"
SKOPEO_STATE="$TEST_ROOT/skopeo-state"
cleanup() { rm -rf "$TEST_ROOT"; }
trap cleanup EXIT
mkdir -p "$FAKE_BIN" "$SKOPEO_STATE"

SHA="0123456789abcdef0123456789abcdef01234567"
DIGEST_A="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
DIGEST_B="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
DIGEST_C="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
DIGEST_D="sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
DIGEST_E="sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
DIGEST_F="sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
REGISTRY="123456789012.dkr.ecr.ap-northeast-2.amazonaws.com"

jq -n \
  --arg sha "$SHA" \
  --arg a "$DIGEST_A" --arg b "$DIGEST_B" --arg c "$DIGEST_C" \
  --arg d "$DIGEST_D" --arg e "$DIGEST_E" --arg f "$DIGEST_F" \
  '{
    commit_sha: $sha,
    config_sha: $sha,
    ci_url: "https://github.com/example/cc-service/actions/runs/1",
    images: {
      "config-server": ("nrt.ocir.io/ns/cc-dev/config-server@" + $a),
      "eureka-server": ("nrt.ocir.io/ns/cc-dev/eureka-server@" + $b),
      "gateway": ("nrt.ocir.io/ns/cc-dev/gateway@" + $c),
      "order-service": ("nrt.ocir.io/ns/cc-dev/order-service@" + $d),
      "product-service": ("nrt.ocir.io/ns/cc-dev/product-service@" + $e),
      "user-service": ("nrt.ocir.io/ns/cc-dev/user-service@" + $f)
    }
  }' > "$TEST_ROOT/candidate.json"

cat > "$FAKE_BIN/skopeo" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$SKOPEO_LOG"
if [[ "$1" == "copy" ]]; then
  printf '%s\n' "$*" | grep -Fq -- "--all" || exit 1
  printf '%s\n' "$*" | grep -Fq -- "--preserve-digests" || exit 1
  src=""
  dest=""
  for arg in "$@"; do
    if [[ "$arg" == docker://* ]]; then
      if [[ -z "$src" ]]; then src="$arg"; else dest="$arg"; fi
    fi
  done
  digest="${src##*@}"
  key="$(printf '%s' "$dest" | sed 's|docker://||' | tr '/' '_')"
  printf '%s\n' "$digest" > "$SKOPEO_STATE/$key"
  echo "copied $dest" >> "$SKOPEO_LOG.copy"
  exit 0
fi
if [[ "$1" == "inspect" ]]; then
  printf '%s\n' "$*" | grep -Fq -- "--raw" || {
    echo "host-arch inspect is forbidden" >&2
    exit 1
  }
  ref=""
  for arg in "$@"; do
    if [[ "$arg" == docker://* ]]; then ref="$arg"; fi
  done
  if [[ "${TEST_INSPECT_AUTH:-false}" == "true" ]]; then
    echo "unauthorized: authentication required" >&2
    exit 1
  fi
  if [[ "${TEST_INSPECT_NETWORK:-false}" == "true" ]]; then
    echo "connection refused" >&2
    exit 1
  fi
  key="$(printf '%s' "$ref" | sed 's|docker://||' | tr '/' '_')"
  if [[ "${TEST_DIGEST_MISMATCH:-false}" == "true" && -f "$SKOPEO_STATE/$key" ]]; then
    printf 'sha256:%s\n' "0000000000000000000000000000000000000000000000000000000000000000"
    exit 0
  fi
  if [[ ! -f "$SKOPEO_STATE/$key" ]]; then
    echo "manifest unknown: Requested image not found" >&2
    exit 1
  fi
  cat "$SKOPEO_STATE/$key"
  exit 0
fi
if [[ "$1" == "manifest-digest" ]]; then
  file="$2"
  value="$(tr -d '\n' < "$file")"
  if [[ "$value" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    printf '%s\n' "$value"
    exit 0
  fi
  echo "unexpected raw manifest" >&2
  exit 1
fi
echo "unexpected skopeo command: $*" >&2
exit 1
EOF
chmod +x "$FAKE_BIN/skopeo"

run_copy() {
  SKOPEO="$FAKE_BIN/skopeo" \
  SKOPEO_LOG="$SKOPEO_LOG" \
  SKOPEO_STATE="$SKOPEO_STATE" \
  TEST_DIGEST_MISMATCH="${TEST_DIGEST_MISMATCH:-false}" \
  TEST_INSPECT_AUTH="${TEST_INSPECT_AUTH:-false}" \
  TEST_INSPECT_NETWORK="${TEST_INSPECT_NETWORK:-false}" \
    bash "$SCRIPT" --candidate "$TEST_ROOT/candidate.json" --registry "$REGISTRY" \
      --repository-prefix cc-test/ --output "$TEST_ROOT/ecr-images.json"
}

run_copy
grep -Fq -- "copy --all --preserve-digests" "$SKOPEO_LOG"
if grep -E ' inspect ' "$SKOPEO_LOG" | grep -v -- '--raw' | grep -q .; then
  echo "skopeo inspect ran without --raw" >&2
  exit 1
fi
[[ "$(wc -l < "$SKOPEO_LOG.copy")" -eq 6 ]]
[[ "$(jq -r '.images["user-service"]' "$TEST_ROOT/ecr-images.json")" == "$REGISTRY/cc-test/user-service@$DIGEST_F" ]]

run_copy >"$TEST_ROOT/noop.out"
[[ "$(wc -l < "$SKOPEO_LOG.copy")" -eq 6 ]]
grep -Fq "No-op:" "$TEST_ROOT/noop.out"
[[ "$(jq -r '.images["user-service"]' "$TEST_ROOT/ecr-images.json")" == "$REGISTRY/cc-test/user-service@$DIGEST_F" ]]

printf '%s\n' "sha256:0000000000000000000000000000000000000000000000000000000000000000" \
  > "$SKOPEO_STATE/${REGISTRY}_cc-test_user-service:${SHA}"
if run_copy >"$TEST_ROOT/conflict.out" 2>"$TEST_ROOT/conflict.err"; then
  echo "Expected immutable tag conflict to fail." >&2
  exit 1
fi
grep -Fq "already has" "$TEST_ROOT/conflict.err"
[[ "$(wc -l < "$SKOPEO_LOG.copy")" -eq 6 ]]

rm -f "$SKOPEO_STATE"/*
export TEST_INSPECT_AUTH=true
if run_copy >"$TEST_ROOT/auth.out" 2>"$TEST_ROOT/auth.err"; then
  echo "Expected auth inspect failure." >&2
  exit 1
fi
unset TEST_INSPECT_AUTH
grep -Fq "unauthorized" "$TEST_ROOT/auth.err"
[[ ! -f "$SKOPEO_LOG.copy" ]] || [[ "$(wc -l < "$SKOPEO_LOG.copy")" -eq 6 ]]

export TEST_INSPECT_NETWORK=true
if run_copy >"$TEST_ROOT/net.out" 2>"$TEST_ROOT/net.err"; then
  echo "Expected network inspect failure." >&2
  exit 1
fi
unset TEST_INSPECT_NETWORK
grep -Fq "connection refused" "$TEST_ROOT/net.err"

rm -f "$SKOPEO_STATE"/* "$SKOPEO_LOG.copy"
export TEST_DIGEST_MISMATCH=true
if run_copy >"$TEST_ROOT/mismatch.out" 2>"$TEST_ROOT/mismatch.err"; then
  echo "Expected digest mismatch to fail." >&2
  exit 1
fi
unset TEST_DIGEST_MISMATCH
grep -Fq "Digest-preserving copy failed" "$TEST_ROOT/mismatch.err"

if SKOPEO="$FAKE_BIN/skopeo" bash "$SCRIPT" --candidate "$TEST_ROOT/candidate.json" \
  --registry "example.com" --output "$TEST_ROOT/bad-registry.json" \
  >"$TEST_ROOT/registry.out" 2>"$TEST_ROOT/registry.err"; then
  echo "Expected invalid registry to fail." >&2
  exit 1
fi

echo "copy-ocir-images-to-ecr.sh regression tests passed."

