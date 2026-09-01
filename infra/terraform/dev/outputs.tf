output "cc_nsg_ocid" {
  value     = oci_core_network_security_group.dev.id
  sensitive = true
}

output "ocir_namespace" {
  value = data.oci_objectstorage_namespace.current.namespace
}

output "release_manifest_bucket" {
  value = oci_objectstorage_bucket.release_manifest.name
}

output "vault_ocid" {
  value     = oci_kms_vault.dev.id
  sensitive = true
}

output "vault_key_ocid" {
  value     = oci_kms_key.dev_secrets.id
  sensitive = true
}

output "existing_instance_name" {
  value = data.oci_core_instance.existing.display_name
}
