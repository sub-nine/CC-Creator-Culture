#!/bin/sh
# 사용법: load-test/scripts/run.sh <scenario-path> [target]  (target 기본값: local)
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.."

SCENARIO="$1"
TARGET="${2:-local}"

if [ -z "$SCENARIO" ]; then
  echo "사용법: $0 <scenario-path> [target]" >&2
  exit 1
fi

TESTID="$(date +%s)"

docker compose -f load-test/docker-compose.yml run --rm --build -e TARGET="$TARGET" k6 \
  run --out experimental-prometheus-rw --tag testid="$TESTID" "$SCENARIO"
