#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/validate-candidate-manifest.sh"
TEST_ROOT="$(mktemp -d)"
cleanup() { rm -rf "$TEST_ROOT"; }
trap cleanup EXIT

SHA="0123456789abcdef0123456789abcdef01234567"
OTHER="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
DIGEST="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

write_valid() {
  jq -n --arg sha "$SHA" --arg digest "$DIGEST" '{
    commit_sha: $sha,
    ci_url: "https://github.com/example/cc-service/actions/runs/1",
    images: {
      "config-server": ("nrt.ocir.io/ns/cc-dev/config-server@" + $digest),
      "embedding-service": ("nrt.ocir.io/ns/cc-dev/embedding-service@" + $digest),
      "eureka-server": ("nrt.ocir.io/ns/cc-dev/eureka-server@" + $digest),
      "gateway": ("nrt.ocir.io/ns/cc-dev/gateway@" + $digest),
      "k6": ("nrt.ocir.io/ns/cc-dev/k6@" + $digest),
      "order-service": ("nrt.ocir.io/ns/cc-dev/order-service@" + $digest),
      "product-service": ("nrt.ocir.io/ns/cc-dev/product-service@" + $digest),
      "user-service": ("nrt.ocir.io/ns/cc-dev/user-service@" + $digest)
    },
    config_labels: {
      "config-server": $sha,
      "eureka-server": $sha,
      "gateway": $sha,
      "order-service": $sha,
      "product-service": $sha,
      "user-service": $sha
    }
  }' > "$1"
}

expect_fail() {
  local name="$1"
  local file="$2"
  if bash "$SCRIPT" "$file" >"$TEST_ROOT/$name.out" 2>"$TEST_ROOT/$name.err"; then
    echo "Expected $name to fail." >&2
    exit 1
  fi
}

write_valid "$TEST_ROOT/valid.json"
bash "$SCRIPT" "$TEST_ROOT/valid.json"

jq '.config_labels["order-service"] = $sha' --arg sha "$OTHER" "$TEST_ROOT/valid.json" > "$TEST_ROOT/label-ok.json"
bash "$SCRIPT" "$TEST_ROOT/label-ok.json"

jq 'del(.config_labels["order-service"])' "$TEST_ROOT/valid.json" > "$TEST_ROOT/missing-label.json"
expect_fail missing-label "$TEST_ROOT/missing-label.json"

jq '.config_labels["order-service"] = "not-a-sha"' "$TEST_ROOT/valid.json" > "$TEST_ROOT/bad-label.json"
expect_fail bad-label "$TEST_ROOT/bad-label.json"

jq '.commit_sha = (.commit_sha | ascii_upcase)' "$TEST_ROOT/valid.json" > "$TEST_ROOT/uppercase.json"
expect_fail uppercase "$TEST_ROOT/uppercase.json"

jq 'del(.images["order-service"])' "$TEST_ROOT/valid.json" > "$TEST_ROOT/missing.json"
expect_fail missing "$TEST_ROOT/missing.json"
jq 'del(.images["embedding-service"])' "$TEST_ROOT/valid.json" > "$TEST_ROOT/missing-embedding.json"
expect_fail missing-embedding "$TEST_ROOT/missing-embedding.json"
jq 'del(.images["k6"])' "$TEST_ROOT/valid.json" > "$TEST_ROOT/missing-k6.json"
expect_fail missing-k6 "$TEST_ROOT/missing-k6.json"

jq '.images["payment-service"] = .images["user-service"]' "$TEST_ROOT/valid.json" > "$TEST_ROOT/unknown.json"
expect_fail unknown "$TEST_ROOT/unknown.json"

jq '.images["user-service"] = "nrt.ocir.io/ns/cc-dev/user-service:latest"' "$TEST_ROOT/valid.json" > "$TEST_ROOT/mutable.json"
expect_fail mutable "$TEST_ROOT/mutable.json"

echo "validate-candidate-manifest.sh regression tests passed."
