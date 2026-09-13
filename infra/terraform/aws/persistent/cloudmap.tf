resource "aws_service_discovery_private_dns_namespace" "this" {
  name        = "${var.name_prefix}.internal"
  description = "Stable private DNS for config and eureka"
  vpc         = aws_vpc.this.id
}

resource "aws_service_discovery_service" "core" {
  for_each = toset(["config-server", "eureka-server"])
  name     = each.key

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.this.id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {}
}
