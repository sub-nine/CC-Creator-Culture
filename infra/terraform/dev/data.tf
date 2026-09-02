data "oci_core_instance" "existing" {
  instance_id = var.instance_ocid
}

data "oci_core_vcn" "existing" {
  vcn_id = var.vcn_ocid
}

data "oci_objectstorage_namespace" "current" {
  compartment_id = var.cc_compartment_ocid
}
