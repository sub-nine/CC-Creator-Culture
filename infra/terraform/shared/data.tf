data "oci_core_instance" "existing" {
  instance_id = var.instance_ocid
}

data "oci_core_vcn" "existing" {
  vcn_id = var.vcn_ocid
}

data "oci_core_vnic" "existing" {
  vnic_id = var.vnic_ocid
}

data "oci_core_private_ips" "existing" {
  vnic_id = var.vnic_ocid
}

locals {
  primary_private_ip_id = one([
    for private_ip in data.oci_core_private_ips.existing.private_ips : private_ip.id
    if private_ip.is_primary
  ])
}
