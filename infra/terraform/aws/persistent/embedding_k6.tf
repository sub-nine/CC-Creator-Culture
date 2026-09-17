resource "aws_vpc_security_group_ingress_rule" "embedding_from_clients" {
  for_each                     = toset(["product-service", "k6"])
  security_group_id            = aws_security_group.app["embedding-service"].id
  referenced_security_group_id = aws_security_group.app[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = 8000
  to_port                      = 8000
}

resource "aws_vpc_security_group_ingress_rule" "prometheus_from_k6" {
  security_group_id            = aws_security_group.observation.id
  referenced_security_group_id = aws_security_group.app["k6"].id
  ip_protocol                  = "tcp"
  from_port                    = 9090
  to_port                      = 9090
}
