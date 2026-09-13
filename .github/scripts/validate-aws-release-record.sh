#!/usr/bin/env bash
set -Eeuo pipefail

# Format and git-tree checks only. Callers must hash-verify candidate/evidence
# files against artifacts.candidate_manifest_sha256 before promotion.

usage() {
  echo "Usage: $0 [--require-promotion --repository <git-dir>] <aws-release.json>" >&2
  exit 64
}

REQUIRED_SERVICES='["config-server","eureka-server","gateway","order-service","product-service","user-service"]'
REQUIRE_PROMOTION=false
REPO=""
FILE=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --require-promotion) REQUIRE_PROMOTION=true; shift ;;
    --repository)
      [[ "$#" -ge 2 ]] || usage
      REPO="$2"
      shift 2
      ;;
    -*) usage ;;
    *)
      [[ -z "$FILE" ]] || usage
      FILE="$1"
      shift
      ;;
  esac
done
[[ -n "$FILE" && -f "$FILE" ]] || usage

fail() {
  echo "$1" >&2
  exit 65
}

jq -e . "$FILE" >/dev/null || fail "AWS release record must be valid JSON."

jq -e '.schema_version == 1' "$FILE" >/dev/null || fail "schema_version must be number 1."

source_sha="$(jq -er '.source_sha' "$FILE")" || fail "source_sha is required."
config_sha="$(jq -er '.config_sha' "$FILE")" || fail "config_sha is required."
main_sha="$(jq -er '.main_sha' "$FILE")" || fail "main_sha is required."
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail "source_sha must be a full lowercase Git SHA."
[[ "$config_sha" =~ ^[0-9a-f]{40}$ ]] || fail "config_sha must be a full lowercase Git SHA."
[[ "$main_sha" =~ ^[0-9a-f]{40}$ ]] || fail "main_sha must be a full lowercase Git SHA."
[[ "$config_sha" == "$source_sha" ]] || fail "source_sha and config_sha must be the same full lowercase Git SHA."
jq -e --arg release_id "rel-${source_sha}" '.release_id == $release_id' "$FILE" >/dev/null || fail "release_id must be rel-<source_sha>."

jq -e '.ci_url | type == "string" and test("^https://")' "$FILE" >/dev/null || fail "ci_url must be an https URL."
jq -e '.tag | type == "string" and test("^v[0-9]+\\.[0-9]+\\.[0-9]+$")' "$FILE" >/dev/null || fail "tag must be vMAJOR.MINOR.PATCH."

jq -e --argjson required "$REQUIRED_SERVICES" '
  ((.images | keys | sort) == $required)
  and all(.images[]; type == "string" and test("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$"))
' "$FILE" >/dev/null || fail "images must map the six services to canonical ECR digest refs."

jq -e '
  (.migrations.version | type == "string" and length > 0)
  and (.migrations.artifact | type == "string" and test("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$"))
  and (.seed.artifact | type == "string" and test("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$"))
' "$FILE" >/dev/null || fail "migrations.artifact and seed.artifact must be immutable ECR digest images."

jq -e '
  (.artifacts.candidate_manifest | type == "string" and length > 0)
  and (.artifacts.candidate_manifest_sha256 | test("^sha256:[0-9a-f]{64}$"))
  and (.artifacts.oci_verified_record | type == "string" and length > 0)
  and (.artifacts.ecr_images | type == "string" and length > 0)
' "$FILE" >/dev/null || fail "artifacts must reference candidate, OCI verified record, ECR images, and manifest checksum."

jq -e '
  (.validation.oci_health_url | type == "string" and test("^https://"))
  and .validation.oci_health_result == "passed"
  and (.validation.verification_run_url | type == "string" and test("^https://"))
  and (.validation.verification_result == "passed" or .validation.verification_result == "failed")
  and (.validation.manifest_sha256 | test("^sha256:[0-9a-f]{64}$"))
  and .validation.manifest_sha256 == .artifacts.candidate_manifest_sha256
' "$FILE" >/dev/null || fail "validation evidence must bind manifest checksum and an explicit string test result."

if [[ "$REQUIRE_PROMOTION" == "true" ]]; then
  [[ -n "$REPO" ]] || fail "--repository is required for promotion."
  git -C "$REPO" rev-parse --git-dir >/dev/null 2>&1 || fail "--repository must be a git directory; refusing promotion."
  jq -e '.validation.verification_result == "passed"' "$FILE" >/dev/null || fail "promotion requires verification_result passed."
  jq -e '.validation.verification_run_url != .validation.oci_health_url' "$FILE" >/dev/null || fail "OCI health URL is not E2E evidence; verification_run_url must be a separate test result run."
  source_tree="$(git -C "$REPO" rev-parse --verify "${source_sha}^{tree}" 2>/dev/null)" || fail "source_sha is not in --repository; refusing promotion."
  main_tree="$(git -C "$REPO" rev-parse --verify "${main_sha}^{tree}" 2>/dev/null)" || fail "main_sha is not in --repository; refusing promotion."
  [[ "$source_tree" =~ ^[0-9a-f]{40}$ && "$main_tree" =~ ^[0-9a-f]{40}$ ]] || fail "git tree ids must be full lowercase SHAs; refusing promotion."
  [[ "$source_tree" == "$main_tree" ]] || fail "main tree must equal the candidate tree."
  jq -e '
    .validation.artifact_tests.result == "passed"
    and (.validation.artifact_tests.run_url | type == "string" and test("^https://"))
    and .validation.artifact_tests.run_url != .validation.oci_health_url
    and .validation.artifact_tests.db_migrate.result == "passed"
    and .validation.artifact_tests.db_seed.result == "passed"
    and (.validation.artifact_tests.db_migrate.image | type == "string" and test("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$"))
    and (.validation.artifact_tests.db_seed.image | type == "string" and test("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$"))
    and (.migrations.images["db-migrate"] | type == "string" and test("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$"))
    and (.migrations.images["db-seed"] | type == "string" and test("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$"))
    and .migrations.artifact == .migrations.images["db-migrate"]
    and .seed.artifact == .migrations.images["db-seed"]
    and .validation.artifact_tests.db_migrate.image == .migrations.images["db-migrate"]
    and .validation.artifact_tests.db_seed.image == .migrations.images["db-seed"]
  ' "$FILE" >/dev/null || fail "promotion requires passed digest-pinned db-migrate and db-seed artifact tests."
fi
