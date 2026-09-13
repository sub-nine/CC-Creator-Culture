#!/bin/sh
set -eu

read_secret() {
  name="$1"
  eval "file_path=\${${name}_FILE:-}"
  eval "value=\${${name}:-}"
  if [ -n "$file_path" ]; then
    cat "$file_path"
  elif [ -n "$value" ]; then
    printf '%s' "$value"
  else
    echo "$name or ${name}_FILE is required" >&2
    exit 1
  fi
}

: "${SERVICE:?SERVICE is required (user|product|order)}"

case "$SERVICE" in
  user|product|order) ;;
  *)
    echo "SERVICE must be user, product, or order" >&2
    exit 1
    ;;
esac

if [ -z "${FLYWAY_USER:-}" ] && [ -n "${DB_USER:-}" ]; then
  FLYWAY_USER="$DB_USER"
fi
if [ -z "${FLYWAY_URL:-}" ]; then
  : "${DB_HOST:?FLYWAY_URL or DB_HOST is required}"
  : "${DB_NAME:?FLYWAY_URL or DB_NAME is required}"
  FLYWAY_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}"
fi
: "${FLYWAY_URL:?FLYWAY_URL is required}"
: "${FLYWAY_USER:?FLYWAY_USER or DB_USER is required}"

if [ -z "${FLYWAY_PASSWORD:-}" ] && [ -z "${FLYWAY_PASSWORD_FILE:-}" ]; then
  if [ -n "${DB_PASSWORD_FILE:-}" ]; then
    FLYWAY_PASSWORD_FILE="$DB_PASSWORD_FILE"
  elif [ -n "${DB_PASSWORD:-}" ]; then
    FLYWAY_PASSWORD="$DB_PASSWORD"
  fi
fi

FLYWAY_PASSWORD="$(read_secret FLYWAY_PASSWORD)"
export FLYWAY_PASSWORD

append_ssl() {
  url="$1"
  mode="${FLYWAY_SSLMODE:-}"
  cert="${FLYWAY_SSLROOTCERT:-/certs/rds-global-bundle.pem}"
  if [ -z "$mode" ]; then
    printf '%s' "$url"
    return
  fi
  case "$url" in
    *sslmode=*)
      printf '%s' "$url"
      ;;
    *\?*)
      printf '%s&sslmode=%s&sslrootcert=%s' "$url" "$mode" "$cert"
      ;;
    *)
      printf '%s?sslmode=%s&sslrootcert=%s' "$url" "$mode" "$cert"
      ;;
  esac
}

FLYWAY_URL="$(append_ssl "$FLYWAY_URL")"
export FLYWAY_URL
export FLYWAY_USER

if [ "$SERVICE" = "user" ]; then
  FLYWAY_SCHEMAS=private
  FLYWAY_DEFAULT_SCHEMA=private
else
  FLYWAY_SCHEMAS=public
  FLYWAY_DEFAULT_SCHEMA=public
fi

export FLYWAY_SCHEMAS
export FLYWAY_DEFAULT_SCHEMA
export FLYWAY_LOCATIONS="filesystem:/flyway/sql/${SERVICE}"
export FLYWAY_CONNECT_RETRIES="${FLYWAY_CONNECT_RETRIES:-10}"
export FLYWAY_SKIP_CHECK_FOR_UPDATE=true

echo "flyway migrate service=$SERVICE schema=$FLYWAY_DEFAULT_SCHEMA release=${RELEASE_SHA:-unknown}"

exec flyway -skipCheckForUpdate migrate
