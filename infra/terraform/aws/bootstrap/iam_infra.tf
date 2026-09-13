locals {
  persistent_role_names = [
    "${var.name_prefix}-ecs-execution",
    "${var.name_prefix}-ecs-task",
    "${var.name_prefix}-observation",
  ]
  persistent_role_arns = [
    for name in local.persistent_role_names :
    "arn:aws:iam::${local.account_id}:role/${name}"
  ]
  gha_role_arns = [
    aws_iam_role.plan_read.arn,
    aws_iam_role.runtime_deploy.arn,
    aws_iam_role.image_publisher.arn,
    "arn:aws:iam::${local.account_id}:role/${var.name_prefix}-gha-infrastructure-apply",
  ]
  persistent_state_key = "${local.state_key_prefix}/persistent/terraform.tfstate"
  project_tag          = "cc-service"
}

data "aws_iam_policy_document" "persistent_boundary" {
  statement {
    sid = "AllowTaggedPersistentServices"
    actions = [
      "ec2:*",
      "rds:*",
      "logs:*",
      "secretsmanager:*",
      "kms:*",
      "servicediscovery:*",
      "ssm:GetParameter",
      "ssm:GetParameters",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  statement {
    sid = "AllowProjectIamRoles"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:GetRolePolicy",
      "iam:TagRole",
      "iam:PassRole",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:GetInstanceProfile",
      "iam:TagInstanceProfile",
    ]
    resources = concat(local.persistent_role_arns, [
      "arn:aws:iam::${local.account_id}:instance-profile/${var.name_prefix}-observation",
    ])
  }

  statement {
    sid       = "DenyBootstrapRoleMutation"
    effect    = "Deny"
    actions   = ["iam:*"]
    resources = concat(local.gha_role_arns, [aws_iam_openid_connect_provider.github.arn])
  }

  statement {
    sid       = "DenyAdminManagedPolicy"
    effect    = "Deny"
    actions   = ["iam:AttachRolePolicy", "iam:AttachUserPolicy", "iam:AttachGroupPolicy"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "iam:PolicyARN"
      values   = ["arn:aws:iam::aws:policy/AdministratorAccess"]
    }
  }

  statement {
    sid       = "DenyRemoveBoundary"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary", "iam:PutRolePermissionsBoundary"]
    resources = ["*"]
  }

  statement {
    sid       = "DenyIdentityCreate"
    effect    = "Deny"
    actions   = ["iam:CreateUser", "iam:CreateAccessKey", "iam:CreateLoginProfile"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "persistent_boundary" {
  name   = "${var.name_prefix}-persistent-boundary"
  policy = data.aws_iam_policy_document.persistent_boundary.json
}

data "aws_iam_policy_document" "infrastructure_apply" {
  statement {
    sid       = "CreateTaggedKms"
    actions   = ["kms:CreateKey"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Project"
      values   = [local.project_tag]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/ManagedBy"
      values   = ["terraform"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  statement {
    sid = "CreateTaggedNetworkAndCompute"
    actions = [
      "ec2:CreateVpc",
      "ec2:CreateSubnet",
      "ec2:CreateInternetGateway",
      "ec2:CreateRouteTable",
      "ec2:CreateSecurityGroup",
      "ec2:RunInstances",
      "ec2:CreateTags",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Project"
      values   = [local.project_tag]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  statement {
    sid = "CreateTaggedRdsLogsSecrets"
    actions = [
      "rds:CreateDBInstance",
      "rds:CreateDBSubnetGroup",
      "rds:CreateDBParameterGroup",
      "logs:CreateLogGroup",
      "secretsmanager:CreateSecret",
      "servicediscovery:CreatePrivateDnsNamespace",
      "servicediscovery:CreateService",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestTag/Project"
      values   = [local.project_tag]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  statement {
    sid = "MutateTaggedPersistent"
    actions = [
      "ec2:*",
      "rds:*",
      "logs:*",
      "secretsmanager:*",
      "kms:*",
      "servicediscovery:*",
      "ssm:GetParameter",
      "ssm:GetParameters",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/Project"
      values   = [local.project_tag]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  statement {
    sid = "DescribeInRegion"
    actions = [
      "ec2:Describe*",
      "rds:Describe*",
      "logs:Describe*",
      "logs:ListTagsLogGroup",
      "secretsmanager:DescribeSecret",
      "secretsmanager:ListSecrets",
      "kms:List*",
      "kms:Describe*",
      "servicediscovery:List*",
      "servicediscovery:Get*",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:GetInstanceProfile",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "ssm:GetParameter",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  statement {
    sid       = "CreateProjectTaskRoles"
    actions   = ["iam:CreateRole"]
    resources = local.persistent_role_arns
    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.persistent_boundary.arn]
    }
  }

  statement {
    sid = "ManageProjectTaskRoles"
    actions = [
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:GetRolePolicy",
      "iam:TagRole",
      "iam:PassRole",
    ]
    resources = local.persistent_role_arns
  }

  statement {
    sid = "ObservationInstanceProfile"
    actions = [
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:GetInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:PassRole",
    ]
    resources = concat(local.persistent_role_arns, [
      "arn:aws:iam::${local.account_id}:instance-profile/${var.name_prefix}-observation",
    ])
  }

  statement {
    sid     = "PersistentStateRead"
    actions = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = [
      "${aws_s3_bucket.state.arn}/${local.state_key_prefix}/bootstrap/terraform.tfstate",
      "${aws_s3_bucket.state.arn}/${local.persistent_state_key}",
    ]
  }

  statement {
    sid       = "PersistentStateWrite"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.state.arn}/${local.persistent_state_key}"]
  }

  statement {
    sid       = "PersistentStateLock"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.state.arn}/${local.persistent_state_key}.tflock"]
  }

  statement {
    sid       = "StateList"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.state.arn]
  }

  statement {
    sid       = "DenyBootstrapRoleMutation"
    effect    = "Deny"
    actions   = ["iam:*"]
    resources = concat(local.gha_role_arns, [aws_iam_openid_connect_provider.github.arn])
  }

  statement {
    sid       = "DenyAdminManagedPolicy"
    effect    = "Deny"
    actions   = ["iam:AttachRolePolicy", "iam:AttachUserPolicy"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "iam:PolicyARN"
      values   = ["arn:aws:iam::aws:policy/AdministratorAccess"]
    }
  }

  statement {
    sid    = "DenyPersistentDestroys"
    effect = "Deny"
    actions = [
      "rds:DeleteDBInstance",
      "ec2:DeleteVpc",
      "ec2:TerminateInstances",
      "ec2:DeleteVolume",
      "secretsmanager:DeleteSecret",
      "kms:ScheduleKeyDeletion",
      "s3:DeleteBucket",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role" "infrastructure_apply" {
  name                 = "${var.name_prefix}-gha-infrastructure-apply"
  assume_role_policy   = data.aws_iam_policy_document.production_assume.json
  permissions_boundary = aws_iam_policy.persistent_boundary.arn
}

resource "aws_iam_role_policy" "infrastructure_apply" {
  name   = "infrastructure-apply"
  role   = aws_iam_role.infrastructure_apply.id
  policy = data.aws_iam_policy_document.infrastructure_apply.json
}
