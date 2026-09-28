# Product images live in a private bucket of this account and are served only through CloudFront.
# product-service writes with the ECS task role on AWS and with the OCI IAM user on the OCI dev server.

locals {
  product_image_bucket     = "${var.name_prefix}-product-images-${data.aws_caller_identity.current.account_id}"
  product_image_bucket_arn = "arn:aws:s3:::${local.product_image_bucket}"
  product_image_prefix     = "products/images/"
}

resource "aws_s3_bucket" "product_images" {
  bucket = local.product_image_bucket

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "product_images" {
  bucket                  = aws_s3_bucket.product_images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "product_images" {
  bucket = aws_s3_bucket.product_images.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "product_images" {
  bucket = aws_s3_bucket.product_images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Browsers upload directly with the presigned PUT URL, so the frontend origin must be allowed here.
resource "aws_s3_bucket_cors_configuration" "product_images" {
  bucket = aws_s3_bucket.product_images.id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = var.product_image_cors_origins
    allowed_headers = ["Content-Type"]
    max_age_seconds = 3000
  }
}

resource "aws_cloudfront_origin_access_control" "product_images" {
  name                              = local.product_image_bucket
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_distribution" "product_images" {
  enabled     = true
  comment     = "${var.name_prefix} product images"
  price_class = "PriceClass_200"

  origin {
    origin_id                = "product-images"
    domain_name              = aws_s3_bucket.product_images.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.product_images.id
  }

  default_cache_behavior {
    target_origin_id       = "product-images"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
    compress               = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

# Only this CloudFront distribution may read objects. Writers get access through their IAM policies below.
data "aws_iam_policy_document" "product_images_bucket" {
  statement {
    sid       = "CloudFrontRead"
    actions   = ["s3:GetObject"]
    resources = ["${local.product_image_bucket_arn}/${local.product_image_prefix}*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.product_images.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "product_images" {
  bucket = aws_s3_bucket.product_images.id
  policy = data.aws_iam_policy_document.product_images_bucket.json

  depends_on = [aws_s3_bucket_public_access_block.product_images]
}

data "aws_iam_policy_document" "product_image_writer" {
  statement {
    sid       = "ProductImageObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${local.product_image_bucket_arn}/${local.product_image_prefix}*"]
  }

  # Without ListBucket, S3 answers HeadObject on a missing key with 403 instead of 404,
  # so the application could not map it to IMAGE_UPLOAD_NOT_FOUND.
  statement {
    sid       = "ProductImageList"
    actions   = ["s3:ListBucket"]
    resources = [local.product_image_bucket_arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${local.product_image_prefix}*"]
    }
  }
}

resource "aws_iam_role" "product_service_task" {
  name               = "${var.name_prefix}-product-service-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy" "product_service_task" {
  name   = "product-images"
  role   = aws_iam_role.product_service_task.id
  policy = data.aws_iam_policy_document.product_image_writer.json
}

# The OCI dev server cannot assume an ECS task role, so it uses this user's access key.
# The key is created outside Terraform and stored only in OCI Vault, keeping the secret out of state.
resource "aws_iam_user" "oci_product_images" {
  name = "${var.name_prefix}-oci-product-images"
}

resource "aws_iam_user_policy" "oci_product_images" {
  name   = "product-images"
  user   = aws_iam_user.oci_product_images.name
  policy = data.aws_iam_policy_document.product_image_writer.json
}
