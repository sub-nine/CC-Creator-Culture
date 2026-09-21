resource "aws_ecs_task_definition" "k6" {
  family                   = "${var.name_prefix}-k6"
  cpu                      = 1024
  memory                   = 2048
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = var.persistent_config.roles.ecs_execution

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = "k6"
      image     = "${var.persistent_config.artifact_repository_urls["k6"]}:${var.image_tags["k6"]}"
      essential = true
      cpu       = 1024
      memory    = 2048
      command   = ["version"]
      environment = [
        { name = "TARGET", value = "prod" },
        { name = "BASE_URL", value = "https://${var.api_hostname}" },
        { name = "EMBEDDING_SERVICE_URL", value = local.embedding_url },
        { name = "K6_PROMETHEUS_RW_SERVER_URL", value = "http://${coalesce(var.persistent_config.observation_private_ip, data.aws_instance.observation.private_ip)}:9090/api/v1/write" },
        { name = "K6_PROMETHEUS_RW_TREND_STATS", value = "avg,min,med,max,p(90),p(95),p(99)" },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.persistent_config.log_groups["k6"]
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "k6"
        }
      }
    }
  ])
}

output "k6_runner" {
  description = "Manual k6 RunTask inputs. This output does not start a task."
  value = {
    region            = var.aws_region
    cluster           = aws_ecs_cluster.this.arn
    task_definition   = aws_ecs_task_definition.k6.arn
    subnet_ids        = var.persistent_config.app_subnet_ids
    security_group_id = local.sg["k6"]
    base_url          = "https://${var.api_hostname}"
    embedding_url     = local.embedding_url
    prometheus_url    = "http://${coalesce(var.persistent_config.observation_private_ip, data.aws_instance.observation.private_ip)}:9090/api/v1/write"
  }
}
