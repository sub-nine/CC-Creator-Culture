locals {
  embedding_url = "http://${var.persistent_config.cloudmap.dns_names["embedding-service"]}:8000"
}

resource "aws_ecs_task_definition" "embedding" {
  family                   = "${var.name_prefix}-embedding-service"
  cpu                      = 512
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
      name         = "embedding-service"
      image        = "${var.persistent_config.ecr_repository_urls["embedding-service"]}:${var.image_tags["embedding-service"]}"
      essential    = true
      cpu          = 512
      memory       = 2048
      portMappings = [{ containerPort = 8000, protocol = "tcp" }]
      healthCheck = {
        command     = ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000/docs', timeout=4)"]
        interval    = 10
        timeout     = 5
        retries     = 12
        startPeriod = 120
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.persistent_config.log_groups["embedding-service"]
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "embedding-service"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "embedding" {
  name                               = "${var.name_prefix}-embedding-service"
  cluster                            = aws_ecs_cluster.this.id
  task_definition                    = aws_ecs_task_definition.embedding.arn
  desired_count                      = local.desired_count
  wait_for_steady_state              = true
  availability_zone_rebalancing      = "ENABLED"
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.persistent_config.app_subnet_ids
    security_groups  = [local.sg["embedding-service"]]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = local.cloudmap_service_arns["embedding-service"]
  }

  depends_on = [aws_ecs_cluster_capacity_providers.this, aws_nat_gateway.this, aws_route.private_nat]
}
