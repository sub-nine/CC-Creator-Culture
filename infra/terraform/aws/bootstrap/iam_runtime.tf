locals {
  account_id        = data.aws_caller_identity.current.account_id
  state_key_prefix  = "cc-service/aws"
  runtime_state_key = "cc-service/aws/runtime/terraform.tfstate"
  task_role_arn     = "arn:aws:iam::${local.account_id}:role/${var.name_prefix}-*"
  rds_instance_arn  = "arn:aws:rds:${var.aws_region}:${local.account_id}:db:${var.name_prefix}-*"
  # The runtime ECS cluster is named exactly name_prefix, so cluster paths use "${name_prefix}*" not "${name_prefix}-*".
  ecs_cluster_arn     = "arn:aws:ecs:${var.aws_region}:${local.account_id}:cluster/${var.name_prefix}*"
  ecs_service_arn     = "arn:aws:ecs:${var.aws_region}:${local.account_id}:service/${var.name_prefix}*/*"
  ecs_taskdef_arn     = "arn:aws:ecs:${var.aws_region}:${local.account_id}:task-definition/${var.name_prefix}-*:*"
  ecs_task_arn        = "arn:aws:ecs:${var.aws_region}:${local.account_id}:task/${var.name_prefix}*/*"
  redis_arn           = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:replicationgroup:${var.name_prefix}-*"
  redis_cluster_arn   = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:cluster:${var.name_prefix}-*"
  redis_paramgroup    = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:parametergroup:default*"
  cw_alarm_arn        = "arn:aws:cloudwatch:${var.aws_region}:${local.account_id}:alarm:${var.name_prefix}-*"
  redis_subnet_arn    = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:subnetgroup:${var.name_prefix}-*"
  redis_user_arn      = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:user:${var.name_prefix}-*"
  redis_usergroup_arn = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:usergroup:${var.name_prefix}-*"
  ecr_repository_arn  = "arn:aws:ecr:${var.aws_region}:${local.account_id}:repository/${var.name_prefix}/*"
  ssm_document_arn    = "arn:aws:ssm:${var.aws_region}:${local.account_id}:document/${var.name_prefix}-*"
  ec2_instance_arn    = "arn:aws:ec2:${var.aws_region}:${local.account_id}:instance/*"
}

data "aws_iam_policy_document" "plan_read_lock" {
  statement {
    sid     = "StateLockfiles"
    actions = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = [
      "${aws_s3_bucket.state.arn}/${local.state_key_prefix}/bootstrap/terraform.tfstate.tflock",
      "${aws_s3_bucket.state.arn}/${local.state_key_prefix}/persistent/terraform.tfstate.tflock",
      "${aws_s3_bucket.state.arn}/${local.state_key_prefix}/runtime/terraform.tfstate.tflock",
    ]
  }
}

data "aws_iam_policy_document" "runtime_deploy" {
  statement {
    sid = "EcsRead"
    actions = [
      "ecs:DescribeClusters",
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:DescribeTasks",
      "ecs:ListClusters",
      "ecs:ListServices",
      "ecs:ListTaskDefinitions",
      "ecs:ListTasks",
    ]
    resources = ["*"]
  }

  # AWS provider steady-state waits use the deployment APIs in addition to DescribeServices.
  statement {
    sid       = "EcsDeploymentList"
    actions   = ["ecs:ListServiceDeployments"]
    resources = [local.ecs_service_arn]
  }

  statement {
    sid     = "EcsDeploymentRead"
    actions = ["ecs:DescribeServiceDeployments"]
    resources = [
      local.ecs_service_arn,
      "arn:aws:ecs:${var.aws_region}:${local.account_id}:service-deployment/${var.name_prefix}/${var.name_prefix}-*/*",
    ]
  }

  # These ECS actions have no resource-level scoping in IAM, so they must be "*".
  statement {
    sid = "EcsUnscoped"
    actions = [
      "ecs:CreateCluster",
      "ecs:RegisterTaskDefinition",
      "ecs:DeregisterTaskDefinition",
    ]
    resources = ["*"]
  }

  statement {
    sid = "EcsMutate"
    actions = [
      "ecs:DeleteCluster",
      "ecs:UpdateClusterSettings",
      "ecs:PutClusterCapacityProviders",
      "ecs:CreateService",
      "ecs:DeleteService",
      "ecs:UpdateService",
      "ecs:TagResource",
      "ecs:UntagResource",
      "ecs:RunTask",
      "ecs:StopTask",
    ]
    resources = [
      local.ecs_cluster_arn,
      local.ecs_service_arn,
      local.ecs_taskdef_arn,
      local.ecs_task_arn,
    ]
  }

  # The deploy workflow checks that per-service image_tags exist before terraform apply.
  statement {
    sid = "EcrDescribeImages"
    actions = [
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
    ]
    resources = [local.ecr_repository_arn]
  }

  # The deploy workflow bootstraps the observation EC2 with the persistent SSM document after NAT exists.
  statement {
    sid       = "SsmSendCommandDocument"
    actions   = ["ssm:SendCommand"]
    resources = [local.ssm_document_arn]
  }

  statement {
    sid       = "SsmSendCommandInstance"
    actions   = ["ssm:SendCommand"]
    resources = [local.ec2_instance_arn]

    condition {
      test     = "StringLike"
      variable = "ssm:resourceTag/Name"
      values   = ["${var.name_prefix}-*"]
    }
  }

  statement {
    sid = "SsmCommandRead"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
      "ssm:ListCommands",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "PassEcsTaskRoles"
    actions   = ["iam:PassRole"]
    resources = [local.task_role_arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  statement {
    sid       = "GetEcsTaskRoles"
    actions   = ["iam:GetRole"]
    resources = [local.task_role_arn]
  }

  statement {
    sid     = "ServiceLinkedRoles"
    actions = ["iam:CreateServiceLinkedRole"]
    resources = [
      "arn:aws:iam::${local.account_id}:role/aws-service-role/ecs.amazonaws.com/*",
      "arn:aws:iam::${local.account_id}:role/aws-service-role/ecs.application-autoscaling.amazonaws.com/*",
      "arn:aws:iam::${local.account_id}:role/aws-service-role/elasticloadbalancing.amazonaws.com/*",
      "arn:aws:iam::${local.account_id}:role/aws-service-role/elasticache.amazonaws.com/*",
    ]
  }

  statement {
    sid = "RdsRead"
    actions = [
      "rds:DescribeDBInstances",
      "rds:DescribeDBInstanceAutomatedBackups",
    ]
    resources = ["*"]
  }

  statement {
    sid = "RdsStartStop"
    actions = [
      "rds:StartDBInstance",
      "rds:StopDBInstance",
    ]
    resources = [local.rds_instance_arn]
  }

  statement {
    sid = "ObservationStartStop"
    actions = [
      "ec2:StartInstances",
      "ec2:StopInstances",
    ]
    resources = [local.ec2_instance_arn]

    condition {
      test     = "StringLike"
      variable = "ec2:ResourceTag/Name"
      values   = ["${var.name_prefix}-*"]
    }
  }

  statement {
    sid = "AppAutoscaling"
    actions = [
      "application-autoscaling:RegisterScalableTarget",
      "application-autoscaling:DeregisterScalableTarget",
      "application-autoscaling:PutScalingPolicy",
      "application-autoscaling:DeleteScalingPolicy",
      "application-autoscaling:DescribeScalableTargets",
      "application-autoscaling:DescribeScalingPolicies",
      "application-autoscaling:ListTagsForResource",
      "application-autoscaling:TagResource",
    ]
    resources = ["*"]
  }

  statement {
    sid = "ScalingAlarms"
    actions = [
      "cloudwatch:PutMetricAlarm",
      "cloudwatch:DeleteAlarms",
      "cloudwatch:DescribeAlarms",
    ]
    resources = [local.cw_alarm_arn]
  }

  statement {
    sid = "RedisRead"
    actions = [
      "elasticache:DescribeReplicationGroups",
      "elasticache:DescribeCacheClusters",
      "elasticache:DescribeCacheSubnetGroups",
      "elasticache:DescribeUsers",
      "elasticache:DescribeUserGroups",
      "elasticache:ListTagsForResource",
    ]
    resources = ["*"]
  }

  statement {
    sid = "RedisMutate"
    actions = [
      "elasticache:CreateReplicationGroup",
      "elasticache:DeleteReplicationGroup",
      "elasticache:ModifyReplicationGroup",
      "elasticache:CreateCacheSubnetGroup",
      "elasticache:DeleteCacheSubnetGroup",
      "elasticache:ModifyCacheSubnetGroup",
      "elasticache:AddTagsToResource",
      "elasticache:RemoveTagsFromResource",
      "elasticache:CreateUser",
      "elasticache:DeleteUser",
      "elasticache:ModifyUser",
      "elasticache:CreateUserGroup",
      "elasticache:DeleteUserGroup",
      "elasticache:ModifyUserGroup",
    ]
    # CreateReplicationGroup is authorized against the member cache clusters, the parameter group,
    # the subnet group, and the user group too. redis_user_group_id must therefore start with name_prefix.
    resources = [
      local.redis_arn,
      local.redis_cluster_arn,
      local.redis_paramgroup,
      local.redis_subnet_arn,
      local.redis_user_arn,
      local.redis_usergroup_arn,
    ]
  }

  statement {
    sid = "LoadBalancing"
    actions = [
      "elasticloadbalancing:*",
      "acm:RequestCertificate",
      "acm:DescribeCertificate",
      "acm:ListCertificates",
      "acm:ListTagsForCertificate",
      "acm:AddTagsToCertificate",
      "acm:RemoveTagsFromCertificate",
      "acm:DeleteCertificate",
    ]
    resources = ["*"]
  }

  statement {
    sid = "RuntimeNetwork"
    actions = [
      "ec2:Describe*",
      "ec2:GetSecurityGroupsForVpc",
      "ec2:AllocateAddress",
      "ec2:ReleaseAddress",
      "ec2:AssociateAddress",
      "ec2:DisassociateAddress",
      "ec2:CreateNatGateway",
      "ec2:DeleteNatGateway",
      "ec2:CreateRoute",
      "ec2:DeleteRoute",
      "ec2:ReplaceRoute",
      "ec2:CreateTags",
      "ec2:DeleteTags",
      "ec2:CreateSecurityGroup",
      "ec2:DeleteSecurityGroup",
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:AuthorizeSecurityGroupEgress",
      "ec2:RevokeSecurityGroupIngress",
      "ec2:RevokeSecurityGroupEgress",
    ]
    resources = ["*"]
  }

  statement {
    sid = "LogsAndDiscovery"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams",
      "servicediscovery:RegisterInstance",
      "servicediscovery:DeregisterInstance",
      "servicediscovery:Get*",
      "servicediscovery:List*",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "StateList"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.state.arn, aws_s3_bucket.release.arn]
  }

  statement {
    sid     = "StateRead"
    actions = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = [
      "${aws_s3_bucket.state.arn}/${local.state_key_prefix}/*",
    ]
  }

  statement {
    sid     = "RuntimeStateWrite"
    actions = ["s3:PutObject"]
    resources = [
      "${aws_s3_bucket.state.arn}/${local.runtime_state_key}",
    ]
  }

  statement {
    sid     = "RuntimeStateLock"
    actions = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = [
      "${aws_s3_bucket.state.arn}/${local.runtime_state_key}.tflock",
    ]
  }

  statement {
    sid     = "ReleaseObjects"
    actions = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject"]
    resources = [
      "${aws_s3_bucket.release.arn}/*",
    ]
  }

  statement {
    sid    = "DenyPersistentDeletes"
    effect = "Deny"
    actions = [
      "rds:DeleteDBInstance",
      "rds:DeleteDBSubnetGroup",
      "ec2:DeleteVpc",
      "ec2:DeleteSubnet",
      "ec2:DeleteVolume",
      "ec2:TerminateInstances",
      "s3:DeleteBucket",
      "ecr:DeleteRepository",
      "kms:ScheduleKeyDeletion",
      "kms:DisableKey",
    ]
    resources = ["*"]
  }
}
