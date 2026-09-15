variable "aws_region" {
  description = "AWS region for bootstrap resources."
  type        = string
  default     = "ap-northeast-2"
}

variable "name_prefix" {
  description = "Name prefix for bootstrap resources."
  type        = string
  default     = "cc-test"
}

variable "state_bucket" {
  description = "Globally unique S3 bucket for Terraform state."
  type        = string
}

variable "release_bucket" {
  description = "Globally unique S3 bucket for release manifests."
  type        = string
}

variable "github_repository" {
  description = "GitHub org/repo used in OIDC subject claims."
  type        = string
  default     = "sub-nine/CC-Creator-Culture"
}
