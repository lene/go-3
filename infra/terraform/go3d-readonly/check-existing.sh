#!/usr/bin/env bash
# Checks whether the go3d read-only stack already exists outside the new S3 state: resources in
# AWS (created by an earlier apply or by the infra/setup-*.sh scripts) and GitLab-managed state.
# Run before the first `terraform apply` with admin AWS credentials; see README.md.
set -uo pipefail

REGION=${AWS_REGION:-eu-central-1}
found=0

check() {  # description, command...
  local what=$1; shift
  if "$@" > /dev/null 2>&1; then
    echo "EXISTS   $what"
    found=1
  else
    echo "missing  $what"
  fi
  return 0
}

account=$(aws sts get-caller-identity --query Account --output text)
check "DynamoDB table go3d-active-games" \
  aws dynamodb describe-table --region "$REGION" --table-name go3d-active-games
check "DynamoDB table go3d-players" \
  aws dynamodb describe-table --region "$REGION" --table-name go3d-players
check "S3 bucket go3d-game-archives-$account-$REGION" \
  aws s3api head-bucket --bucket "go3d-game-archives-$account-$REGION"
check "IAM role go3d-lambda-readonly-role" \
  aws iam get-role --role-name go3d-lambda-readonly-role
check "Lambda function go3d-read" \
  aws lambda get-function --region "$REGION" --function-name go3d-read

echo "Other resources tagged Project=go3d:"
aws resourcegroupstaggingapi get-resources --region "$REGION" \
  --tag-filters Key=Project,Values=go3d --query 'ResourceTagMappingList[].ResourceARN' \
  --output text | tr '\t' '\n' | sed 's/^/  /'

if [[ -n "${GITLAB_TOKEN:-}" ]]; then
  state_url="https://gitlab.com/api/v4/projects/6643214/terraform/state/go3d-eu-central-1-prod"
  check "GitLab Terraform state go3d-eu-central-1-prod" \
    curl -sf --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "$state_url"
else
  echo "skipped  GitLab Terraform state (set GITLAB_TOKEN to check)"
fi

if [[ "$found" -eq 1 ]]; then
  echo "Existing resources found: import them or migrate the GitLab state before applying."
fi
