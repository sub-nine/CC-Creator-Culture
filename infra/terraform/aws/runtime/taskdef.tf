locals {
  config_import = "configserver:http://${local.config_dns}:${local.services["config-server"].port}"
  eureka_zone   = "http://${local.eureka_dns}:${local.services["eureka-server"].port}/eureka/"
  redis_host    = aws_elasticache_replication_group.this.primary_endpoint_address
  kafka_brokers = data.aws_msk_bootstrap_brokers.this.bootstrap_brokers_sasl_scram
  zipkin_endpoint = format(
    "http://%s:%s/api/v2/spans",
    coalesce(var.persistent_config.observation_private_ip, data.aws_instance.observation.private_ip),
    var.persistent_config.zipkin_port,
  )

  container_environment = {
    for name, svc in local.services : name => concat(
      [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = local.port_env[name], value = tostring(svc.port) },
        { name = "JAVA_TOOL_OPTIONS", value = svc.cpu < 512 ? "-Xms64m -Xmx256m -XX:+ExitOnOutOfMemoryError" : "-Xms128m -Xmx512m -XX:+ExitOnOutOfMemoryError" },
        { name = "MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENDPOINT", value = local.zipkin_endpoint },
      ],
      name == "config-server" ? [
        { name = "CONFIG_GIT_URI", value = "https://github.com/sub-nine/CC-Creator-Culture.git" },
        ] : [
        { name = "CONFIG_SERVER_IMPORT", value = local.config_import },
        { name = "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE", value = local.eureka_zone },
      ],
      contains(local.redis_clients, name) ? [
        { name = "REDIS_HOST", value = local.redis_host },
        { name = "REDIS_PORT", value = "6379" },
        { name = "REDIS_USERNAME", value = var.redis_username },
      ] : [],
      contains(local.kafka_clients, name) ? [
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = local.kafka_brokers },
        { name = "KAFKA_SASL_MECHANISM", value = "SCRAM-SHA-512" },
      ] : [],
      contains(local.db_keys, name) ? [
        { name = local.db_host_env[name], value = var.persistent_config.db_endpoints[name] },
        { name = local.db_port_env[name], value = "5432" },
        { name = local.db_name_env[name], value = local.db_names[name] },
      ] : [],
    )
  }

  container_secrets = {
    for name, svc in local.services : name => concat(
      name == "gateway" || name == "user-service" ? [
        { name = "JWT_SECRET", valueFrom = var.persistent_config.secret_arns.jwt },
      ] : [],
      contains(local.redis_clients, name) ? [
        { name = "REDIS_PASSWORD", valueFrom = var.persistent_config.secret_arns.redis },
      ] : [],
      contains(local.kafka_clients, name) ? [
        { name = "KAFKA_USERNAME", valueFrom = "${var.persistent_config.secret_arns.kafka[name]}:username::" },
        { name = "KAFKA_PASSWORD", valueFrom = "${var.persistent_config.secret_arns.kafka[name]}:password::" },
      ] : [],
      contains(local.db_keys, name) ? [
        { name = local.db_user_env[name], valueFrom = "${var.persistent_config.secret_arns.app[name]}:username::" },
        { name = local.db_password_env[name], valueFrom = "${var.persistent_config.secret_arns.app[name]}:password::" },
      ] : [],
      name == "product-service" ? [
        { name = "R2_ACCESS_KEY", valueFrom = "${var.persistent_config.secret_arns.r2}:access_key::" },
        { name = "R2_SECRET_KEY", valueFrom = "${var.persistent_config.secret_arns.r2}:secret_key::" },
        { name = "R2_ENDPOINT", valueFrom = "${var.persistent_config.secret_arns.r2}:endpoint::" },
        { name = "R2_BUCKET", valueFrom = "${var.persistent_config.secret_arns.r2}:bucket::" },
        { name = "R2_PUBLIC_URL", valueFrom = "${var.persistent_config.secret_arns.r2}:public_url::" },
      ] : [],
    )
  }
}

resource "aws_ecs_task_definition" "service" {
  for_each                 = local.services
  family                   = "${var.name_prefix}-${each.key}"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
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
      name        = each.key
      image       = var.service_images[each.key]
      essential   = true
      cpu         = each.value.cpu
      memory      = each.value.memory
      stopTimeout = 90
      portMappings = [
        {
          containerPort = each.value.port
          protocol      = "tcp"
        },
        {
          containerPort = local.management_port
          protocol      = "tcp"
        },
      ]
      environment = local.container_environment[each.key]
      secrets     = local.container_secrets[each.key]
      healthCheck = {
        command     = ["CMD-SHELL", "curl -fsS http://localhost:${local.management_port}${local.readiness_path} || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 5
        startPeriod = 120
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.persistent_config.log_groups[each.key]
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = each.key
        }
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }
}
