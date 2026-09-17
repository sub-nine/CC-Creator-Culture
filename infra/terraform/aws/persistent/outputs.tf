output "persistent_config" {
  description = "Runtime input object. IDs are empty until apply. Secret values are not included."

  value = {
    name_prefix = var.name_prefix
    region      = var.aws_region
    vpc_id      = aws_vpc.this.id

    public_subnet_ids      = [for az in local.azs : aws_subnet.public[az].id]
    app_subnet_ids         = [for az in slice(local.azs, 0, 2) : aws_subnet.app[az].id]
    data_subnet_ids        = [for az in local.azs : aws_subnet.data[az].id]
    public_route_table_id  = aws_route_table.public.id
    private_route_table_id = aws_route_table.private.id
    internet_gateway_id    = aws_internet_gateway.this.id

    app_ports       = local.app_ports
    management_port = local.management_port
    zipkin_port     = local.zipkin_port
    db_port         = 5432
    db_names        = local.db_names

    security_groups = {
      config-server     = aws_security_group.app["config-server"].id
      eureka-server     = aws_security_group.app["eureka-server"].id
      gateway           = aws_security_group.app["gateway"].id
      user-service      = aws_security_group.app["user-service"].id
      product-service   = aws_security_group.app["product-service"].id
      order-service     = aws_security_group.app["order-service"].id
      embedding-service = aws_security_group.app["embedding-service"].id
      k6                = aws_security_group.app["k6"].id
      alb               = aws_security_group.alb.id
      observation       = aws_security_group.observation.id
      kafka             = aws_security_group.kafka.id
      redis             = aws_security_group.redis.id
      migration         = aws_security_group.migration.id
      rds = {
        user-service    = aws_security_group.rds["user-service"].id
        product-service = aws_security_group.rds["product-service"].id
        order-service   = aws_security_group.rds["order-service"].id
      }
    }

    rds_instances = { for name, db in aws_db_instance.service : name => db.identifier }
    db_endpoints  = { for name, db in aws_db_instance.service : name => db.address }

    secret_arns = {
      rds_master = { for name, db in aws_db_instance.service : name => db.master_user_secret[0].secret_arn }
      jwt        = aws_secretsmanager_secret.jwt.arn
      redis      = aws_secretsmanager_secret.redis.arn
      grafana    = aws_secretsmanager_secret.grafana.arn
      seed       = aws_secretsmanager_secret.seed.arn
      r2         = aws_secretsmanager_secret.product_r2.arn
    }

    roles = {
      ecs_execution = aws_iam_role.ecs_execution.arn
      ecs_task      = aws_iam_role.ecs_task.arn
      observation   = aws_iam_role.observation.arn
      kafka         = aws_iam_role.kafka.arn
    }

    cloudmap = {
      namespace_id   = aws_service_discovery_private_dns_namespace.this.id
      namespace_name = aws_service_discovery_private_dns_namespace.this.name
      hosted_zone_id = aws_service_discovery_private_dns_namespace.this.hosted_zone
      service_ids = {
        for name, svc in aws_service_discovery_service.core : name => svc.id
      }
      dns_names = {
        config-server     = "config-server.${aws_service_discovery_private_dns_namespace.this.name}"
        eureka-server     = "eureka-server.${aws_service_discovery_private_dns_namespace.this.name}"
        kafka             = "kafka.${aws_service_discovery_private_dns_namespace.this.name}"
        embedding-service = "embedding-service.${aws_service_discovery_private_dns_namespace.this.name}"
      }
    }

    observation_instance_id = aws_instance.observation.id
    observation_images      = local.observation_images
    kafka_instance_id       = aws_instance.kafka.id
    kafka_image             = local.kafka_image

    observation_bootstrap_document = {
      name    = aws_ssm_document.start_observation.name
      version = aws_ssm_document.start_observation.latest_version
    }

    kafka_bootstrap_document = {
      name    = aws_ssm_document.start_kafka.name
      version = aws_ssm_document.start_kafka.latest_version
    }

    log_groups = { for name, group in aws_cloudwatch_log_group.app : name => group.name }

    ecr_repository_urls      = data.terraform_remote_state.bootstrap.outputs.ecr_repository_urls
    ecr_repository_arns      = data.terraform_remote_state.bootstrap.outputs.ecr_repository_arns
    artifact_repository_urls = data.terraform_remote_state.bootstrap.outputs.artifact_repository_urls
    artifact_repository_arns = data.terraform_remote_state.bootstrap.outputs.artifact_repository_arns
    release_bucket           = data.terraform_remote_state.bootstrap.outputs.release_bucket
    selected_manifest_key    = data.terraform_remote_state.bootstrap.outputs.selected_manifest_key
  }
}

output "observation_bootstrap_document" {
  value = {
    name    = aws_ssm_document.start_observation.name
    version = aws_ssm_document.start_observation.latest_version
  }
}
