#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 --retained <retained-images.json> [--apply]" >&2
  exit 64
}

RETAINED_FILE=""
APPLY=false
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --retained) RETAINED_FILE="$2"; shift 2 ;;
    --apply) APPLY=true; shift ;;
    *) usage ;;
  esac
done

[[ -f "$RETAINED_FILE" ]] || usage
: "${OCI_COMPARTMENT_OCID:?OCI_COMPARTMENT_OCID is required}"
: "${OCIR_REPOSITORY_PREFIX:?OCIR_REPOSITORY_PREFIX is required}"
[[ "$OCIR_REPOSITORY_PREFIX" == "cc-dev/" ]] || {
  echo "OCIR_REPOSITORY_PREFIX must be exactly cc-dev/." >&2
  exit 65
}

required_services='["config-server","eureka-server","gateway","order-service","product-service","user-service"]'
jq -e --argjson required "$required_services" '
  (.services | keys | sort) == $required
  and all(.services[]; length >= 1 and length <= 2)
  and all(.services[][]; test("^sha256:[0-9a-f]{64}$"))
' "$RETAINED_FILE" >/dev/null || {
  echo "Retained image list is incomplete or invalid; refusing cleanup." >&2
  exit 65
}

retained_digests="$(jq -c '[.services[][]] | unique' "$RETAINED_FILE")"
images_json="$(oci artifacts container image list \
  --compartment-id "$OCI_COMPARTMENT_OCID" \
  --repository-name "${OCIR_REPOSITORY_PREFIX}*" \
  --all)"

delete_count=0
while IFS= read -r image; do
  image_id="$(jq -r '.id' <<<"$image")"
  digest="$(jq -r '.digest' <<<"$image")"
  display_name="$(jq -r '."display-name"' <<<"$image")"

  [[ "$image_id" == ocid1.containerimage.* ]] || {
    echo "Unexpected container image ID; refusing cleanup." >&2
    exit 65
  }
  [[ "$display_name" == "$OCIR_REPOSITORY_PREFIX"* ]] || {
    echo "Image outside cc-dev prefix; refusing cleanup." >&2
    exit 65
  }
  if jq -e --arg digest "$digest" 'index($digest) != null' <<<"$retained_digests" >/dev/null; then
    continue
  fi

  printf 'cleanup-candidate %s %s\n' "$display_name" "$digest"
  delete_count=$((delete_count + 1))
  if [[ "$APPLY" == "true" ]]; then
    oci artifacts container image delete --image-id "$image_id" --force
  fi
done < <(jq -c '.data.items[]?' <<<"$images_json")

if [[ "$APPLY" == "true" ]]; then
  echo "Deleted $delete_count superseded cc-service image(s)."
else
  echo "Dry run: $delete_count superseded cc-service image(s) would be deleted."
fi
