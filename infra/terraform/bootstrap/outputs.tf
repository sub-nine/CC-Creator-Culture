output "cc_compartment_ocid" {
  value     = oci_identity_compartment.dev.id
  sensitive = true
}

output "object_storage_namespace" {
  value = data.oci_objectstorage_namespace.current.namespace
}

output "terraform_state_bucket" {
  value = oci_objectstorage_bucket.terraform_state.name
}
