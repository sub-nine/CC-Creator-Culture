output "state_bucket" {
  value = aws_s3_bucket.state.bucket
}

output "release_bucket" {
  value = aws_s3_bucket.release.bucket
}

output "selected_manifest_key" {
  value = "selected/manifest.json"
}

output "ecr_repository_urls" {
  value = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "ecr_repository_arns" {
  value = { for name, repo in aws_ecr_repository.service : name => repo.arn }
}

output "artifact_repository_urls" {
  value = { for name, repo in aws_ecr_repository.artifact : name => repo.repository_url }
}

output "artifact_repository_arns" {
  value = { for name, repo in aws_ecr_repository.artifact : name => repo.arn }
}

output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github.arn
}

output "plan_read_role_arn" {
  value = aws_iam_role.plan_read.arn
}

output "runtime_deploy_role_arn" {
  value = aws_iam_role.runtime_deploy.arn
}

output "image_publisher_role_arn" {
  value = aws_iam_role.image_publisher.arn
}

output "infrastructure_apply_role_arn" {
  value = aws_iam_role.infrastructure_apply.arn
}

output "persistent_boundary_arn" {
  value = aws_iam_policy.persistent_boundary.arn
}
