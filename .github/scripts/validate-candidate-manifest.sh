#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 <candidate.json>" >&2
  exit 64
}

[[ "$#" -eq 1 ]] || usage
CANDIDATE="$1"
[[ -f "$CANDIDATE" ]] || usage

required_services='["config-server","eureka-server","gateway","order-service","product-service","user-service"]'

jq -e --argjson required "$required_services" '
  has("commit_sha") and has("config_sha") and has("ci_url") and has("images")
  and (.commit_sha | test("^[0-9a-f]{40}$"))
  and .config_sha == .commit_sha
  and (.ci_url | type == "string" and test("^https://"))
  and ((.images | keys | sort) == $required)
  and all(.images[]; type == "string" and test("^[^[:space:]]+@sha256:[0-9a-f]{64}$"))
' "$CANDIDATE" >/dev/null || {
  echo "Candidate manifest is incomplete or invalid." >&2
  exit 65
}

