variable "aws_region" {
  description = "AWS region for persistent resources."
  type        = string
  default     = "ap-northeast-2"
}

variable "name_prefix" {
  description = "Name prefix matching bootstrap resources."
  type        = string
  default     = "cc-test"
}

variable "vpc_cidr" {
  description = "VPC CIDR."
  type        = string
  default     = "10.42.0.0/16"
}

variable "bootstrap_state_bucket" {
  description = "S3 bucket holding bootstrap Terraform state."
  type        = string
}

variable "bootstrap_state_key" {
  description = "S3 key of bootstrap Terraform state."
  type        = string
  default     = "cc-service/aws/bootstrap/terraform.tfstate"
}

variable "product_image_bucket" {
  description = "Existing S3 bucket for product images. Not managed by Terraform."
  type        = string
  default     = "cc-creator-culture"
}

variable "product_image_public_url" {
  description = "Existing HTTPS CloudFront URL serving product images."
  type        = string

  validation {
    condition     = startswith(var.product_image_public_url, "https://")
    error_message = "product_image_public_url must be an HTTPS URL."
  }
}
