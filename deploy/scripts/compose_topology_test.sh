#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

export CANDIDATE_SHA=test
export CONFIG_SERVER_CONFIG_LABEL=1111111111111111111111111111111111111111
export EUREKA_SERVER_CONFIG_LABEL=2222222222222222222222222222222222222222
export GATEWAY_CONFIG_LABEL=3333333333333333333333333333333333333333
export USER_SERVICE_CONFIG_LABEL=4444444444444444444444444444444444444444
export PRODUCT_SERVICE_CONFIG_LABEL=5555555555555555555555555555555555555555
export ORDER_SERVICE_CONFIG_LABEL=6666666666666666666666666666666666666666
export DEV_DOMAIN=dev.example.com
export POSTGRES_ADMIN_PASSWORD=postgres-admin
export USER_DB_ADMIN_PASSWORD=user-admin
export PRODUCT_DB_ADMIN_PASSWORD=product-admin
export ORDER_DB_ADMIN_PASSWORD=order-admin
export USER_DB_PASSWORD=user-password
export PRODUCT_DB_PASSWORD=product-password
export ORDER_DB_PASSWORD=order-password
export GRAFANA_ADMIN_PASSWORD=grafana-password
export JWT_SECRET=jwt-test-secret
export R2_ACCESS_KEY=test-access
export R2_SECRET_KEY=test-r2-secret
export R2_ENDPOINT=https://example.r2.cloudflarestorage.com
export R2_BUCKET=cc-dev-product
export R2_PUBLIC_URL=https://pub-example.r2.dev
export CONFIG_SERVER_IMAGE=config-server
export EUREKA_SERVER_IMAGE=eureka-server
export GATEWAY_IMAGE=gateway
export USER_SERVICE_IMAGE=user-service
export PRODUCT_SERVICE_IMAGE=product-service
export ORDER_SERVICE_IMAGE=order-service
export EMBEDDING_SERVICE_IMAGE=embedding-service
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

jq -e \
  --arg config_server "$CONFIG_SERVER_CONFIG_LABEL" \
  --arg gateway "$GATEWAY_CONFIG_LABEL" \
  --arg user_service "$USER_SERVICE_CONFIG_LABEL" \
  --arg product_service "$PRODUCT_SERVICE_CONFIG_LABEL" \
  --arg order_service "$ORDER_SERVICE_CONFIG_LABEL" \
  '
  .services["config-server"].environment.SPRING_PROFILES_ACTIVE == "git"
  and .services["config-server"].environment.CONFIG_GIT_DEFAULT_LABEL == $config_server
  and .services.gateway.environment.SPRING_CLOUD_CONFIG_LABEL == $gateway
  and .services["user-service"].environment.SPRING_CLOUD_CONFIG_LABEL == $user_service
  and .services["product-service"].environment.SPRING_CLOUD_CONFIG_LABEL == $product_service
  and .services["order-service"].environment.SPRING_CLOUD_CONFIG_LABEL == $order_service
  and .services["user-service"].environment.REDIS_HOST == "redis"
  and .services["product-service"].environment.REDIS_HOST == "redis"
  and .services["order-service"].environment.REDIS_HOST == "redis"
  and .services.gateway.environment.REDIS_HOST == "redis"
  and .services["user-service"].environment.JWT_SECRET == "jwt-test-secret"
  and .services.gateway.environment.JWT_SECRET == "jwt-test-secret"
  and .services["product-service"].environment.R2_ACCESS_KEY == "test-access"
  and .services["product-service"].environment.R2_SECRET_KEY == "test-r2-secret"
  and .services["product-service"].environment.R2_ENDPOINT == "https://example.r2.cloudflarestorage.com"
  and .services["product-service"].environment.R2_BUCKET == "cc-dev-product"
  and .services["product-service"].environment.R2_PUBLIC_URL == "https://pub-example.r2.dev"
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
