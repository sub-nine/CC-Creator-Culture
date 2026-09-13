resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "${var.name_prefix}-nat"
  }
}

resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = var.persistent_config.public_subnet_ids[0]

  tags = {
    Name = "${var.name_prefix}-nat"
  }

  depends_on = [aws_eip.nat]
}

resource "aws_route" "private_nat" {
  route_table_id         = var.persistent_config.private_route_table_id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this.id
}

resource "aws_lb" "api" {
  name               = "${var.name_prefix}-api"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [local.sg["alb"]]
  subnets            = var.persistent_config.public_subnet_ids
  ip_address_type    = "ipv4"

  tags = {
    Name = "${var.name_prefix}-api"
  }
}

resource "aws_lb_target_group" "gateway" {
  name        = "${var.name_prefix}-gateway"
  port        = local.services["gateway"].port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.persistent_config.vpc_id

  health_check {
    enabled             = true
    protocol            = "HTTP"
    port                = tostring(local.management_port)
    path                = local.readiness_path
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  deregistration_delay = 30
}

resource "aws_acm_certificate" "api" {
  domain_name       = var.api_hostname
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "cloudflare_dns_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.api.domain_validation_options : dvo.domain_name => dvo
  }

  zone_id = var.cloudflare_zone_id
  name    = each.value.resource_record_name
  type    = each.value.resource_record_type
  content = trimsuffix(each.value.resource_record_value, ".")
  ttl     = 60
  proxied = false
  comment = "ACM DNS validation for ${var.api_hostname}"
}

resource "aws_acm_certificate_validation" "api" {
  certificate_arn = aws_acm_certificate.api.arn
  validation_record_fqdns = [
    for dvo in aws_acm_certificate.api.domain_validation_options : trimsuffix(dvo.resource_record_name, ".")
  ]

  depends_on = [cloudflare_dns_record.acm_validation]
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.api.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.api.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }
}

# Runtime-owned test API DNS. Destroy is allowed with the runtime stack.
# If an explicit api.nodyy.com record already exists, import before apply:
# terraform import cloudflare_dns_record.api ZONE_ID/RECORD_ID
# Wildcard vs explicit record is not confirmed here. Do not manage dev.nodyy.com.
resource "cloudflare_dns_record" "api" {
  zone_id = var.cloudflare_zone_id
  name    = var.api_hostname
  type    = "CNAME"
  content = aws_lb.api.dns_name
  ttl     = 300
  proxied = false
  comment = "cc-test API ALB"
}
