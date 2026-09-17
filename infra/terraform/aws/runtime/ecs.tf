locals {
  platform_keys    = toset(["config-server", "eureka-server"])
  app_service_keys = toset(["user-service", "product-service", "order-service"])

  desired_count = var.app_running ? 1 : 0

  autoscaling_max = {
    config-server   = 1
    eureka-server   = 1
    gateway         = 4
    user-service    = 4
    product-service = 4
    order-service   = 6
  }
}

resource "aws_ecs_cluster" "this" {
  name = var.name_prefix

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

# Start order: platform (config-server, eureka-server) -> app (user, product, order) -> gateway.
# wait_for_steady_state makes each group reach steady state before the next group is created or updated.

resource "aws_ecs_service" "platform" {
  for_each                           = local.platform_keys
  name                               = "${var.name_prefix}-${each.key}"
  cluster                            = aws_ecs_cluster.this.id
  task_definition                    = aws_ecs_task_definition.service[each.key].arn
  desired_count                      = local.desired_count
  wait_for_steady_state              = true
  availability_zone_rebalancing      = "ENABLED"
  enable_execute_command             = false
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
    security_groups  = [local.sg[each.key]]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = local.cloudmap_service_arns[each.key]
  }

  depends_on = [
    aws_ecs_cluster_capacity_providers.this,
    aws_nat_gateway.this,
    aws_route.private_nat,
    aws_rds_instance_state.service,
    aws_ec2_instance_state.kafka,
  ]
}

resource "aws_ecs_service" "app" {
  for_each                           = local.app_service_keys
  name                               = "${var.name_prefix}-${each.key}"
  cluster                            = aws_ecs_cluster.this.id
  task_definition                    = aws_ecs_task_definition.service[each.key].arn
  desired_count                      = local.desired_count
  wait_for_steady_state              = true
  availability_zone_rebalancing      = "ENABLED"
  enable_execute_command             = false
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
    security_groups  = [local.sg[each.key]]
    assign_public_ip = false
  }

  depends_on = [aws_ecs_service.platform, aws_ecs_service.embedding]
}

resource "aws_ecs_service" "gateway" {
  name                               = "${var.name_prefix}-gateway"
  cluster                            = aws_ecs_cluster.this.id
  task_definition                    = aws_ecs_task_definition.service["gateway"].arn
  desired_count                      = local.desired_count
  wait_for_steady_state              = true
  health_check_grace_period_seconds  = 180
  availability_zone_rebalancing      = "ENABLED"
  enable_execute_command             = false
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
    security_groups  = [local.sg["gateway"]]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.gateway.arn
    container_name   = "gateway"
    container_port   = local.services["gateway"].port
  }

  depends_on = [aws_ecs_service.app, aws_lb_listener.https]
}

locals {
  ecs_service_names = merge(
    { for name, svc in aws_ecs_service.platform : name => svc.name },
    { for name, svc in aws_ecs_service.app : name => svc.name },
    { gateway = aws_ecs_service.gateway.name },
  )
}

resource "aws_appautoscaling_target" "service" {
  for_each           = local.services
  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${local.ecs_service_names[each.key]}"
  min_capacity       = local.desired_count
  max_capacity       = local.autoscaling_max[each.key]
}

# Power state. app_running=false stops RDS and the observation/kafka EC2 together with the services.

resource "aws_rds_instance_state" "service" {
  for_each   = var.persistent_config.rds_instances
  identifier = each.value
  state      = var.app_running ? "available" : "stopped"
}

resource "aws_ec2_instance_state" "observation" {
  instance_id = var.persistent_config.observation_instance_id
  state       = var.app_running ? "running" : "stopped"
}

resource "aws_ec2_instance_state" "kafka" {
  instance_id = var.persistent_config.kafka_instance_id
  state       = var.app_running ? "running" : "stopped"
}
