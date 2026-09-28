# App DB credentials come from the RDS managed master secret (aws_db_instance.service[*].master_user_secret).
# There is no separate app secret. Kafka is PLAINTEXT on the VPC, so there is no broker secret.

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.name_prefix}/jwt"
  description             = "JWT secret. Populate outside Terraform."
  recovery_window_in_days = 7

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_secretsmanager_secret" "redis" {
  name                    = "${var.name_prefix}/redis"
  description             = "Redis AUTH. Populate outside Terraform."
  recovery_window_in_days = 7

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_secretsmanager_secret" "grafana" {
  name                    = "${var.name_prefix}/grafana"
  description             = "Grafana admin JSON {username,password}. Populate outside Terraform."
  recovery_window_in_days = 7

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_secretsmanager_secret" "seed" {
  name                    = "${var.name_prefix}/seed"
  description             = "Seed JSON {password_hash} BCrypt. Populate outside Terraform."
  recovery_window_in_days = 7

  lifecycle {
    prevent_destroy = true
  }
}

# Product images moved from R2 to S3 with the product-service task role.
# Stop managing the old secret without deleting it; remove it manually once no deployment reads it.
removed {
  from = aws_secretsmanager_secret.product_r2

  lifecycle {
    destroy = false
  }
}
