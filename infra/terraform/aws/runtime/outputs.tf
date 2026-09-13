output "release_sha" {
  description = "Git commit that produced this apply. Images are tagged by image_tags."
  value       = var.release_sha
}

output "image_tags" {
  description = "Content-hash tags currently applied to the six services and db-seed."
  value       = var.image_tags
}

output "config_labels" {
  description = "Per-service config-repo labels currently applied to the six services."
  value       = var.config_labels
}

output "app_running" {
  description = "Whether services, RDS, and the observation and kafka EC2 instances are running."
  value       = var.app_running
}

output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "seed_task_families" {
  description = "Seed task definition family per service key (user-service, product-service, order-service)."
  value       = { for name, td in aws_ecs_task_definition.db_seed : name => td.family }
}

output "app_subnet_ids" {
  value = var.persistent_config.app_subnet_ids
}

output "migration_security_group_id" {
  value = var.persistent_config.security_groups.migration
}

output "observation_instance_id" {
  value = var.persistent_config.observation_instance_id
}

output "observation_bootstrap_document" {
  value = var.persistent_config.observation_bootstrap_document
}

output "kafka_instance_id" {
  value = var.persistent_config.kafka_instance_id
}

output "kafka_bootstrap_document" {
  value = var.persistent_config.kafka_bootstrap_document
}

output "redis_user_group_id" {
  value = var.redis_user_group_id
}
