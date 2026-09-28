# The product image bucket and its CloudFront distribution already exist and are managed outside Terraform.
# Only the product-service task role gets access, limited to the prefix the application writes.

locals {
  product_image_bucket_arn = "arn:aws:s3:::${var.product_image_bucket}"
  product_image_prefix     = "products/images/"
}

resource "aws_iam_role" "product_service_task" {
  name               = "${var.name_prefix}-product-service-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "product_service_task" {
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

resource "aws_iam_role_policy" "product_service_task" {
  name   = "product-images"
  role   = aws_iam_role.product_service_task.id
  policy = data.aws_iam_policy_document.product_service_task.json
}
