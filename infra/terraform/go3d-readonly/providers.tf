provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

provider "aws" {
  alias  = "billing"
  region = "us-east-1"

  default_tags {
    tags = local.common_tags
  }
}
