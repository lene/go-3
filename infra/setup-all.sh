#!/usr/bin/env bash
# Run all go3d AWS infrastructure setup scripts (issue #118)
# Usage: ./infra/setup-all.sh [--region us-east-1] [--alarm-sns-arn arn:aws:sns:...]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse optional arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --region)
      export AWS_REGION="$2"
      shift 2
      ;;
    --alarm-sns-arn)
      export ALARM_SNS_ARN="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: $0 [--region REGION] [--alarm-sns-arn ARN]"
      exit 1
      ;;
  esac
done

echo "=== go3d AWS Infrastructure Setup ==="
echo ""

# Verify AWS CLI is configured
if ! aws sts get-caller-identity > /dev/null 2>&1; then
  echo "ERROR: AWS CLI not configured. Run 'aws configure' first."
  exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION="${AWS_REGION:-us-east-1}"
echo "Account: $ACCOUNT_ID"
echo "Region:  $REGION"
echo ""

echo "--- Step 1: DynamoDB tables ---"
bash "${SCRIPT_DIR}/setup-dynamodb.sh"
echo ""

echo "--- Step 2: S3 bucket ---"
bash "${SCRIPT_DIR}/setup-s3.sh"
echo ""

echo "--- Step 3: IAM roles and policies ---"
bash "${SCRIPT_DIR}/setup-iam.sh"
echo ""

echo "--- Step 4: CloudWatch alarms ---"
bash "${SCRIPT_DIR}/setup-cloudwatch.sh"
echo ""

echo "=== Infrastructure setup complete ==="
echo ""
echo "Next steps:"
echo "  1. Note the IAM role ARNs for Lambda deployment (Phase 5 / issue #121)"
echo "  2. Test DynamoDB access: aws dynamodb list-tables"
echo "  3. Test S3 access: aws s3 ls s3://go3d-game-archives"
echo "  4. Verify alarms: aws cloudwatch describe-alarms --alarm-name-prefix go3d"
