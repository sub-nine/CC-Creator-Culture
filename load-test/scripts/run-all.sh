#!/bin/sh
# 사용법: load-test/scripts/run-all.sh [target]  (target 기본값: local)
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.."

TARGET="${1:-local}"

docker compose -f load-test/docker-compose.yml run --rm -e TARGET="$TARGET" k6-all
