# Import the existing reserved public IP before the first plan. Terraform must
# never create a second address or replace the one currently serving the VM.
resource "oci_core_public_ip" "dev" {
  compartment_id = var.network_compartment_ocid
  display_name   = var.reserved_public_ip_name
  lifetime       = "RESERVED"
  private_ip_id  = local.primary_private_ip_id

  freeform_tags = {
    Environment = "development"
    ManagedBy   = "terraform"
    Project     = "cc-service"
  }

  lifecycle {
    prevent_destroy = true
  }
}

# Import the existing record before the first plan. The API remains on the
# same hostname and public IP throughout deployment.
resource "cloudflare_dns_record" "dev" {
  zone_id = var.cloudflare_zone_id
  name    = var.cloudflare_record_name
  type    = "A"
  content = oci_core_public_ip.dev.ip_address
  ttl     = 300
  proxied = false
  comment = "cc-service development API managed by Terraform"

  lifecycle {
    prevent_destroy = true
  }
}
