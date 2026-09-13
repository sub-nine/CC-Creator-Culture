resource "aws_kms_key" "msk_secrets" {
  description             = "${var.name_prefix} MSK SCRAM secrets"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AccountRoot"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      }
    ]
  })

  tags = {
    Name = "${var.name_prefix}-msk-secrets"
  }
}

resource "aws_kms_alias" "msk_secrets" {
  name          = "alias/${var.name_prefix}/msk-secrets"
  target_key_id = aws_kms_key.msk_secrets.id
}

resource "aws_secretsmanager_secret" "msk" {
  for_each = local.kafka_clients

  name                    = "AmazonMSK_${var.name_prefix}_${each.key}"
  description             = "MSK SCRAM container for ${each.key}. Populate username/password outside Terraform."
  kms_key_id              = aws_kms_key.msk_secrets.arn
  recovery_window_in_days = 7

  tags = {
    Name = "AmazonMSK_${var.name_prefix}_${each.key}"
  }
}

resource "aws_secretsmanager_secret" "app" {
  for_each = local.db_keys

  name                    = "${var.name_prefix}/app/${each.key}"
  description             = "App DB credentials for ${each.key}. Populate outside Terraform."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.name_prefix}/jwt"
  description             = "JWT secret. Populate outside Terraform."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret" "redis" {
  name                    = "${var.name_prefix}/redis"
  description             = "Redis AUTH. Populate outside Terraform."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret" "grafana" {
  name                    = "${var.name_prefix}/grafana"
  description             = "Grafana admin JSON {username,password}. Populate outside Terraform."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret" "seed" {
  name                    = "${var.name_prefix}/seed"
  description             = "Seed JSON {password_hash} BCrypt. Populate outside Terraform."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret" "product_r2" {
  name                    = "${var.name_prefix}/product-r2"
  description             = "Product R2 JSON {access_key,secret_key,endpoint,bucket,public_url}. Populate outside Terraform."
  recovery_window_in_days = 7
}
