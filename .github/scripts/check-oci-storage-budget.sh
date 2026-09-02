#!/usr/bin/env bash
set -Eeuo pipefail

: "${OCI_COMPARTMENT_OCID:?OCI_COMPARTMENT_OCID is required}"
: "${OCI_NAMESPACE:?OCI_NAMESPACE is required}"
: "${OCIR_REPOSITORY_PREFIX:?OCIR_REPOSITORY_PREFIX is required}"

MAX_STORAGE_BYTES="${MAX_STORAGE_BYTES:-8000000000}"
[[ "$MAX_STORAGE_BYTES" =~ ^[1-9][0-9]*$ ]] || {
  echo "MAX_STORAGE_BYTES must be a positive integer." >&2
  exit 65
}
[[ "$OCIR_REPOSITORY_PREFIX" == "cc-dev/" ]] || {
  echo "OCIR_REPOSITORY_PREFIX must be exactly cc-dev/." >&2
  exit 65
}

repositories_json="$(oci artifacts container repository list \
  --compartment-id "$OCI_COMPARTMENT_OCID" \
  --all)"

if ! ocir_bytes="$(jq -er --arg prefix "$OCIR_REPOSITORY_PREFIX" '
  .data.items as $items
  | if ($items | type) != "array" then error("invalid repository list") else
      [
        $items[]
        | select((."display-name" // "") | startswith($prefix))
        | ."layers-size-in-bytes"
      ] as $sizes
      | if all($sizes[]; type == "number" and . >= 0)
        then ($sizes | add // 0)
        else error("invalid OCIR repository size")
        end
    end
' <<<"$repositories_json")"; then
  echo "Unable to determine cc-dev OCIR storage; refusing deployment." >&2
  exit 65
fi

object_storage_bytes=0
buckets_json="$(oci os bucket list \
  --compartment-id "$OCI_COMPARTMENT_OCID" \
  --namespace-name "$OCI_NAMESPACE" \
  --all)"
if ! bucket_names_json="$(jq -ce '
  .data
  | if type != "array" then error("invalid bucket list") else
      map(.name)
      | if all(.[]; type == "string" and length > 0)
        then .
        else error("invalid bucket name")
        end
    end
' <<<"$buckets_json")"; then
  echo "Unable to list Object Storage buckets; refusing deployment." >&2
  exit 65
fi

while IFS= read -r bucket_name; do
  bucket_json="$(oci os bucket get \
    --namespace-name "$OCI_NAMESPACE" \
    --bucket-name "$bucket_name" \
    --fields approximateSize)"
  if ! bucket_bytes="$(jq -er '
    if (.data | type) != "object" then error("invalid bucket response") else
      (.data."approximate-size" // 0)
      | select(type == "number" and . >= 0)
    end
  ' <<<"$bucket_json")"; then
    echo "Unable to determine Object Storage usage for $bucket_name; refusing deployment." >&2
    exit 65
  fi
  object_storage_bytes=$((object_storage_bytes + bucket_bytes))
done < <(jq -r '.[]' <<<"$bucket_names_json")

total_bytes=$((ocir_bytes + object_storage_bytes))
printf 'OCI storage guard: OCIR=%s bytes, Object Storage=%s bytes, total=%s bytes, limit=%s bytes\n' \
  "$ocir_bytes" "$object_storage_bytes" "$total_bytes" "$MAX_STORAGE_BYTES"

if (( total_bytes >= MAX_STORAGE_BYTES )); then
  echo "OCI storage is at or above the 8 GB deployment guard. Review retention before publishing images." >&2
  exit 1
fi
