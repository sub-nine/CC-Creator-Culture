#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

export CANDIDATE_SHA=test
export DEV_DOMAIN=dev.example.com
export USER_DB_ADMIN_PASSWORD=user-admin
export PRODUCT_DB_ADMIN_PASSWORD=product-admin
export ORDER_DB_ADMIN_PASSWORD=order-admin
export USER_DB_PASSWORD=user-password
export PRODUCT_DB_PASSWORD=product-password
export ORDER_DB_PASSWORD=order-password
export GRAFANA_ADMIN_PASSWORD=grafana-password
export CONFIG_SERVER_IMAGE=config-server
export EUREKA_SERVER_IMAGE=eureka-server
export GATEWAY_IMAGE=gateway
export USER_SERVICE_IMAGE=user-service
export PRODUCT_SERVICE_IMAGE=product-service
export ORDER_SERVICE_IMAGE=order-service
export POSTGRES_IMAGE=postgres
export REDIS_IMAGE=redis
export KAFKA_IMAGE=kafka
export KAFKA_UI_IMAGE=kafka-ui
export PROMETHEUS_IMAGE=prometheus
export GRAFANA_IMAGE=grafana
export ZIPKIN_IMAGE=zipkin
export CADDY_IMAGE=caddy

deploy_config="$(docker compose \
  --project-directory "$REPOSITORY_ROOT" \
  --env-file "$REPOSITORY_ROOT/deploy/dev.env.example" \
  -f "$REPOSITORY_ROOT/deploy/compose.dev.yml" \
  --profile messaging \
  --profile observability \
  config --format json)"

jq -e '
  . as $root
  | [
      {container:"user-postgres", app:"user-service", database:"user_db", username:"user_app", network:"user-data", volume:"user-postgres-data", url_key:"USER_DATASOURCE_URL"},
      {container:"product-postgres", app:"product-service", database:"product_db", username:"product_app", network:"product-data", volume:"product-postgres-data", url_key:"PRODUCT_DATASOURCE_URL"},
      {container:"order-postgres", app:"order-service", database:"order_db", username:"order_app", network:"order-data", volume:"order-postgres-data", url_key:"ORDER_DATASOURCE_URL"}
    ] as $expected
  | ([.services | keys[] | select(endswith("-postgres"))] | sort) == ["order-postgres", "product-postgres", "user-postgres"]
  and (.services | has("postgres") | not)
  and (.volumes | has("postgres-data") | not)
  and all($expected[];
    . as $item
    | ($root.services[$item.container].environment.POSTGRES_DB == $item.database)
      and ($root.services[$item.container].environment.SERVICE_DB_NAME == $item.database)
      and ($root.services[$item.container].environment.SERVICE_DB_USERNAME == $item.username)
      and (($root.services[$item.container].networks | keys) == [$item.network])
      and (($root.services[$item.container].ports // []) | length == 0)
      and ($root.services[$item.container].mem_limit == "536870912")
      and ($root.services[$item.container].healthcheck.test[1] | contains("pg_isready -h 127.0.0.1"))
      and ($root.services[$item.container].volumes | any(.source == $item.volume))
      and ($root.services[$item.container].volumes | any(.source | endswith("/deploy/postgres/init-service-database.sh")))
      and (($root.services[$item.app].networks | keys | sort) == (["internal", $item.network] | sort))
      and ($root.services[$item.app].depends_on | has($item.container))
      and ($root.services[$item.app].environment[$item.url_key] == ("jdbc:postgresql://" + $item.container + ":5432/" + $item.database))
  )
' <<<"$deploy_config" >/dev/null

local_config="$(docker compose \
  --project-directory "$REPOSITORY_ROOT" \
  --env-file "$REPOSITORY_ROOT/.env.example" \
  -f "$REPOSITORY_ROOT/compose.yaml" \
  config --format json)"

jq -e '
  (.services | has("postgres"))
  and (.services.postgres.environment.POSTGRES_DB == "postgres")
  and (.services.postgres.ports | any(.published == "5432" and .host_ip == "127.0.0.1"))
  and (.volumes | has("postgres-data"))
  and (.volumes | has("user-postgres-data") | not)
  and (.volumes | has("product-postgres-data") | not)
  and (.volumes | has("order-postgres-data") | not)
' <<<"$local_config" >/dev/null

echo "Compose database topology regression tests passed."
