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

resource "aws_secretsmanager_secret" "product_r2" {
  name                    = "${var.name_prefix}/product-r2"
  description             = "Product R2 JSON {access_key,secret_key,endpoint,bucket,public_url}. Populate outside Terraform."
  recovery_window_in_days = 7

  lifecycle {
    prevent_destroy = true
  }
}
