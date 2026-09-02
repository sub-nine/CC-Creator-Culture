variable "tenancy_ocid" {
  description = "OCI tenancy OCID."
  type        = string
  sensitive   = true
}

variable "parent_compartment_ocid" {
  description = "Parent compartment OCID for the cc-service development compartment."
  type        = string
  sensitive   = true
}

variable "compartment_name" {
  description = "Name of the cc-service development compartment."
  type        = string
  default     = "cc-dev"
}

variable "terraform_state_bucket" {
  description = "Namespace-unique Object Storage bucket for Terraform state."
  type        = string
}
