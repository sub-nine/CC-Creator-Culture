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
: "${DB_HOST:?DB_HOST is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${ADMIN_USER:?ADMIN_USER is required}"
: "${APP_USER:?APP_USER is required}"

case "$SERVICE" in
  user|product|order) ;;
  *)
    echo "SERVICE must be user, product, or order" >&2
    exit 1
    ;;
esac

DB_PORT="${DB_PORT:-5432}"
if [ -n "${PGSSLMODE:-}" ]; then
  export PGSSLMODE
fi
if [ "${PGSSLMODE:-}" = "verify-full" ] || [ "${PGSSLMODE:-}" = "verify-ca" ]; then
  export PGSSLROOTCERT="${PGSSLROOTCERT:-/certs/rds-global-bundle.pem}"
fi
export PGHOST="$DB_HOST"
export PGPORT="$DB_PORT"
export PGDATABASE="$DB_NAME"
export PGUSER="$ADMIN_USER"
PGPASSWORD="$(read_secret ADMIN_PASSWORD)"
export PGPASSWORD
APP_PASSWORD="$(read_secret APP_PASSWORD)"

psql --set=ON_ERROR_STOP=1 \
  --set=app_user="$APP_USER" \
  --set=app_password="$APP_PASSWORD" \
  --set=db_name="$DB_NAME" \
  --set=service="$SERVICE" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = :'app_user'
)
\gexec

SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password') \gexec
SELECT format('GRANT CONNECT, CREATE, TEMPORARY ON DATABASE %I TO %I', :'db_name', :'app_user') \gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'db_name') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'app_user') \gexec

SELECT format('CREATE SCHEMA IF NOT EXISTS private AUTHORIZATION %I', :'app_user')
WHERE :'service' = 'user'
  AND NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'private')
\gexec

SELECT format('ALTER SCHEMA private OWNER TO %I', :'app_user')
WHERE :'service' = 'user'
  AND EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'private')
\gexec

SELECT format('GRANT USAGE, CREATE ON SCHEMA private TO %I', :'app_user')
WHERE :'service' = 'user'
\gexec

SELECT format('ALTER ROLE %I IN DATABASE %I SET search_path TO private, public', :'app_user', :'db_name')
WHERE :'service' = 'user'
\gexec
SQL

echo "bootstrapped service=$SERVICE db=$DB_NAME app_user=$APP_USER"
