output "state_bucket" {
  description = "Value for the GitHub variable TF_STATE_BUCKET and backend.hcl."
  value       = aws_s3_bucket.state.bucket
}

output "plan_role_arn" {
  description = "Value for the GitHub variable AWS_TF_PLAN_ROLE_ARN."
  value       = aws_iam_role.plan.arn
}

output "apply_role_arn" {
  description = "Value for the GitHub variable AWS_TF_APPLY_ROLE_ARN (production environment)."
  value       = aws_iam_role.apply.arn
}
