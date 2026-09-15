locals {
  db_oneoff_cpu    = 256
  db_oneoff_memory = 512
}

# One seed task per service. run-task overrides cannot inject secrets, so each definition carries
# its own DB endpoint, RDS master credentials, and the seed password hash.
resource "aws_ecs_task_definition" "db_seed" {
  for_each                 = toset(local.db_keys)
  family                   = "${var.name_prefix}-db-seed-${each.key}"
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
      image       = local.db_seed_image
      essential   = true
      cpu         = local.db_oneoff_cpu
      memory      = local.db_oneoff_memory
      stopTimeout = 90
      entryPoint  = ["/scripts/seed.sh"]
      environment = [
        { name = "PGSSLMODE", value = "verify-full" },
        { name = "SERVICE", value = replace(each.key, "-service", "") },
        { name = "DB_HOST", value = var.persistent_config.db_endpoints[each.key] },
        { name = "DB_PORT", value = tostring(var.persistent_config.db_port) },
        { name = "DB_NAME", value = local.db_names[each.key] },
      ]
      secrets = [
        { name = "DB_USER", valueFrom = "${var.persistent_config.secret_arns.rds_master[each.key]}:username::" },
        { name = "DB_PASSWORD", valueFrom = "${var.persistent_config.secret_arns.rds_master[each.key]}:password::" },
        { name = "SEED_PASSWORD_HASH", valueFrom = "${var.persistent_config.secret_arns.seed}:password_hash::" },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.persistent_config.log_groups[each.key]
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "db-seed"
        }
      }
    }
  ])
}
