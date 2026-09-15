locals {
  common_tags = {
    Project     = "cc-service"
    Environment = "test"
    ManagedBy   = "terraform"
    Stack       = "runtime"
  }

  app_keys = [
    "config-server",
    "eureka-server",
    "gateway",
    "user-service",
    "product-service",
    "order-service",
  ]

  db_keys = [
    "user-service",
    "product-service",
    "order-service",
  ]

  kafka_clients = [
    "user-service",
    "product-service",
    "order-service",
  ]

  redis_clients = [
    "gateway",
    "user-service",
    "product-service",
    "order-service",
  ]

  management_port = 9090
  readiness_path  = "/actuator/health/readiness"

  services = {
    config-server = {
      port   = 8888
      cpu    = 256
      memory = 512
    }
    eureka-server = {
      port   = 8761
      cpu    = 256
      memory = 512
    }
    gateway = {
      port   = 8080
      cpu    = 512
      memory = 1024
    }
    user-service = {
      port   = 8081
      cpu    = 512
      memory = 1024
    }
    product-service = {
      port   = 8082
      cpu    = 512
      memory = 1024
    }
    order-service = {
      port   = 8083
      cpu    = 512
      memory = 1024
    }
  }

  db_names = {
    user-service    = "user_db"
    product-service = "product_db"
    order-service   = "order_db"
  }

  db_host_env = {
    user-service    = "USER_DB_HOST"
    product-service = "PRODUCT_DB_HOST"
    order-service   = "ORDER_DB_HOST"
  }

  db_port_env = {
    user-service    = "USER_DB_PORT"
    product-service = "PRODUCT_DB_PORT"
    order-service   = "ORDER_DB_PORT"
  }

  db_name_env = {
    user-service    = "USER_DB_NAME"
    product-service = "PRODUCT_DB_NAME"
    order-service   = "ORDER_DB_NAME"
  }

  db_user_env = {
    user-service    = "USER_DB_USERNAME"
    product-service = "PRODUCT_DB_USERNAME"
    order-service   = "ORDER_DB_USERNAME"
  }

  db_password_env = {
    user-service    = "USER_DB_PASSWORD"
    product-service = "PRODUCT_DB_PASSWORD"
    order-service   = "ORDER_DB_PASSWORD"
  }

  port_env = {
    config-server   = "SERVER_PORT"
    eureka-server   = "SERVER_PORT"
    gateway         = "GATEWAY_PORT"
    user-service    = "USER_SERVICE_PORT"
    product-service = "PRODUCT_SERVICE_PORT"
    order-service   = "ORDER_SERVICE_PORT"
  }

  sg             = var.persistent_config.security_groups
  release_bucket = coalesce(var.release_bucket, var.persistent_config.release_bucket)
  account_id     = split(":", var.persistent_config.roles.ecs_execution)[4]

  service_images = {
    for name in local.app_keys : name => "${var.persistent_config.ecr_repository_urls[name]}:${var.image_tags[name]}"
  }
  db_seed_image = "${var.persistent_config.artifact_repository_urls["db-seed"]}:${var.image_tags["db-seed"]}"

  config_dns = var.persistent_config.cloudmap.dns_names["config-server"]
  eureka_dns = var.persistent_config.cloudmap.dns_names["eureka-server"]

  cloudmap_service_arns = {
    for name, id in var.persistent_config.cloudmap.service_ids :
    name => "arn:aws:servicediscovery:${var.aws_region}:${local.account_id}:service/${id}"
  }
}
