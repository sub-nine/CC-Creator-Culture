#!/usr/bin/env bash
set -Eeuo pipefail

: "${GITHUB_OWNER:?GITHUB_OWNER is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GITHUB_RUNNER_TOKEN:?GITHUB_RUNNER_TOKEN is required}"
: "${OCIR_REGISTRY:?OCIR_REGISTRY is required, for example nrt.ocir.io}"

RUNNER_USER="github-runner-cc"
RUNNER_HOME="/opt/actions-runner-cc"
RUNNER_LABEL="cc-dev-deploy"
OCIR_HELPER_REF="${OCIR_HELPER_REF:-e2411c3c86c633537a8f10113c96c99c2fc71e5e}"
HELPER_BUILD_DIR=""

cleanup() {
  unset GITHUB_RUNNER_TOKEN
  if [[ -n "$HELPER_BUILD_DIR" && -d "$HELPER_BUILD_DIR" ]]; then
    rm -rf "$HELPER_BUILD_DIR"
  fi
}
trap cleanup EXIT

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run this script with sudo as root." >&2
  exit 1
fi

command -v docker >/dev/null
command -v jq >/dev/null
command -v curl >/dev/null
command -v oci >/dev/null
command -v timeout >/dev/null

if ! command -v git >/dev/null || ! command -v go >/dev/null; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y --no-install-recommends git golang-go
fi

if ! id "$RUNNER_USER" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "$RUNNER_USER"
fi
usermod -aG docker "$RUNNER_USER"

install -d -m 0750 -o "$RUNNER_USER" -g "$RUNNER_USER" "$RUNNER_HOME" /opt/cc-service

HELPER_BUILD_DIR="$(mktemp -d)"
git -C "$HELPER_BUILD_DIR" init --quiet
git -C "$HELPER_BUILD_DIR" remote add origin https://github.com/jan-g/ip-credential.git
git -C "$HELPER_BUILD_DIR" fetch --depth 1 origin "$OCIR_HELPER_REF"
git -C "$HELPER_BUILD_DIR" checkout --detach FETCH_HEAD
(
  cd "$HELPER_BUILD_DIR"
  go mod vendor
  go build -trimpath -ldflags='-s -w' -o docker-credential-ocir docker-credential-ocir.go
)
install -m 0755 "$HELPER_BUILD_DIR/docker-credential-ocir" /usr/local/bin/docker-credential-ocir

docker_config_dir="/home/$RUNNER_USER/.docker"
docker_config="$docker_config_dir/config.json"
docker_config_tmp="$(mktemp)"
install -d -m 0700 -o "$RUNNER_USER" -g "$RUNNER_USER" "$docker_config_dir"
if [[ -f "$docker_config" ]]; then
  jq --arg registry "$OCIR_REGISTRY" \
    '.credHelpers = (.credHelpers // {}) | .credHelpers[$registry] = "ocir"' \
    "$docker_config" > "$docker_config_tmp"
else
  jq -n --arg registry "$OCIR_REGISTRY" \
    '{credHelpers: {($registry): "ocir"}}' > "$docker_config_tmp"
fi
install -m 0600 -o "$RUNNER_USER" -g "$RUNNER_USER" "$docker_config_tmp" "$docker_config"
rm -f "$docker_config_tmp"

timeout --foreground 30s runuser -u "$RUNNER_USER" -- \
  env OCI_CLI_AUTH=instance_principal oci iam region list >/dev/null
printf '%s\n' "$OCIR_REGISTRY" \
  | timeout --foreground 30s runuser -u "$RUNNER_USER" -- docker-credential-ocir get >/dev/null

if [[ ! -x "$RUNNER_HOME/run.sh" ]]; then
  runner_version="$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest | jq -r '.tag_name | ltrimstr("v")')"
  archive="$RUNNER_HOME/actions-runner.tar.gz"
  curl -fsSL -o "$archive" \
    "https://github.com/actions/runner/releases/download/v${runner_version}/actions-runner-linux-arm64-${runner_version}.tar.gz"
  tar -xzf "$archive" -C "$RUNNER_HOME"
  rm -f "$archive"
  chown -R "$RUNNER_USER:$RUNNER_USER" "$RUNNER_HOME"
fi

if [[ -f "$RUNNER_HOME/.runner" ]]; then
  echo "Runner is already configured at $RUNNER_HOME." >&2
  exit 1
fi

runuser -u "$RUNNER_USER" -- bash -lc "
  cd '$RUNNER_HOME'
  ./config.sh --unattended \\
    --url 'https://github.com/$GITHUB_OWNER/$GITHUB_REPOSITORY' \\
    --token '$GITHUB_RUNNER_TOKEN' \\
    --name 'cc-dev-$(hostname)' \\
    --labels '$RUNNER_LABEL' \\
    --work '_work'
"
unset GITHUB_RUNNER_TOKEN

"$RUNNER_HOME/svc.sh" install "$RUNNER_USER"
runner_service="$(cat "$RUNNER_HOME/.service")"
[[ -n "$runner_service" ]] || {
  echo "GitHub Actions runner service was not created." >&2
  exit 1
}

install -d "/etc/systemd/system/${runner_service}.d"
printf '%s\n' '[Service]' 'MemoryMax=1G' 'Restart=always' 'RestartSec=5s' \
  > "/etc/systemd/system/${runner_service}.d/override.conf"
systemctl daemon-reload
systemctl enable --now "$runner_service"
systemctl is-active --quiet "$runner_service"

echo "cc-service deployment runner is active with label $RUNNER_LABEL."
