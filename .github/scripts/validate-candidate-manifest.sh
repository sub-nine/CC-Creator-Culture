#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 <candidate.json>" >&2
  exit 64
}

[[ "$#" -eq 1 ]] || usage
CANDIDATE="$1"
[[ -f "$CANDIDATE" ]] || usage

required_labels='["config-server","eureka-server","gateway","order-service","product-service","user-service"]'
required_images='["config-server","embedding-service","eureka-server","gateway","k6","order-service","product-service","user-service"]'

jq -e --argjson labels "$required_labels" --argjson images "$required_images" '
  has("commit_sha") and has("config_labels") and has("ci_url") and has("images")
  and (.commit_sha | test("^[0-9a-f]{40}$"))
  and (.ci_url | type == "string" and test("^https://"))
  and ((.config_labels | keys | sort) == $labels)
  and all(.config_labels[]; type == "string" and test("^[0-9a-f]{40}$"))
  and ((.images | keys | sort) == $images)
  and all(.images[]; type == "string" and test("^[^[:space:]]+@sha256:[0-9a-f]{64}$"))
' "$CANDIDATE" >/dev/null || {
  echo "Candidate manifest is incomplete or invalid." >&2
  exit 65
}
