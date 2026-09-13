data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ssm_parameter" "al2023_arm" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

data "terraform_remote_state" "bootstrap" {
  backend = "s3"

  config = {
    bucket       = var.bootstrap_state_bucket
    key          = var.bootstrap_state_key
    region       = var.aws_region
    encrypt      = true
    use_lockfile = true
  }
}
