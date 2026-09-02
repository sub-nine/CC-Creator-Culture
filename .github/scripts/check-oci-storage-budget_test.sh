#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/check-oci-storage-budget.sh"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
OCI_LOG="$TEST_ROOT/oci.log"

cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT
mkdir -p "$FAKE_BIN"

cat > "$FAKE_BIN/oci" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$OCI_LOG"

if [[ "$*" == "artifacts container repository list"* ]]; then
  if [[ "${TEST_INVALID_REPOSITORY_SIZE:-false}" == "true" ]]; then
    repository_size='"unknown"'
  else
    repository_size="${TEST_REPOSITORY_BYTES:-2147483648}"
  fi
  jq -n --argjson repository_size "$repository_size" '{
    data: {items: [
      {"display-name": "cc-dev/user-service", "layers-size-in-bytes": $repository_size},
      {"display-name": "unrelated/service", "layers-size-in-bytes": 999999999999}
    ]}
  }'
elif [[ "$*" == "os bucket list"* ]]; then
  jq -n '{data: [{name: "cc-dev-state"}, {name: "cc-dev-release"}]}'
elif [[ "$*" == "os bucket get"* ]]; then
  jq -n --argjson size "${TEST_BUCKET_BYTES:-1073741824}" \
    '{data: {"approximate-size": $size}}'
else
  echo "Unexpected OCI command: $*" >&2
  exit 1
fi
EOF
chmod +x "$FAKE_BIN/oci"

run_guard() {
  OCI_COMPARTMENT_OCID=ocid1.compartment.test \
  OCI_NAMESPACE=testnamespace \
  OCIR_REPOSITORY_PREFIX="${TEST_PREFIX:-cc-dev/}" \
  MAX_STORAGE_BYTES="${TEST_MAX_STORAGE_BYTES:-8000000000}" \
  OCI_LOG="$OCI_LOG" \
  PATH="$FAKE_BIN:$PATH" \
    bash "$SCRIPT"
}

run_guard > "$TEST_ROOT/within-budget.out"
grep -Fq 'total=4294967296 bytes' "$TEST_ROOT/within-budget.out"
grep -Fq 'artifacts container repository list' "$OCI_LOG"
[[ "$(grep -c 'os bucket get' "$OCI_LOG")" -eq 2 ]]

export TEST_MAX_STORAGE_BYTES=4294967296
if run_guard > "$TEST_ROOT/at-limit.out" 2>&1; then
  echo "Expected the storage guard to stop at the limit." >&2
  exit 1
fi
unset TEST_MAX_STORAGE_BYTES
grep -Fq 'at or above' "$TEST_ROOT/at-limit.out"

export TEST_INVALID_REPOSITORY_SIZE=true
if run_guard > "$TEST_ROOT/invalid-size.out" 2>&1; then
  echo "Expected an invalid repository size to fail closed." >&2
  exit 1
fi
unset TEST_INVALID_REPOSITORY_SIZE
grep -Fq 'refusing deployment' "$TEST_ROOT/invalid-size.out"

export TEST_PREFIX=other/
if run_guard > "$TEST_ROOT/invalid-prefix.out" 2>&1; then
  echo "Expected a non-cc-dev prefix to fail." >&2
  exit 1
fi
unset TEST_PREFIX

echo "check-oci-storage-budget.sh regression tests passed."
