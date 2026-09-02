variable "tenancy_ocid" {
  description = "OCI tenancy OCID for identity resources."
  type        = string
  sensitive   = true
}

variable "cc_compartment_ocid" {
  description = "cc-service development compartment OCID from bootstrap."
  type        = string
  sensitive   = true
}

variable "network_compartment_ocid" {
  description = "Compartment OCID containing the existing VCN."
  type        = string
  sensitive   = true
}

variable "instance_ocid" {
  description = "Existing development Compute instance OCID. Read-only reference."
  type        = string
  sensitive   = true
}

variable "vcn_ocid" {
  description = "Existing VCN OCID. Read-only reference."
  type        = string
  sensitive   = true
}

variable "release_manifest_bucket" {
  description = "Namespace-unique bucket for immutable release manifests."
  type        = string
}

variable "github_actions_user_email" {
  description = "Primary email for the dedicated GitHub Actions OCI API user."
  type        = string
  sensitive   = true
}
