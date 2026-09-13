resource "aws_service_discovery_private_dns_namespace" "this" {
  name        = "${var.name_prefix}.internal"
  description = "Stable private DNS for config, eureka, and kafka"
  vpc         = aws_vpc.this.id
}

resource "aws_service_discovery_service" "kafka" {
  name = "kafka"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.this.id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = 10
      type = "A"
    }
  }
}

resource "aws_service_discovery_instance" "kafka" {
  instance_id = "kafka"
  service_id  = aws_service_discovery_service.kafka.id

  attributes = {
    AWS_INSTANCE_IPV4 = aws_instance.kafka.private_ip
  }
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
