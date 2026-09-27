terraform {
  required_version = ">= 1.10.0, < 2.0.0" # 1.10: S3 backend native locking

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0, < 7.0"
    }
  }

  # Settings in backend.hcl (see backend.hcl.example); created by ../bootstrap
  backend "s3" {}
}
