locals {
  service_names = toset([
    "config-server",
    "eureka-server",
    "gateway",
    "user-service",
    "product-service",
    "order-service",
  ])

  common_tags = {
    Environment = "development"
    ManagedBy   = "terraform"
    Project     = "cc-service"
  }
}

resource "oci_core_network_security_group" "dev" {
  compartment_id = var.network_compartment_ocid
  vcn_id         = data.oci_core_vcn.existing.id
  display_name   = "cc-dev-nsg"
  freeform_tags  = local.common_tags
}

resource "oci_core_network_security_group_security_rule" "https" {
  network_security_group_id = oci_core_network_security_group.dev.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "Public HTTPS for the development API"

  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "http" {
  network_security_group_id = oci_core_network_security_group.dev.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "HTTP for ACME validation and HTTPS redirect"

  tcp_options {
    destination_port_range {
      min = 80
      max = 80
    }
  }
}

resource "oci_core_network_security_group_security_rule" "egress" {
  network_security_group_id = oci_core_network_security_group.dev.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
  description               = "Image pulls, Vault access, GitHub Actions, and ACME"
}

resource "oci_artifacts_container_repository" "service" {
  for_each       = local.service_names
  compartment_id = var.cc_compartment_ocid
  display_name   = "cc-dev/${each.value}"
  is_public      = false
  freeform_tags  = local.common_tags
}

resource "oci_objectstorage_bucket" "release_manifest" {
  compartment_id = var.cc_compartment_ocid
  namespace      = data.oci_objectstorage_namespace.current.namespace
  name           = var.release_manifest_bucket
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard"
  versioning     = "Enabled"
  freeform_tags  = merge(local.common_tags, { Purpose = "release-manifests" })
}

resource "oci_objectstorage_object_lifecycle_policy" "release_manifest" {
  namespace = data.oci_objectstorage_namespace.current.namespace
  bucket    = oci_objectstorage_bucket.release_manifest.name

  depends_on = [oci_identity_policy.object_storage_lifecycle]

  rules {
    action      = "DELETE"
    is_enabled  = true
    name        = "delete-candidate-manifests-after-30-days"
    target      = "objects"
    time_amount = 30
    time_unit   = "DAYS"

    object_name_filter {
      inclusion_patterns = ["candidates/*"]
    }
  }

  rules {
    action      = "DELETE"
    is_enabled  = true
    name        = "delete-previous-candidate-versions-after-30-days"
    target      = "previous-object-versions"
    time_amount = 30
    time_unit   = "DAYS"

    object_name_filter {
      inclusion_patterns = ["candidates/*"]
    }
  }

  rules {
    action      = "ABORT"
    is_enabled  = true
    name        = "abort-uncommitted-multipart-uploads-after-7-days"
    target      = "multipart-uploads"
    time_amount = 7
    time_unit   = "DAYS"
  }
}

resource "oci_identity_policy" "object_storage_lifecycle" {
  compartment_id = var.tenancy_ocid
  name           = "cc-dev-object-storage-lifecycle-policy"
  description    = "Tokyo Object Storage can execute lifecycle rules in the cc-service development compartment."
  statements = [
    "Allow service objectstorage-ap-tokyo-1 to manage object-family in compartment id ${var.cc_compartment_ocid} where any {request.permission='BUCKET_INSPECT', request.permission='BUCKET_READ', request.permission='OBJECT_INSPECT', request.permission='OBJECT_DELETE', request.permission='OBJECT_VERSION_DELETE'}",
  ]
}

resource "oci_kms_vault" "dev" {
  compartment_id = var.cc_compartment_ocid
  display_name   = "cc-dev-vault"
  vault_type     = "DEFAULT"
  freeform_tags  = local.common_tags
}

resource "oci_kms_key" "dev_secrets" {
  compartment_id      = var.cc_compartment_ocid
  display_name        = "cc-dev-secrets"
  management_endpoint = oci_kms_vault.dev.management_endpoint
  protection_mode     = "SOFTWARE"

  key_shape {
    algorithm = "AES"
    length    = 32
  }

  freeform_tags = local.common_tags
}

resource "oci_identity_dynamic_group" "dev_instance" {
  compartment_id = var.tenancy_ocid
  name           = "cc-dev-instance-dg"
  description    = "Development VM can read cc-service images and runtime secret bundles."
  matching_rule  = "instance.id = '${var.instance_ocid}'"
}

resource "oci_identity_policy" "dev_instance" {
  compartment_id = var.tenancy_ocid
  name           = "cc-dev-instance-policy"
  description    = "Least-privilege image pull and secret bundle access for cc-service development."
  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.dev_instance.name} to read repos in compartment id ${var.cc_compartment_ocid}",
    "Allow dynamic-group ${oci_identity_dynamic_group.dev_instance.name} to read secret-bundles in compartment id ${var.cc_compartment_ocid}",
  ]
}

resource "oci_identity_user" "github_actions" {
  compartment_id = var.tenancy_ocid
  name           = "cc-dev-github-actions"
  description    = "Dedicated API user for the cc-service development deployment workflow."
  email          = var.github_actions_user_email
  freeform_tags  = local.common_tags
}

resource "oci_identity_group" "github_actions" {
  compartment_id = var.tenancy_ocid
  name           = "cc-dev-github-actions-group"
  description    = "Least-privilege group for the cc-service development deployment workflow."
  freeform_tags  = local.common_tags
}

resource "oci_identity_user_group_membership" "github_actions" {
  compartment_id = var.tenancy_ocid
  group_id       = oci_identity_group.github_actions.id
  user_id        = oci_identity_user.github_actions.id
}

resource "oci_identity_policy" "github_actions" {
  compartment_id = var.tenancy_ocid
  name           = "cc-dev-github-actions-policy"
  description    = "GitHub Actions can publish cc-service images and release manifests without reading Terraform state."
  statements = [
    "Allow group ${oci_identity_group.github_actions.name} to read objectstorage-namespaces in tenancy",
    "Allow group ${oci_identity_group.github_actions.name} to read buckets in compartment id ${var.cc_compartment_ocid}",
    "Allow group ${oci_identity_group.github_actions.name} to manage objects in compartment id ${var.cc_compartment_ocid} where target.bucket.name='${oci_objectstorage_bucket.release_manifest.name}'",
    "Allow group ${oci_identity_group.github_actions.name} to manage repos in compartment id ${var.cc_compartment_ocid}",
  ]
}
