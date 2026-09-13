locals {
  db_admin_user = "cc_master"
  db_app_users = {
    user-service    = "user_app"
    product-service = "product_app"
    order-service   = "order_app"
  }
  db_oneoff_cpu    = 256
  db_oneoff_memory = 512
}

resource "aws_ecs_task_definition" "migrate" {
  for_each                 = toset(local.db_keys)
  family                   = "${var.name_prefix}-db-migrate-${each.key}"
  cpu                      = local.db_oneoff_cpu
  memory                   = local.db_oneoff_memory
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = var.persistent_config.roles.ecs_execution
  task_role_arn            = var.persistent_config.roles.ecs_task

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name        = "migrate"
      image       = var.artifact_images["db-migrate"]
      essential   = true
      cpu         = local.db_oneoff_cpu
      memory      = local.db_oneoff_memory
      stopTimeout = 90
      entryPoint  = ["/flyway/scripts/migrate.sh"]
      environment = [
        { name = "SERVICE", value = replace(each.key, "-service", "") },
        { name = "DB_HOST", value = var.persistent_config.db_endpoints[each.key] },
        { name = "DB_PORT", value = tostring(var.persistent_config.db_port) },
        { name = "DB_NAME", value = var.persistent_config.db_names[each.key] },
        { name = "DB_USER", value = local.db_app_users[each.key] },
        { name = "FLYWAY_USER", value = local.db_app_users[each.key] },
        { name = "FLYWAY_SSLMODE", value = "verify-full" },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.persistent_config.log_groups[each.key]
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "db-migrate"
        }
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }
}

resource "aws_ecs_task_definition" "db_bootstrap" {
  family                   = "${var.name_prefix}-db-bootstrap"
  cpu                      = local.db_oneoff_cpu
  memory                   = local.db_oneoff_memory
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = var.persistent_config.roles.ecs_execution
  task_role_arn            = var.persistent_config.roles.ecs_task

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name        = "bootstrap"
      image       = var.artifact_images["db-seed"]
      essential   = true
      cpu         = local.db_oneoff_cpu
      memory      = local.db_oneoff_memory
      stopTimeout = 90
      entryPoint  = ["/scripts/bootstrap-roles.sh"]
      environment = [
        { name = "PGSSLMODE", value = "verify-full" },
        { name = "ADMIN_USER", value = local.db_admin_user },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.persistent_config.log_groups["user-service"]
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "db-bootstrap"
        }
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }
}

resource "aws_ecs_task_definition" "db_seed" {
  family                   = "${var.name_prefix}-db-seed"
  cpu                      = local.db_oneoff_cpu
  memory                   = local.db_oneoff_memory
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = var.persistent_config.roles.ecs_execution
  task_role_arn            = var.persistent_config.roles.ecs_task

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name        = "seed"
      image       = var.artifact_images["db-seed"]
      essential   = true
      cpu         = local.db_oneoff_cpu
      memory      = local.db_oneoff_memory
      stopTimeout = 90
      entryPoint  = ["/scripts/seed.sh"]
      environment = [
        { name = "PGSSLMODE", value = "verify-full" },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.persistent_config.log_groups["user-service"]
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "db-seed"
        }
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }
}
