output "development_domain" {
  value = cloudflare_dns_record.dev.name
}

output "reserved_public_ip" {
  value     = oci_core_public_ip.dev.ip_address
  sensitive = true
}

output "reserved_public_ip_ocid" {
  value     = oci_core_public_ip.dev.id
  sensitive = true
}

output "existing_instance_name" {
  value = data.oci_core_instance.existing.display_name
}

output "existing_vcn_name" {
  value = data.oci_core_vcn.existing.display_name
}
