#!/bin/sh

set -eu

: "${USER_DB_NAME:?USER_DB_NAME is required}"
: "${USER_DB_USERNAME:?USER_DB_USERNAME is required}"
: "${USER_DB_PASSWORD:?USER_DB_PASSWORD is required}"
: "${PRODUCT_DB_NAME:?PRODUCT_DB_NAME is required}"
: "${PRODUCT_DB_USERNAME:?PRODUCT_DB_USERNAME is required}"
: "${PRODUCT_DB_PASSWORD:?PRODUCT_DB_PASSWORD is required}"
: "${ORDER_DB_NAME:?ORDER_DB_NAME is required}"
: "${ORDER_DB_USERNAME:?ORDER_DB_USERNAME is required}"
: "${ORDER_DB_PASSWORD:?ORDER_DB_PASSWORD is required}"

create_service_database() {
  database_name="$1"
  database_user="$2"
  database_password="$3"

  psql \
    --set=ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=database_name="$database_name" \
    --set=database_user="$database_user" \
    --set=database_password="$database_password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'database_user', :'database_password')
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_roles
  WHERE rolname = :'database_user'
)
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'database_user')
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_database
  WHERE datname = :'database_name'
)
\gexec
SQL

  echo "Initialized PostgreSQL database: $database_name"
}

create_service_database "$USER_DB_NAME" "$USER_DB_USERNAME" "$USER_DB_PASSWORD"
create_service_database "$PRODUCT_DB_NAME" "$PRODUCT_DB_USERNAME" "$PRODUCT_DB_PASSWORD"
create_service_database "$ORDER_DB_NAME" "$ORDER_DB_USERNAME" "$ORDER_DB_PASSWORD"
