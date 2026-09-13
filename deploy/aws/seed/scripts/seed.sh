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
: "${SEED_RUN_ID:?SEED_RUN_ID is required}"
: "${DB_HOST:?DB_HOST is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USER:?DB_USER is required}"
: "${SEED_PASSWORD_HASH:?SEED_PASSWORD_HASH is required; no default login password is used}"

# shellcheck disable=SC2016 # literal $ separators in the BCrypt pattern
if ! printf '%s\n' "$SEED_PASSWORD_HASH" | grep -Eq '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'; then
  echo "SEED_PASSWORD_HASH must be a BCrypt password hash" >&2
  exit 1
fi

case "$SERVICE" in
  user|product|order) ;;
  *)
    echo "SERVICE must be user, product, or order" >&2
    exit 1
    ;;
esac

SEED_DIR="$(CDPATH='' cd -- "$(dirname "$0")/.." && pwd)"
if [ ! -f "$SEED_DIR/fixtures.env" ]; then
  SEED_DIR=/seed
fi

# shellcheck disable=SC1091
. "$SEED_DIR/fixtures.env"

FIXTURE_PASSWORD_HASH="$SEED_PASSWORD_HASH"

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
export PGUSER="$DB_USER"
PGPASSWORD="$(read_secret DB_PASSWORD)"
export PGPASSWORD

psql --set=ON_ERROR_STOP=1 \
  --set=run_id="$SEED_RUN_ID" \
  --set=service="$SERVICE" <<'SQL'
CREATE SCHEMA IF NOT EXISTS ops;
CREATE TABLE IF NOT EXISTS ops.seed_runs (
    run_id text NOT NULL,
    service text NOT NULL,
    applied_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, service)
);
INSERT INTO ops.seed_runs (run_id, service)
VALUES (:'run_id', :'service')
ON CONFLICT (run_id, service) DO NOTHING;
SQL

psql --set=ON_ERROR_STOP=1 \
  --set=master_user_id="$FIXTURE_MASTER_USER_ID" \
  --set=manager_user_id="$FIXTURE_MANAGER_USER_ID" \
  --set=creator_user_id="$FIXTURE_CREATOR_USER_ID" \
  --set=customer_user_id="$FIXTURE_CUSTOMER_USER_ID" \
  --set=creator_id="$FIXTURE_CREATOR_ID" \
  --set=category_id="$FIXTURE_CATEGORY_ID" \
  --set=hashtag_id="$FIXTURE_HASHTAG_ID" \
  --set=category_hashtag_id="$FIXTURE_CATEGORY_HASHTAG_ID" \
  --set=product_id="$FIXTURE_PRODUCT_ID" \
  --set=sku_id="$FIXTURE_SKU_ID" \
  --set=stock_id="$FIXTURE_STOCK_ID" \
  --set=hashtag_product_id="$FIXTURE_HASHTAG_PRODUCT_ID" \
  --set=coupon_id="$FIXTURE_COUPON_ID" \
  --set=unique_version="$FIXTURE_UNIQUE_VERSION" \
  --set=password_hash="$FIXTURE_PASSWORD_HASH" \
  -f "$SEED_DIR/sql/${SERVICE}.sql"

echo "seed applied service=$SERVICE run_id=$SEED_RUN_ID"
