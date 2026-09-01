terraform {
  required_version = ">= 1.12.0, < 2.0.0"

  backend "oci" {}

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.0"
    }
    oci = {
      source  = "oracle/oci"
      version = "~> 8.0"
    }
  }
}

provider "oci" {}

# Cloudflare reads CLOUDFLARE_API_TOKEN from the process environment.
provider "cloudflare" {}
