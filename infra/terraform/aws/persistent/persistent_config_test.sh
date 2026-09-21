#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
cfg="$root"

required=(
  name_prefix region vpc_id
  public_subnet_ids app_subnet_ids data_subnet_ids
  private_route_table_id
  config-server eureka-server gateway user-service product-service order-service
  alb observation kafka redis migration embedding-service k6
  rds_instances db_endpoints
  rds_master jwt redis
  ecs_execution ecs_task
  observation_instance_id observation_images
  kafka_instance_id kafka_image
  artifact_repository_urls
  management_port
  grafana seed
  r2 product-r2
  observation_bootstrap_document
  kafka_bootstrap_document
  start-observation
  start-kafka
  password_hash
)

missing=0
for key in "${required[@]}"; do
  if ! grep -rq --include='*.tf' --include='*.sh' -F "$key" "$cfg"; then
    echo "missing persistent_config key: $key" >&2
    missing=1
  fi
done

if grep -q 'merge(' "$cfg/outputs.tf"; then
  echo "security_groups must be an object, not merge()" >&2
  missing=1
fi

test "$missing" -eq 0
echo "persistent_config required keys present."
