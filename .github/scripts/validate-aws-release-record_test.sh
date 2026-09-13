#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/validate-aws-release-record.sh"
TEST_ROOT="$(mktemp -d)"
cleanup() { rm -rf "$TEST_ROOT"; }
trap cleanup EXIT

SHA="0123456789abcdef0123456789abcdef01234567"
OTHER="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
DIGEST_A="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
DIGEST_B="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
DIGEST_C="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
DIGEST_D="sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
DIGEST_E="sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
DIGEST_F="sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
ECR="123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/cc-service"
MANIFEST_SHA="sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
MIGRATION_ARTIFACT="${ECR}/db-migration@${DIGEST_A}"

write_valid() {
  local dest="$1"
  local source_sha="${2:-$SHA}"
  local main_sha="${3:-$source_sha}"
  jq -n \
    --arg source_sha "$source_sha" \
    --arg main_sha "$main_sha" \
    --arg ecr "$ECR" \
    --arg digest_a "$DIGEST_A" \
    --arg digest_b "$DIGEST_B" \
    --arg digest_c "$DIGEST_C" \
    --arg digest_d "$DIGEST_D" \
    --arg digest_e "$DIGEST_E" \
    --arg digest_f "$DIGEST_F" \
    --arg migration_artifact "$MIGRATION_ARTIFACT" \
    --arg manifest_sha "$MANIFEST_SHA" \
    --arg seed_artifact "${ECR}/db-seed@${DIGEST_B}" \
    '{
      schema_version: 1,
      release_id: ("rel-" + $source_sha),
      source_sha: $source_sha,
      config_sha: $source_sha,
      main_sha: $main_sha,
      ci_url: "https://github.com/example/cc-service/actions/runs/1",
      tag: "v1.2.3",
      images: {
        "config-server": ($ecr + "/config-server@" + $digest_a),
        "eureka-server": ($ecr + "/eureka-server@" + $digest_b),
        "gateway": ($ecr + "/gateway@" + $digest_c),
        "order-service": ($ecr + "/order-service@" + $digest_d),
        "product-service": ($ecr + "/product-service@" + $digest_e),
        "user-service": ($ecr + "/user-service@" + $digest_f)
      },
      migrations: {
        version: "V1",
        artifact: $migration_artifact,
        images: {"db-migrate": $migration_artifact, "db-seed": $seed_artifact}
      },
      seed: {artifact: $seed_artifact},
      artifacts: {
        candidate_manifest: ("candidates/" + $source_sha + ".json"),
        candidate_manifest_sha256: $manifest_sha,
        oci_verified_record: ("candidates/" + $source_sha + ".oci-verified.json"),
        ecr_images: ("candidates/" + $source_sha + ".ecr.json")
      },
      validation: {
        oci_health_url: "https://dev.example.com/actuator/health",
        oci_health_result: "passed",
        verification_run_url: "https://github.com/example/cc-service/actions/runs/99",
        verification_result: "passed",
        manifest_sha256: $manifest_sha,
        artifact_tests: {
          result: "passed",
          run_url: "https://github.com/example/cc-service/actions/runs/88",
          db_migrate: {result: "passed", image: $migration_artifact},
          db_seed: {result: "passed", image: $seed_artifact}
        }
      }
    }' > "$dest"
}

expect_fail() {
  local name="$1"
  local file="$2"
  shift 2
  if bash "$SCRIPT" "$@" "$file" >"$TEST_ROOT/$name.out" 2>"$TEST_ROOT/$name.err"; then
    echo "Expected $name to fail." >&2
    exit 1
  fi
}

REPO="$TEST_ROOT/repo"
mkdir -p "$REPO"
git -C "$REPO" init -q
git -C "$REPO" config user.email test@example.com
git -C "$REPO" config user.name test
git -C "$REPO" config commit.gpgsign false
printf 'base\n' > "$REPO/file"
git -C "$REPO" add file
git -C "$REPO" commit -q -m base
SOURCE_SHA="$(git -C "$REPO" rev-parse HEAD)"
git -C "$REPO" commit -q --allow-empty -m merge
MAIN_SAME_TREE="$(git -C "$REPO" rev-parse HEAD)"
printf 'changed\n' > "$REPO/file"
git -C "$REPO" add file
git -C "$REPO" commit -q -m change
MAIN_DIFF_TREE="$(git -C "$REPO" rev-parse HEAD)"
[[ "$SOURCE_SHA" != "$MAIN_SAME_TREE" ]]
[[ "$(git -C "$REPO" rev-parse "${SOURCE_SHA}^{tree}")" == "$(git -C "$REPO" rev-parse "${MAIN_SAME_TREE}^{tree}")" ]]
[[ "$(git -C "$REPO" rev-parse "${SOURCE_SHA}^{tree}")" != "$(git -C "$REPO" rev-parse "${MAIN_DIFF_TREE}^{tree}")" ]]

write_valid "$TEST_ROOT/valid.json"
bash "$SCRIPT" "$TEST_ROOT/valid.json"

write_valid "$TEST_ROOT/same-tree.json" "$SOURCE_SHA" "$MAIN_SAME_TREE"
bash "$SCRIPT" --require-promotion --repository "$REPO" "$TEST_ROOT/same-tree.json"

write_valid "$TEST_ROOT/diff-tree.json" "$SOURCE_SHA" "$MAIN_DIFF_TREE"
expect_fail diff-tree "$TEST_ROOT/diff-tree.json" --require-promotion --repository "$REPO"
grep -Fq "main tree must equal the candidate tree" "$TEST_ROOT/diff-tree.err"

write_valid "$TEST_ROOT/missing-git.json" "$OTHER" "$SOURCE_SHA"
expect_fail missing-git "$TEST_ROOT/missing-git.json" --require-promotion --repository "$REPO"
grep -Fq "not in --repository" "$TEST_ROOT/missing-git.err"

expect_fail missing-repo "$TEST_ROOT/same-tree.json" --require-promotion
grep -Fq -- "--repository is required" "$TEST_ROOT/missing-repo.err"

jq '.config_sha = $sha' --arg sha "$OTHER" "$TEST_ROOT/valid.json" > "$TEST_ROOT/sha-mismatch.json"
expect_fail sha-mismatch "$TEST_ROOT/sha-mismatch.json"
grep -Fq "source_sha and config_sha" "$TEST_ROOT/sha-mismatch.err"

jq '.source_sha = (.source_sha | ascii_upcase)' "$TEST_ROOT/valid.json" > "$TEST_ROOT/uppercase-sha.json"
expect_fail uppercase-sha "$TEST_ROOT/uppercase-sha.json"

jq 'del(.images["order-service"])' "$TEST_ROOT/valid.json" > "$TEST_ROOT/missing-service.json"
expect_fail missing-service "$TEST_ROOT/missing-service.json"

jq '.images["payment-service"] = .images["user-service"]' "$TEST_ROOT/valid.json" > "$TEST_ROOT/unknown-service.json"
expect_fail unknown-service "$TEST_ROOT/unknown-service.json"

jq '.images["user-service"] = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/cc-service/user-service:latest"' \
  "$TEST_ROOT/valid.json" > "$TEST_ROOT/mutable-image.json"
expect_fail mutable-image "$TEST_ROOT/mutable-image.json"

jq '.images["user-service"] = "nrt.ocir.io/ns/cc-dev/user-service@sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"' \
  "$TEST_ROOT/valid.json" > "$TEST_ROOT/ocir-image.json"
expect_fail ocir-image "$TEST_ROOT/ocir-image.json"

jq '.migrations.artifact = "apps/user-service/src/main/resources/db/migration"' \
  "$TEST_ROOT/valid.json" > "$TEST_ROOT/migration-path.json"
expect_fail migration-path "$TEST_ROOT/migration-path.json"

jq '.migrations.artifact = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/cc-service/db-migration:latest"' \
  "$TEST_ROOT/valid.json" > "$TEST_ROOT/migration-mutable.json"
expect_fail migration-mutable "$TEST_ROOT/migration-mutable.json"

jq 'del(.seed)' "$TEST_ROOT/valid.json" > "$TEST_ROOT/missing-seed.json"
expect_fail missing-seed "$TEST_ROOT/missing-seed.json"

jq '.seed.artifact = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/cc-service/db-seed:latest"' \
  "$TEST_ROOT/valid.json" > "$TEST_ROOT/seed-mutable.json"
expect_fail seed-mutable "$TEST_ROOT/seed-mutable.json"

jq '.validation.verification_result = true' "$TEST_ROOT/valid.json" > "$TEST_ROOT/bool-true.json"
expect_fail bool-true "$TEST_ROOT/bool-true.json"

jq 'del(.validation.verification_result)' "$TEST_ROOT/valid.json" > "$TEST_ROOT/missing-result.json"
expect_fail missing-result "$TEST_ROOT/missing-result.json"

jq '.tag = "latest"' "$TEST_ROOT/valid.json" > "$TEST_ROOT/latest-tag.json"
expect_fail latest-tag "$TEST_ROOT/latest-tag.json"

jq '.schema_version = "1"' "$TEST_ROOT/valid.json" > "$TEST_ROOT/schema-string.json"
expect_fail schema-string "$TEST_ROOT/schema-string.json"

jq '.validation.manifest_sha256 = $sha' --arg sha "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" \
  "$TEST_ROOT/valid.json" > "$TEST_ROOT/checksum-mismatch.json"
expect_fail checksum-mismatch "$TEST_ROOT/checksum-mismatch.json"

jq '.validation.verification_result = "failed"' "$TEST_ROOT/same-tree.json" > "$TEST_ROOT/failed-result.json"
bash "$SCRIPT" "$TEST_ROOT/failed-result.json"
expect_fail failed-result "$TEST_ROOT/failed-result.json" --require-promotion --repository "$REPO"
grep -Fq "verification_result passed" "$TEST_ROOT/failed-result.err"

jq '.validation.verification_run_url = .validation.oci_health_url' "$TEST_ROOT/same-tree.json" > "$TEST_ROOT/health-as-e2e.json"
expect_fail health-as-e2e "$TEST_ROOT/health-as-e2e.json" --require-promotion --repository "$REPO"
grep -Fq "not E2E" "$TEST_ROOT/health-as-e2e.err"

jq 'del(.validation.artifact_tests)' "$TEST_ROOT/same-tree.json" > "$TEST_ROOT/missing-artifact-tests.json"
expect_fail missing-artifact-tests "$TEST_ROOT/missing-artifact-tests.json" --require-promotion --repository "$REPO"
grep -Fq "artifact tests" "$TEST_ROOT/missing-artifact-tests.err"

jq '.validation.artifact_tests.result = "failed"' "$TEST_ROOT/same-tree.json" > "$TEST_ROOT/failed-artifact-tests.json"
expect_fail failed-artifact-tests "$TEST_ROOT/failed-artifact-tests.json" --require-promotion --repository "$REPO"

jq '.seed.artifact = .migrations.artifact' "$TEST_ROOT/same-tree.json" > "$TEST_ROOT/seed-mismatch.json"
expect_fail seed-mismatch "$TEST_ROOT/seed-mismatch.json" --require-promotion --repository "$REPO"

echo "validate-aws-release-record.sh regression tests passed."
