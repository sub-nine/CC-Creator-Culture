data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:*"]
    }
  }
}

resource "aws_iam_role" "ecs_execution" {
  name               = "${var.name_prefix}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role" "ecs_task" {
  name               = "${var.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "ecs_execution" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [local.ecr_prefix]
  }

  statement {
    sid = "Logs"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = [for name in local.app_keys : "${aws_cloudwatch_log_group.app[name].arn}:*"]
  }

  # RDS managed master secrets (rds!db-...) are the app DB credentials. They use the AWS managed
  # aws/secretsmanager KMS key, whose key policy already allows this account via Secrets Manager,
  # so no extra kms:Decrypt statement is needed for them.
  statement {
    sid = "Secrets"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = concat(
      [
        "${local.secret_prefix}*",
        "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:AmazonMSK_${var.name_prefix}_*",
        aws_secretsmanager_secret.seed.arn,
        aws_secretsmanager_secret.product_r2.arn,
      ],
      [for name, db in aws_db_instance.service : db.master_user_secret[0].secret_arn],
    )
  }

  statement {
    sid       = "MskSecretKms"
    actions   = ["kms:Decrypt", "kms:DescribeKey"]
    resources = [aws_kms_key.msk_secrets.arn]
  }
}

resource "aws_iam_role_policy" "ecs_execution" {
  name   = "execution"
  role   = aws_iam_role.ecs_execution.id
  policy = data.aws_iam_policy_document.ecs_execution.json
}

# Apps receive only task-definition secrets; they do not read Secrets Manager directly.

data "aws_iam_policy_document" "observation_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "observation" {
  name               = "${var.name_prefix}-observation"
  assume_role_policy = data.aws_iam_policy_document.observation_assume.json
}

resource "aws_iam_role_policy_attachment" "observation_ssm" {
  role       = aws_iam_role.observation.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "observation_secrets" {
  statement {
    sid = "GrafanaSecret"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [aws_secretsmanager_secret.grafana.arn]
  }
}

resource "aws_iam_role_policy" "observation_secrets" {
  name   = "grafana-secret"
  role   = aws_iam_role.observation.id
  policy = data.aws_iam_policy_document.observation_secrets.json
}

resource "aws_iam_instance_profile" "observation" {
  name = "${var.name_prefix}-observation"
  role = aws_iam_role.observation.name
}
