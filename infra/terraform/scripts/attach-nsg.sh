#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <vnic-ocid> <new-nsg-ocid>" >&2
  exit 64
fi

VNIC_OCID="$1"
NEW_NSG_OCID="$2"

CURRENT_NSGS="$(oci network vnic get \
  --vnic-id "$VNIC_OCID" \
  --query 'data."nsg-ids"' \
  --raw-output)"
MERGED_NSGS="$(jq -c --arg nsg "$NEW_NSG_OCID" '
  if index($nsg) then . else . + [$nsg] end
' <<<"$CURRENT_NSGS")"

if [[ "$CURRENT_NSGS" == "$MERGED_NSGS" ]]; then
  echo "NSG is already attached."
  exit 0
fi

oci network vnic update \
  --vnic-id "$VNIC_OCID" \
  --nsg-ids "$MERGED_NSGS" \
  --force >/dev/null

echo "NSG attachment completed without removing existing NSGs."
