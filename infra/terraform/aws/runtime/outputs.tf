locals {
  deployment_config = {
    region   = var.aws_region
    cluster  = aws_ecs_cluster.this.name
    services = { for name in local.app_keys : name => aws_ecs_service.service[name].name }
    service_task_families = {
      for name in local.app_keys : name => aws_ecs_task_definition.service[name].family
    }
    subnets = var.persistent_config.app_subnet_ids
    security_groups = {
      config-server       = local.sg["config-server"]
      eureka-server       = local.sg["eureka-server"]
      gateway             = local.sg["gateway"]
      user-service        = local.sg["user-service"]
      product-service     = local.sg["product-service"]
      order-service       = local.sg["order-service"]
      alb                 = local.sg["alb"]
      observation         = local.sg["observation"]
      msk                 = local.sg["msk"]
      redis               = local.sg["redis"]
      migration           = local.sg["migration"]
      rds-user-service    = local.sg.rds["user-service"]
      rds-product-service = local.sg.rds["product-service"]
      rds-order-service   = local.sg.rds["order-service"]
    }
    release_bucket                 = local.release_bucket
    rds_instances                  = var.persistent_config.rds_instances
    observation_instance_id        = var.persistent_config.observation_instance_id
    msk_arn                        = aws_msk_cluster.this.arn
    redis_id                       = aws_elasticache_replication_group.this.id
    target_group_arn               = aws_lb_target_group.gateway.arn
    db_endpoints                   = var.persistent_config.db_endpoints
    secret_arns                    = var.persistent_config.secret_arns
    observation_private_ip         = coalesce(var.persistent_config.observation_private_ip, data.aws_instance.observation.private_ip)
    observation_bootstrap_document = var.persistent_config.observation_bootstrap_document
    db_names                       = var.persistent_config.db_names
    db_admin_users = {
      for name in local.db_keys : name => local.db_admin_user
    }
    db_app_users = local.db_app_users
    migrate_task_families = {
      for name in local.db_keys : name => aws_ecs_task_definition.migrate[name].family
    }
    bootstrap_task_family = aws_ecs_task_definition.db_bootstrap.family
    seed_task_family      = aws_ecs_task_definition.db_seed.family
    artifact_images       = var.artifact_images
    db_execution_role_arn = var.persistent_config.roles.ecs_execution
    db_task_role_arn      = var.persistent_config.roles.ecs_task
  }
}

output "deployment_config" {
  value = local.deployment_config
}

output "redis_user_group_id" {
  value = var.redis_user_group_id
}
