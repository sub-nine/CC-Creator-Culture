data "oci_objectstorage_namespace" "current" {
  compartment_id = var.tenancy_ocid
}

resource "oci_identity_compartment" "dev" {
  compartment_id = var.parent_compartment_ocid
  name           = var.compartment_name
  description    = "cc-service development resources managed by Terraform"
  enable_delete  = true

  freeform_tags = {
    Environment = "development"
    ManagedBy   = "terraform"
    Project     = "cc-service"
  }
}

resource "oci_objectstorage_bucket" "terraform_state" {
  compartment_id = oci_identity_compartment.dev.id
  namespace      = data.oci_objectstorage_namespace.current.namespace
  name           = var.terraform_state_bucket
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard"
  versioning     = "Enabled"

  freeform_tags = {
    Environment = "development"
    ManagedBy   = "terraform"
    Project     = "cc-service"
    Purpose     = "terraform-state"
  }

  lifecycle {
    prevent_destroy = true
  }
}
