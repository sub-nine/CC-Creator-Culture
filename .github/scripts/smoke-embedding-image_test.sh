#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/smoke-embedding-image.sh"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
DOCKER_LOG="$TEST_ROOT/docker.log"
cleanup() { rm -rf "$TEST_ROOT"; }
trap cleanup EXIT
mkdir -p "$FAKE_BIN"


write_fake_commands() {
  cat > "$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$DOCKER_LOG"
case "$1" in
  run)
    [[ "$*" == *"--network none"* ]] || exit 1
    printf 'smoke-container\n'
    ;;
  inspect)
    if [[ "${FAIL_INSPECT:-false}" == "true" ]]; then
      printf 'exited\n'
    else
      printf 'running\n'
    fi
    ;;
  exec)
    [[ "${FAIL_EXEC:-false}" == "false" ]] || exit 1
    [[ "${FAIL_EXEC_TIMEOUT:-false}" == "false" ]] || exit 124
    printf 'embedding smoke passed: /docs ready, one /embed request, 768 finite values\n'
    ;;
  rm)
    ;;
  logs)
    ;;
  *)
    exit 1
    ;;
esac
EOF
  cat > "$FAKE_BIN/timeout" <<'EOF'
#!/usr/bin/env bash
[[ "${1:-}" == "--foreground" ]] && shift
shift
exec "$@"
EOF
  chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/timeout"
}

expect_fail() {
  local name="$1"
  if PATH="$FAKE_BIN:$PATH" DOCKER_LOG="$DOCKER_LOG" bash "$SCRIPT" image >/dev/null 2>&1; then
    echo "Expected $name to fail." >&2
    exit 1
  fi
}

write_fake_commands
DOCKER_LOG="$DOCKER_LOG" PATH="$FAKE_BIN:$PATH" bash "$SCRIPT" image
grep -Fq 'run -d --network none image' "$DOCKER_LOG"
grep -Fq 'exec smoke-container python smoke.py' "$DOCKER_LOG"
if ! grep -Fq 'rm -f smoke-container' "$DOCKER_LOG"; then
  echo "Expected the smoke container to be removed." >&2
  exit 1
fi

write_fake_commands
export FAIL_EXEC=true
if DOCKER_LOG="$DOCKER_LOG" PATH="$FAKE_BIN:$PATH" bash "$SCRIPT" image >/dev/null 2>&1; then
  echo "Expected a failed smoke exec to fail the validation." >&2
  exit 1
fi
unset FAIL_EXEC
grep -Fq 'rm -f smoke-container' "$DOCKER_LOG"

write_fake_commands
export FAIL_EXEC_TIMEOUT=true
if DOCKER_LOG="$DOCKER_LOG" PATH="$FAKE_BIN:$PATH" bash "$SCRIPT" image >/dev/null 2>&1; then
  echo "Expected a timed-out smoke exec to fail the validation." >&2
  exit 1
fi
unset FAIL_EXEC_TIMEOUT

write_fake_commands
export FAIL_INSPECT=true
if DOCKER_LOG="$DOCKER_LOG" PATH="$FAKE_BIN:$PATH" SMOKE_TIMEOUT_SECONDS=1 bash "$SCRIPT" image >/dev/null 2>&1; then
  echo "Expected an exited smoke container to fail the validation." >&2
  exit 1
fi
unset FAIL_INSPECT

echo "smoke-embedding-image.sh regression tests passed."
