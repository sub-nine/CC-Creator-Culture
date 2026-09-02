#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/cleanup-ocir-images.sh"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
OCI_LOG="$TEST_ROOT/oci.log"

cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT
mkdir -p "$FAKE_BIN"

CURRENT="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
PREVIOUS="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
OLD="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

jq -n --arg current "$CURRENT" --arg previous "$PREVIOUS" '{
  services: {
    "config-server": [$current, $previous],
    "eureka-server": [$current, $previous],
    "gateway": [$current, $previous],
    "user-service": [$current, $previous],
    "product-service": [$current, $previous],
    "order-service": [$current, $previous]
  }
}' > "$TEST_ROOT/retained.json"

cat > "$FAKE_BIN/oci" <<EOF
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "\$*" >> "\$OCI_LOG"
if [[ "\$*" == *"container image list"* ]]; then
  jq -n --arg current "$CURRENT" --arg previous "$PREVIOUS" --arg old "$OLD" '{
    data: {items: [
      {id: "ocid1.containerimage.test.current", digest: \$current, "display-name": "cc-dev/user-service:current"},
      {id: "ocid1.containerimage.test.previous", digest: \$previous, "display-name": "cc-dev/user-service:previous"},
      {id: "ocid1.containerimage.test.old", digest: \$old, "display-name": "cc-dev/user-service:old"}
    ]}
  }'
fi
EOF
chmod +x "$FAKE_BIN/oci"

OCI_COMPARTMENT_OCID=ocid1.compartment.test \
OCIR_REPOSITORY_PREFIX=cc-dev/ \
OCI_LOG="$OCI_LOG" \
PATH="$FAKE_BIN:$PATH" \
  bash "$SCRIPT" --retained "$TEST_ROOT/retained.json" > "$TEST_ROOT/dry-run.out"
grep -Fq "$OLD" "$TEST_ROOT/dry-run.out"
if grep -Fq "container image delete" "$OCI_LOG"; then
  echo "Dry run unexpectedly deleted an image." >&2
  exit 1
fi

: > "$OCI_LOG"
OCI_COMPARTMENT_OCID=ocid1.compartment.test \
OCIR_REPOSITORY_PREFIX=cc-dev/ \
OCI_LOG="$OCI_LOG" \
PATH="$FAKE_BIN:$PATH" \
  bash "$SCRIPT" --retained "$TEST_ROOT/retained.json" --apply > "$TEST_ROOT/apply.out"
grep -Fq "container image delete --image-id ocid1.containerimage.test.old --force" "$OCI_LOG"
if grep -Fq "ocid1.containerimage.test.current --force" "$OCI_LOG" \
  || grep -Fq "ocid1.containerimage.test.previous --force" "$OCI_LOG"; then
  echo "Retained image was unexpectedly deleted." >&2
  exit 1
fi

jq '.services["order-service"] = []' "$TEST_ROOT/retained.json" > "$TEST_ROOT/invalid.json"
if OCI_COMPARTMENT_OCID=ocid1.compartment.test \
  OCIR_REPOSITORY_PREFIX=cc-dev/ \
  OCI_LOG="$OCI_LOG" \
  PATH="$FAKE_BIN:$PATH" \
    bash "$SCRIPT" --retained "$TEST_ROOT/invalid.json" >/dev/null 2>&1; then
  echo "Expected invalid retained image list to fail." >&2
  exit 1
fi

echo "cleanup-ocir-images.sh regression tests passed."
