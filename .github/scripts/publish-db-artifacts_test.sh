#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/publish-db-artifacts.sh"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
SKOPEO_LOG="$TEST_ROOT/skopeo.log"
DOCKER_LOG="$TEST_ROOT/docker.log"
STATE="$TEST_ROOT/state"
cleanup() { rm -rf "$TEST_ROOT"; }
trap cleanup EXIT
mkdir -p "$FAKE_BIN" "$STATE" "$TEST_ROOT/context/database" "$TEST_ROOT/context/seed"
touch "$TEST_ROOT/context/database/Dockerfile" "$TEST_ROOT/context/seed/Dockerfile"

SHA="0123456789abcdef0123456789abcdef01234567"
OTHER="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
REGISTRY="123456789012.dkr.ecr.ap-northeast-2.amazonaws.com"
DIGEST_M="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
DIGEST_S="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

key_for() { printf '%s' "$1" | sed 's|docker://||' | tr '/' '_'; }

cat > "$FAKE_BIN/skopeo" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$SKOPEO_LOG"
if [[ "$1" == "inspect" && "$*" == *"--raw"* ]]; then
  ref=""
  for arg in "$@"; do [[ "$arg" == docker://* ]] && ref="$arg"; done
  if [[ "${TEST_INSPECT_AUTH:-false}" == "true" ]]; then
    echo "unauthorized: authentication required" >&2
    exit 1
  fi
  key="$(printf '%s' "$ref" | sed 's|docker://||' | tr '/' '_')"
  if [[ ! -f "$STATE/$key.digest" ]]; then
    echo "manifest unknown: Requested image not found" >&2
    exit 1
  fi
  cat "$STATE/$key.digest"
  exit 0
fi
if [[ "$1" == "inspect" ]]; then
  ref=""
  for arg in "$@"; do [[ "$arg" == docker://* ]] && ref="$arg"; done
  key="$(printf '%s' "$ref" | sed 's|docker://||' | tr '/' '_')"
  # digest refs store revision next to the digest file of the tag when tests seed state
  rev_file="$STATE/$key.revision"
  if [[ ! -f "$rev_file" ]]; then
    base="${key%%@*}"
    rev_file="$STATE/${base}.revision"
  fi
  [[ -f "$rev_file" ]] || { echo "revision missing" >&2; exit 1; }
  jq -n --rawfile rev "$rev_file" '{Labels: {"org.opencontainers.image.revision": ($rev | sub("\\n$";""))}}'
  exit 0
fi
if [[ "$1" == "manifest-digest" ]]; then
  value="$(tr -d '\n' < "$2")"
  [[ "$value" =~ ^sha256:[0-9a-f]{64}$ ]] || exit 1
  printf '%s\n' "$value"
  exit 0
fi
echo "unexpected skopeo command: $*" >&2
exit 1
EOF

cat > "$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$DOCKER_LOG"
if [[ "$1" == "buildx" ]]; then
  tag=""
  file=""
  prev=""
  for arg in "$@"; do
    if [[ "$prev" == "-t" ]]; then tag="$arg"; fi
    if [[ "$prev" == "-f" ]]; then file="$arg"; fi
    prev="$arg"
  done
  echo "built $tag from $file" >> "$DOCKER_LOG.build"
  exit 0
fi
if [[ "$1" == "tag" ]]; then
  echo "tag $2 $3" >> "$DOCKER_LOG.tag"
  exit 0
fi
if [[ "$1" == "push" ]]; then
  dest="$2"
  key="$(printf '%s' "$dest" | tr '/' '_')"
  if [[ "$dest" == *db-migrate:* ]]; then digest="$DIGEST_M"; else digest="$DIGEST_S"; fi
  printf '%s\n' "$digest" > "$STATE/$key.digest"
  printf '%s\n' "$SHA" > "$STATE/$key.revision"
  # also store digest-ref keys used by inspect of name@digest
  digest_key="$(printf '%s' "${dest%%:*}@$digest" | tr '/' '_')"
  printf '%s\n' "$digest" > "$STATE/$digest_key.digest"
  printf '%s\n' "$SHA" > "$STATE/$digest_key.revision"
  echo "pushed $dest $digest" >> "$DOCKER_LOG.push"
  exit 0
fi
echo "unexpected docker command: $*" >&2
exit 1
EOF
chmod +x "$FAKE_BIN/skopeo" "$FAKE_BIN/docker"

run_pub() {
  SKOPEO="$FAKE_BIN/skopeo" DOCKER="$FAKE_BIN/docker" \
  SKOPEO_LOG="$SKOPEO_LOG" DOCKER_LOG="$DOCKER_LOG" STATE="$STATE" \
  SHA="$SHA" DIGEST_M="$DIGEST_M" DIGEST_S="$DIGEST_S" \
  TEST_INSPECT_AUTH="${TEST_INSPECT_AUTH:-false}" \
    bash "$SCRIPT" --sha "$SHA" --registry "$REGISTRY" --repository-prefix cc-test/ \
      --context "$TEST_ROOT/context" --output "$TEST_ROOT/out.json"
}

run_pub
[[ "$(grep -c '^buildx' "$DOCKER_LOG")" -eq 2 ]]
[[ "$(grep -c '^push' "$DOCKER_LOG")" -eq 2 ]]
[[ "$(jq -r '.images["db-migrate"]' "$TEST_ROOT/out.json")" == "$REGISTRY/cc-test/db-migrate@$DIGEST_M" ]]
[[ "$(jq -r '.images["db-seed"]' "$TEST_ROOT/out.json")" == "$REGISTRY/cc-test/db-seed@$DIGEST_S" ]]

: > "$DOCKER_LOG"
run_pub >"$TEST_ROOT/noop.out" 2>"$TEST_ROOT/noop.err"
[[ "$(grep -c '^buildx' "$DOCKER_LOG" || true)" -eq 0 ]]
[[ "$(grep -c '^push' "$DOCKER_LOG" || true)" -eq 0 ]]
grep -Fq "No-op:" "$TEST_ROOT/noop.err"
[[ "$(jq -r '.images["db-migrate"]' "$TEST_ROOT/out.json")" == "$REGISTRY/cc-test/db-migrate@$DIGEST_M" ]]

rm -f "$STATE/$(printf '%s' "$REGISTRY/cc-test/db-migrate:${SHA}" | tr '/' '_').digest"
: > "$DOCKER_LOG"
run_pub >"$TEST_ROOT/one.out" 2>"$TEST_ROOT/one.err"
[[ "$(grep -c '^buildx' "$DOCKER_LOG")" -eq 1 ]]
[[ "$(grep -c '^push' "$DOCKER_LOG")" -eq 1 ]]
grep -Fq "db-migrate" "$DOCKER_LOG"

printf '%s\n' "$OTHER" > "$STATE/$(printf '%s' "$REGISTRY/cc-test/db-migrate@$DIGEST_M" | tr '/' '_').revision"
if run_pub >"$TEST_ROOT/conflict.out" 2>"$TEST_ROOT/conflict.err"; then
  echo "Expected revision conflict to fail." >&2
  exit 1
fi
grep -Fq "revision is" "$TEST_ROOT/conflict.err"

rm -f "$STATE"/*
export TEST_INSPECT_AUTH=true
if run_pub >"$TEST_ROOT/auth.out" 2>"$TEST_ROOT/auth.err"; then
  echo "Expected auth failure." >&2
  exit 1
fi
unset TEST_INSPECT_AUTH
grep -Fq "unauthorized" "$TEST_ROOT/auth.err"
[[ ! -f "$DOCKER_LOG.build" ]] || [[ "$(wc -l < "$DOCKER_LOG.build")" -eq 2 ]]

echo "publish-db-artifacts.sh regression tests passed."

