locals {
  common_tags = {
    Project     = "cc-service"
    Environment = "test"
    ManagedBy   = "terraform"
    Stack       = "persistent"
  }

  app_keys = toset([
    "config-server",
    "eureka-server",
    "gateway",
    "user-service",
    "product-service",
    "order-service",
  ])

  db_keys = toset([
    "user-service",
    "product-service",
    "order-service",
  ])

  app_ports = {
    config-server   = 8888
    eureka-server   = 8761
    gateway         = 8080
    user-service    = 8081
    product-service = 8082
    order-service   = 8083
  }

  db_names = {
    user-service    = "user_db"
    product-service = "product_db"
    order-service   = "order_db"
  }

  kafka_clients = toset(["user-service", "product-service", "order-service"])
  redis_clients = toset(["gateway", "user-service", "product-service", "order-service"])

  azs = slice(data.aws_availability_zones.available.names, 0, 3)

  public_cidrs = {
    (local.azs[0]) = cidrsubnet(var.vpc_cidr, 8, 0)
    (local.azs[1]) = cidrsubnet(var.vpc_cidr, 8, 1)
    (local.azs[2]) = cidrsubnet(var.vpc_cidr, 8, 2)
  }

  app_cidrs = {
    (local.azs[0]) = cidrsubnet(var.vpc_cidr, 8, 10)
    (local.azs[1]) = cidrsubnet(var.vpc_cidr, 8, 11)
  }

  data_cidrs = {
    (local.azs[0]) = cidrsubnet(var.vpc_cidr, 8, 20)
    (local.azs[1]) = cidrsubnet(var.vpc_cidr, 8, 21)
    (local.azs[2]) = cidrsubnet(var.vpc_cidr, 8, 22)
  }

  secret_prefix = "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${var.name_prefix}/"
  ecr_prefix    = "arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${var.name_prefix}/*"
  rds_secret    = "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:rds!db-*"

  management_port = 9090
  zipkin_port     = 9411

  base_image_lines = [
    for line in split("\n", file("${path.module}/../../../../deploy/base-images.lock")) : line
    if can(regex("^[A-Z0-9_]+_IMAGE=", line))
  ]

  observation_images = {
    for line in local.base_image_lines : split("=", line)[0] => split("=", line)[1]
    if contains(["PROMETHEUS_IMAGE", "GRAFANA_IMAGE", "ZIPKIN_IMAGE"], split("=", line)[0])
  }
}
