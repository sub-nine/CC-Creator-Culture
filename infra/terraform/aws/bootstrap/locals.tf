locals {
  common_tags = {
    Project     = "cc-service"
    Environment = "test"
    ManagedBy   = "terraform"
    Stack       = "bootstrap"
  }

  service_names = toset([
    "config-server",
    "eureka-server",
    "gateway",
    "user-service",
    "product-service",
    "order-service",
  ])

  artifact_names = toset([
    "db-migrate",
    "db-seed",
  ])

  github_oidc_url = "https://token.actions.githubusercontent.com"
  github_oidc_aud = "sts.amazonaws.com"

  production_subs = [
    "repo:${var.github_repository}:environment:production",
  ]
  image_publisher_subs = [
    "repo:${var.github_repository}:environment:development",
  ]
}
