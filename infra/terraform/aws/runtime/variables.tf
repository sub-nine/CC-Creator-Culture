variable "aws_region" {
  description = "AWS region for runtime resources."
  type        = string
  default     = "ap-northeast-2"
}

variable "name_prefix" {
  description = "Name prefix matching bootstrap and persistent stacks."
  type        = string
  default     = "cc-test"
}

variable "release_bucket" {
  description = "Optional override. Defaults to persistent_config.release_bucket."
  type        = string
  default     = null
}

variable "api_hostname" {
  description = "Public API hostname."
  type        = string
  default     = "api.nodyy.com"
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone id for api hostname."
  type        = string
  sensitive   = true
}

variable "msk_kafka_version" {
  description = "MSK Kafka version. Live version list was not available on the free-plan account."
  type        = string
  default     = "3.9.x"
}

variable "redis_username" {
  description = "Redis ACL user name created outside Terraform."
  type        = string
  default     = "app"
}

variable "redis_user_group_id" {
  description = "Existing ElastiCache user group id created by CLI. Required so Redis is not left without ACL."
  type        = string

  validation {
    condition     = length(trimspace(var.redis_user_group_id)) > 0
    error_message = "redis_user_group_id is required. Create the ACL user group outside Terraform first."
  }
}

variable "artifact_images" {
  description = "Digest-pinned ECR image URIs for db-migrate and db-seed one-off tasks."
  type        = map(string)

  validation {
    condition = alltrue([
      for name in ["db-migrate", "db-seed"] :
      contains(keys(var.artifact_images), name) && can(regex("@sha256:[0-9a-f]{64}$", var.artifact_images[name]))
    ])
    error_message = "artifact_images must map db-migrate and db-seed to repository@sha256:<64 lowercase hex>."
  }
}

variable "service_images" {
  description = "Digest-pinned ECR image URIs for the six app services."
  type        = map(string)

  validation {
    condition = length(var.service_images) == 6 && alltrue([
      for svc in [
        "config-server",
        "eureka-server",
        "gateway",
        "user-service",
        "product-service",
        "order-service",
      ] : contains(keys(var.service_images), svc) && can(regex("@sha256:[0-9a-f]{64}$", var.service_images[svc]))
    ])
    error_message = "service_images must map all 6 services to repository@sha256:<64 lowercase hex>."
  }
}

variable "persistent_config" {
  description = "Exact persistent_config object from the persistent stack output. Extra fields must be declared here so they are not dropped."
  type = object({
    name_prefix             = string
    region                  = string
    vpc_id                  = string
    public_subnet_ids       = list(string)
    app_subnet_ids          = list(string)
    data_subnet_ids         = list(string)
    public_route_table_id   = string
    private_route_table_id  = string
    internet_gateway_id     = string
    app_ports               = map(number)
    management_port         = number
    zipkin_port             = number
    db_port                 = number
    db_names                = map(string)
    observation_instance_id = string
    observation_private_ip  = optional(string)
    observation_images      = any
    observation_bootstrap_document = object({
      name    = string
      version = string
    })
    rds_instances         = map(string)
    db_endpoints          = map(string)
    release_bucket        = string
    selected_manifest_key = string
    log_groups            = map(string)
    security_groups = object({
      config-server   = string
      eureka-server   = string
      gateway         = string
      user-service    = string
      product-service = string
      order-service   = string
      alb             = string
      observation     = string
      msk             = string
      redis           = string
      migration       = string
      rds = object({
        user-service    = string
        product-service = string
        order-service   = string
      })
    })
    secret_arns = object({
      rds_master = map(string)
      app        = map(string)
      jwt        = string
      redis      = string
      kafka      = map(string)
      grafana    = string
      seed       = string
      r2         = string
    })
    msk_secrets_kms_key_arn = string
    roles = object({
      ecs_execution = string
      ecs_task      = string
      observation   = string
    })
    cloudmap = object({
      namespace_id   = string
      namespace_name = string
      hosted_zone_id = string
      service_ids    = map(string)
      dns_names      = map(string)
    })
    ecr_repository_urls      = map(string)
    ecr_repository_arns      = map(string)
    artifact_repository_urls = map(string)
    artifact_repository_arns = map(string)
  })

  validation {
    condition     = can(regex("^i-[0-9a-z]+$", var.persistent_config.observation_instance_id))
    error_message = "persistent_config.observation_instance_id must be an EC2 instance id."
  }

  validation {
    condition     = length(var.persistent_config.public_subnet_ids) >= 2 && length(var.persistent_config.app_subnet_ids) >= 2 && length(var.persistent_config.data_subnet_ids) == 3
    error_message = "Need at least 2 public, 2 app, and exactly 3 data subnet ids."
  }
}
