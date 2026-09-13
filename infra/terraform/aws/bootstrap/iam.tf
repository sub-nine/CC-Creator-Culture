data "aws_caller_identity" "current" {}

data "tls_certificate" "github" {
  url = local.github_oidc_url
}

resource "aws_iam_openid_connect_provider" "github" {
  url             = local.github_oidc_url
  client_id_list  = [local.github_oidc_aud]
  thumbprint_list = [data.tls_certificate.github.certificates[length(data.tls_certificate.github.certificates) - 1].sha1_fingerprint]
}

data "aws_iam_policy_document" "production_assume" {
  statement {
    sid     = "GitHubActionsProduction"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = [local.github_oidc_aud]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = local.production_subs
    }
  }
}

data "aws_iam_policy_document" "image_publisher_assume" {
  statement {
    sid     = "GitHubActionsDevelopment"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = [local.github_oidc_aud]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = local.image_publisher_subs
    }
  }
}

resource "aws_iam_role" "plan_read" {
  name               = "${var.name_prefix}-gha-plan-read"
  assume_role_policy = data.aws_iam_policy_document.production_assume.json
}

resource "aws_iam_role_policy_attachment" "plan_read" {
  role       = aws_iam_role.plan_read.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

resource "aws_iam_role_policy" "plan_read_lock" {
  name   = "state-lockfile"
  role   = aws_iam_role.plan_read.id
  policy = data.aws_iam_policy_document.plan_read_lock.json
}

resource "aws_iam_role" "runtime_deploy" {
  name               = "${var.name_prefix}-gha-runtime-deploy"
  assume_role_policy = data.aws_iam_policy_document.production_assume.json
}

resource "aws_iam_role_policy" "runtime_deploy" {
  name   = "runtime-deploy"
  role   = aws_iam_role.runtime_deploy.id
  policy = data.aws_iam_policy_document.runtime_deploy.json
}

resource "aws_iam_role" "image_publisher" {
  name               = "${var.name_prefix}-gha-dev-image-publisher"
  assume_role_policy = data.aws_iam_policy_document.image_publisher_assume.json
}

data "aws_iam_policy_document" "image_publisher" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = concat(
      [for repo in aws_ecr_repository.service : repo.arn],
      [for repo in aws_ecr_repository.artifact : repo.arn],
    )
  }
}

resource "aws_iam_role_policy" "image_publisher" {
  name   = "ecr-push"
  role   = aws_iam_role.image_publisher.id
  policy = data.aws_iam_policy_document.image_publisher.json
}
