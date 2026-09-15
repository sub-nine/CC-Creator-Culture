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

variable "release_sha" {
  description = "Git commit that produced this apply. Images are tagged by image_tags, not this SHA."
  type        = string

  validation {
    condition     = can(regex("^[0-9a-f]{40}$", var.release_sha))
    error_message = "release_sha must be a 40 character lowercase hex git commit SHA."
  }
}

variable "image_tags" {
  description = "Content-hash tags for the six services and db-seed. Keys must match the ECR repository names."
  type        = map(string)

  validation {
    condition = toset(keys(var.image_tags)) == toset([
      "config-server",
      "eureka-server",
      "gateway",
      "user-service",
      "product-service",
      "order-service",
      "db-seed",
      ]) && alltrue([
      for tag in values(var.image_tags) : can(regex("^[0-9a-f]{64}$", tag))
    ])
    error_message = "image_tags must have exactly the seven service keys, each a 64-character lowercase hex content hash."
  }
}

variable "config_labels" {
  description = "Per-service git SHA that config-server should serve for that service."
  type        = map(string)

  validation {
    condition = toset(keys(var.config_labels)) == toset([
      "config-server",
      "eureka-server",
      "gateway",
      "user-service",
      "product-service",
      "order-service",
      ]) && alltrue([
      for label in values(var.config_labels) : can(regex("^[0-9a-f]{40}$", label))
    ])
    error_message = "config_labels must have exactly the six service keys, each a 40-character lowercase git SHA."
  }
}

variable "app_running" {
  description = "true starts the app: services desired_count 1, RDS available, observation and kafka EC2 running. false stops all of them."
  type        = bool
  default     = false
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
    kafka_instance_id = string
    kafka_image       = optional(string)
    kafka_bootstrap_document = object({
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
      kafka           = string
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
      jwt        = string
      redis      = string
      grafana    = string
      seed       = string
      r2         = string
    })
    roles = object({
      ecs_execution = string
      ecs_task      = string
      observation   = string
      kafka         = optional(string)
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
    condition     = can(regex("^i-[0-9a-z]+$", var.persistent_config.kafka_instance_id))
    error_message = "persistent_config.kafka_instance_id must be an EC2 instance id."
  }

  validation {
    condition     = contains(keys(var.persistent_config.cloudmap.dns_names), "kafka")
    error_message = "persistent_config.cloudmap.dns_names must include kafka."
  }

  validation {
    condition     = length(var.persistent_config.public_subnet_ids) >= 2 && length(var.persistent_config.app_subnet_ids) >= 2 && length(var.persistent_config.data_subnet_ids) == 3
    error_message = "Need at least 2 public, 2 app, and exactly 3 data subnet ids."
  }
}
