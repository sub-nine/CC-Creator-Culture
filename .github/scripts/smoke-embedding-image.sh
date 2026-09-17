#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 <image-ref>" >&2
  exit 64
}

[[ "$#" -eq 1 ]] || usage
IMAGE="$1"
SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-240}"
[[ "$SMOKE_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || {
  echo "SMOKE_TIMEOUT_SECONDS must be a positive integer." >&2
  exit 65
}

command -v timeout >/dev/null

# 네트워크 없이 기동해 이미지에 포함된 모델로만 추론을 검증한다.
container="$(docker run -d --network none "$IMAGE")"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

status=""
for _ in $(seq 1 15); do
  status="$(docker inspect --format '{{.State.Status}}' "$container" 2>/dev/null || true)"
  [[ "$status" == "running" ]] && break
  if [[ "$status" == "exited" || "$status" == "dead" ]]; then
    echo "Embedding smoke container stopped before validation." >&2
    docker logs "$container" >&2 || true
    exit 1
  fi
  sleep 1
done
[[ "$status" == "running" ]] || {
  echo "Embedding smoke container did not reach the running state." >&2
  exit 1
}

timeout --foreground "${SMOKE_TIMEOUT_SECONDS}s" docker exec "$container" python smoke.py

echo "Embedding image smoke validation passed."

