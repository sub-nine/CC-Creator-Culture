variable "network_compartment_ocid" {
  description = "Compartment OCID containing the existing network and reserved public IP."
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

variable "vnic_ocid" {
  description = "Existing primary VNIC OCID. Read-only reference."
  type        = string
  sensitive   = true
}

variable "reserved_public_ip_name" {
  description = "Display name retained for the existing reserved public IP."
  type        = string
  default     = "cc-dev-reserved-ip"
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone ID containing the development DNS record."
  type        = string
  sensitive   = true
}

variable "cloudflare_record_name" {
  description = "Fully qualified development hostname."
  type        = string
  default     = "dev.nodyy.com"
}
