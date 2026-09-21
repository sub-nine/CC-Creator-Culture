resource "aws_cloudwatch_log_group" "app" {
  for_each          = local.workload_keys
  name              = "/ecs/${var.name_prefix}/${each.key}"
  retention_in_days = 7

  lifecycle {
    prevent_destroy = true
  }
}
