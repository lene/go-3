variable "aws_region" {
  description = "AWS region for the state bucket."
  type        = string
  default     = "eu-central-1"
}

variable "github_repository" {
  description = "GitHub repository (owner/name) whose workflows may assume the roles."
  type        = string
  default     = "lene/go-3"
}

variable "state_bucket_name" {
  description = "Terraform state bucket. Defaults to go3d-terraform-state-<account_id>-<region>."
  type        = string
  default     = null
}

variable "create_github_oidc_provider" {
  description = "Create the GitHub Actions OIDC provider (set false if the account already has one)."
  type        = bool
  default     = true
}
