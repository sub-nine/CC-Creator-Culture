resource "aws_security_group" "app" {
  for_each    = local.workload_keys
  name        = "${var.name_prefix}-${each.key}"
  description = "Fargate tasks for ${each.key}"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-${each.key}"
  }
}

resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb"
  description = "Public ALB"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-alb"
  }
}

resource "aws_security_group" "rds" {
  for_each    = local.db_keys
  name        = "${var.name_prefix}-rds-${each.key}"
  description = "RDS for ${each.key} only"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-rds-${each.key}"
  }
}

resource "aws_security_group" "kafka" {
  name        = "${var.name_prefix}-kafka"
  description = "Single Kafka broker EC2"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-kafka"
  }
}

resource "aws_security_group" "redis" {
  name        = "${var.name_prefix}-redis"
  description = "Redis"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-redis"
  }
}

resource "aws_security_group" "observation" {
  name        = "${var.name_prefix}-observation"
  description = "Observation EC2"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-observation"
  }
}

resource "aws_security_group" "migration" {
  name        = "${var.name_prefix}-migration"
  description = "One-off DB migrate/seed tasks"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-migration"
  }
}

resource "aws_vpc_security_group_egress_rule" "app_all" {
  for_each          = aws_security_group.app
  security_group_id = each.value.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "alb_all" {
  security_group_id = aws_security_group.alb.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "observation_all" {
  security_group_id = aws_security_group.observation.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "kafka_all" {
  security_group_id = aws_security_group.kafka.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "migration_all" {
  security_group_id = aws_security_group.migration.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "gateway_from_alb" {
  security_group_id            = aws_security_group.app["gateway"].id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = local.app_ports["gateway"]
  to_port                      = local.app_ports["gateway"]
}

resource "aws_vpc_security_group_ingress_rule" "backend_from_gateway" {
  for_each                     = toset(["user-service", "product-service", "order-service"])
  security_group_id            = aws_security_group.app[each.key].id
  referenced_security_group_id = aws_security_group.app["gateway"].id
  ip_protocol                  = "tcp"
  from_port                    = local.app_ports[each.key]
  to_port                      = local.app_ports[each.key]
}

resource "aws_vpc_security_group_ingress_rule" "config_from_apps" {
  for_each                     = local.app_keys
  security_group_id            = aws_security_group.app["config-server"].id
  referenced_security_group_id = aws_security_group.app[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = local.app_ports["config-server"]
  to_port                      = local.app_ports["config-server"]
}

resource "aws_vpc_security_group_ingress_rule" "eureka_from_apps" {
  for_each                     = local.app_keys
  security_group_id            = aws_security_group.app["eureka-server"].id
  referenced_security_group_id = aws_security_group.app[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = local.app_ports["eureka-server"]
  to_port                      = local.app_ports["eureka-server"]
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_app" {
  for_each                     = local.db_keys
  security_group_id            = aws_security_group.rds[each.key].id
  referenced_security_group_id = aws_security_group.app[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_migration" {
  for_each                     = local.db_keys
  security_group_id            = aws_security_group.rds[each.key].id
  referenced_security_group_id = aws_security_group.migration.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_vpc_security_group_ingress_rule" "kafka_from_clients" {
  for_each                     = local.kafka_clients
  security_group_id            = aws_security_group.kafka.id
  referenced_security_group_id = aws_security_group.app[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = 9092
  to_port                      = 9092
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_clients" {
  for_each                     = local.redis_clients
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.app[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
}

resource "aws_vpc_security_group_ingress_rule" "management_from_observation" {
  for_each                     = local.app_keys
  security_group_id            = aws_security_group.app[each.key].id
  referenced_security_group_id = aws_security_group.observation.id
  ip_protocol                  = "tcp"
  from_port                    = local.management_port
  to_port                      = local.management_port
}

resource "aws_vpc_security_group_ingress_rule" "gateway_management_from_alb" {
  security_group_id            = aws_security_group.app["gateway"].id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = local.management_port
  to_port                      = local.management_port
}

resource "aws_vpc_security_group_ingress_rule" "zipkin_from_apps" {
  for_each                     = local.app_keys
  security_group_id            = aws_security_group.observation.id
  referenced_security_group_id = aws_security_group.app[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = local.zipkin_port
  to_port                      = local.zipkin_port
}
