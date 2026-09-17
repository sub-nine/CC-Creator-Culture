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
    "embedding-service",
  ])

  artifact_names = toset([
    "db-seed",
    "k6",
  ])

  github_oidc_url = "https://token.actions.githubusercontent.com"
  github_oidc_aud = "sts.amazonaws.com"

  github_oidc_repos = distinct(compact([
    var.github_repository,
    var.github_oidc_repository,
  ]))
  production_subs = [
    for repo in local.github_oidc_repos : "repo:${repo}:environment:production"
  ]
  # deploy-dev image job runs without an environment, so trust the dev and release/* branch refs too.
  image_publisher_subs = flatten([
    for repo in local.github_oidc_repos : [
      "repo:${repo}:environment:development",
      "repo:${repo}:ref:refs/heads/dev",
      "repo:${repo}:ref:refs/heads/release/*",
    ]
  ])
}
