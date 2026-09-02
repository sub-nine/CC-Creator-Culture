#!/bin/sh
set -eu

: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${SERVICE_DB_NAME:?SERVICE_DB_NAME is required}"
: "${SERVICE_DB_USERNAME:?SERVICE_DB_USERNAME is required}"
: "${SERVICE_DB_PASSWORD:?SERVICE_DB_PASSWORD is required}"

if [ "$POSTGRES_DB" != "$SERVICE_DB_NAME" ]; then
  echo "POSTGRES_DB and SERVICE_DB_NAME must match." >&2
  exit 65
fi

psql \
  --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$SERVICE_DB_NAME" \
  --set=postgres_user="$POSTGRES_USER" \
  --set=service_db_name="$SERVICE_DB_NAME" \
  --set=service_db_username="$SERVICE_DB_USERNAME" \
  --set=service_db_password="$SERVICE_DB_PASSWORD" <<'SQL'
SET log_min_error_statement = PANIC;

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'service_db_username', :'service_db_password')
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_roles
  WHERE rolname = :'service_db_username'
)
\gexec

SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'service_db_username', :'service_db_password') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'service_db_name', :'service_db_username') \gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'service_db_name') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'service_db_name', :'service_db_username') \gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'service_db_username') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'service_db_username') \gexec
SQL

echo "Initialized isolated PostgreSQL database: $SERVICE_DB_NAME"
