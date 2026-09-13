locals {
  account_id          = data.aws_caller_identity.current.account_id
  state_key_prefix    = "cc-service/aws"
  runtime_state_key   = "cc-service/aws/runtime/terraform.tfstate"
  task_role_arn       = "arn:aws:iam::${local.account_id}:role/${var.name_prefix}-*"
  rds_instance_arn    = "arn:aws:rds:${var.aws_region}:${local.account_id}:db:${var.name_prefix}-*"
  ecs_cluster_arn     = "arn:aws:ecs:${var.aws_region}:${local.account_id}:cluster/${var.name_prefix}-*"
  ecs_service_arn     = "arn:aws:ecs:${var.aws_region}:${local.account_id}:service/${var.name_prefix}-*/*"
  ecs_taskdef_arn     = "arn:aws:ecs:${var.aws_region}:${local.account_id}:task-definition/${var.name_prefix}-*:*"
  ecs_task_arn        = "arn:aws:ecs:${var.aws_region}:${local.account_id}:task/${var.name_prefix}-*/*"
  msk_cluster_arn     = "arn:aws:kafka:${var.aws_region}:${local.account_id}:cluster/${var.name_prefix}-*/*"
  redis_arn           = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:replicationgroup:${var.name_prefix}-*"
  cw_alarm_arn        = "arn:aws:cloudwatch:${var.aws_region}:${local.account_id}:alarm:${var.name_prefix}-*"
  redis_subnet_arn    = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:subnetgroup:${var.name_prefix}-*"
  redis_user_arn      = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:user:${var.name_prefix}-*"
  redis_usergroup_arn = "arn:aws:elasticache:${var.aws_region}:${local.account_id}:usergroup:${var.name_prefix}-*"
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

  statement {
    sid = "EcsMutate"
    actions = [
      "ecs:CreateService",
      "ecs:DeleteService",
      "ecs:UpdateService",
      "ecs:RegisterTaskDefinition",
      "ecs:DeregisterTaskDefinition",
      "ecs:TagResource",
      "ecs:UntagResource",
      "ecs:StopTask",
    ]
    resources = [
      local.ecs_cluster_arn,
      local.ecs_service_arn,
      local.ecs_taskdef_arn,
      local.ecs_task_arn,
    ]
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
      "arn:aws:iam::${local.account_id}:role/aws-service-role/elasticloadbalancing.amazonaws.com/*",
      "arn:aws:iam::${local.account_id}:role/aws-service-role/kafka.amazonaws.com/*",
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
    resources = ["arn:aws:ec2:${var.aws_region}:${local.account_id}:instance/*"]

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
    sid = "MskCreate"
    actions = [
      "kafka:CreateCluster",
      "kafka:CreateConfiguration",
    ]
    resources = ["*"]
  }

  statement {
    sid = "MskRead"
    actions = [
      "kafka:ListClusters",
      "kafka:ListClustersV2",
      "kafka:GetCompatibleKafkaVersions",
      "kafka:ListConfigurations",
    ]
    resources = ["*"]
  }

  statement {
    sid = "MskMutate"
    actions = [
      "kafka:DeleteCluster",
      "kafka:DescribeCluster",
      "kafka:DescribeClusterV2",
      "kafka:GetBootstrapBrokers",
      "kafka:UpdateClusterConfiguration",
      "kafka:UpdateBrokerStorage",
      "kafka:TagResource",
      "kafka:UntagResource",
      "kafka:DescribeConfiguration",
      "kafka:UpdateConfiguration",
      "kafka:BatchAssociateScramSecret",
      "kafka:BatchDisassociateScramSecret",
    ]
    resources = [local.msk_cluster_arn]
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
      "elasticache:AddTagsToResource",
      "elasticache:CreateUser",
      "elasticache:DeleteUser",
      "elasticache:ModifyUser",
      "elasticache:CreateUserGroup",
      "elasticache:DeleteUserGroup",
      "elasticache:ModifyUserGroup",
    ]
    resources = [
      local.redis_arn,
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
      "acm:AddTagsToCertificate",
      "acm:DeleteCertificate",
    ]
    resources = ["*"]
  }

  statement {
    sid = "RuntimeNetwork"
    actions = [
      "ec2:Describe*",
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
