#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 <terraform-plan.json>" >&2
  exit 64
fi

PLAN_JSON="$1"

blocked_addresses="$(jq -r '
  def has_action($action): (.change.actions // []) | index($action) != null;
  def immutable_core:
    .type == "oci_core_instance" or
    .type == "oci_core_boot_volume" or
    .type == "oci_core_vcn" or
    .type == "oci_core_subnet" or
    .type == "oci_core_vnic";
  def retained_edge:
    .type == "oci_core_public_ip" or
    .type == "cloudflare_dns_record";

  .resource_changes[]?
  | select(
      (immutable_core and (has_action("create") or has_action("delete"))) or
      (retained_edge and has_action("delete"))
    )
  | .address
' "$PLAN_JSON")"

if [[ -n "$blocked_addresses" ]]; then
  echo "Unsafe Terraform actions detected:" >&2
  printf '%s\n' "$blocked_addresses" >&2
  exit 1
fi

echo "Terraform plan passed protected-resource checks."
