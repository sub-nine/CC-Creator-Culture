resource "aws_db_subnet_group" "data" {
  name       = "${var.name_prefix}-data"
  subnet_ids = [for az in local.azs : aws_subnet.data[az].id]

  tags = {
    Name = "${var.name_prefix}-data"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_db_parameter_group" "postgres17" {
  name   = "${var.name_prefix}-postgres17"
  family = "postgres17"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

resource "aws_db_instance" "service" {
  for_each = local.db_keys

  identifier     = "${var.name_prefix}-${each.key}"
  engine         = "postgres"
  engine_version = "17"
  instance_class = "db.t4g.small"

  allocated_storage     = 20
  max_allocated_storage = 0
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = local.db_names[each.key]
  username = "cc_master"

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.data.name
  vpc_security_group_ids = [aws_security_group.rds[each.key].id]
  parameter_group_name   = aws_db_parameter_group.postgres17.name
  publicly_accessible    = false
  multi_az               = false
  port                   = 5432

  backup_retention_period   = 7
  copy_tags_to_snapshot     = true
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name_prefix}-${each.key}-final"

  auto_minor_version_upgrade   = true
  apply_immediately            = false
  performance_insights_enabled = false
  monitoring_interval          = 0

  tags = {
    Name = "${var.name_prefix}-${each.key}"
  }

  lifecycle {
    prevent_destroy = true
  }
}
