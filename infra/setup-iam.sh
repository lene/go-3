#!/usr/bin/env bash
# Setup IAM roles and policies for go3d Lambda functions (issue #118)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "Setting up IAM roles for go3d Lambda (account: $ACCOUNT_ID)..."

# Create Lambda execution role
echo "Creating Lambda execution role..."
aws iam create-role \
  --role-name go3d-lambda-role \
  --assume-role-policy-document "file://${SCRIPT_DIR}/iam-trust-policy.json" \
  --description "Execution role for go3d Lambda functions" \
  --tags Key=Project,Value=go3d

# Attach AWS managed policy for basic Lambda execution (CloudWatch Logs)
aws iam attach-role-policy \
  --role-name go3d-lambda-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

# Create and attach read-only policy (used by Phase 5 read-only Lambda)
echo "Creating go3d-lambda-read policy..."
READ_POLICY_ARN=$(aws iam create-policy \
  --policy-name go3d-lambda-read \
  --policy-document "file://${SCRIPT_DIR}/iam-policy-lambda-read.json" \
  --description "Read-only access to go3d DynamoDB tables and S3 archives" \
  --tags Key=Project,Value=go3d \
  --query 'Policy.Arn' \
  --output text)
echo "  Created: $READ_POLICY_ARN"

# Create and attach write policy (used by Phase 7 write Lambda)
echo "Creating go3d-lambda-write policy..."
WRITE_POLICY_ARN=$(aws iam create-policy \
  --policy-name go3d-lambda-write \
  --policy-document "file://${SCRIPT_DIR}/iam-policy-lambda-write.json" \
  --description "Read/write access to go3d DynamoDB tables and S3 archives" \
  --tags Key=Project,Value=go3d \
  --query 'Policy.Arn' \
  --output text)
echo "  Created: $WRITE_POLICY_ARN"

# Phase 5 (read-only) uses only the read policy
echo "Creating read-only Lambda role (Phase 5)..."
aws iam create-role \
  --role-name go3d-lambda-readonly-role \
  --assume-role-policy-document "file://${SCRIPT_DIR}/iam-trust-policy.json" \
  --description "Read-only Lambda role for go3d Phase 5 (status/openGames endpoints)" \
  --tags Key=Project,Value=go3d

aws iam attach-role-policy \
  --role-name go3d-lambda-readonly-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam attach-role-policy \
  --role-name go3d-lambda-readonly-role \
  --policy-arn "$READ_POLICY_ARN"

# Phase 7 (write) uses the write policy
echo "Attaching write policy to go3d-lambda-role (Phase 7)..."
aws iam attach-role-policy \
  --role-name go3d-lambda-role \
  --policy-arn "$WRITE_POLICY_ARN"

echo ""
echo "IAM setup complete."
echo ""
echo "Roles created:"
echo "  go3d-lambda-readonly-role  (Phase 5: /health, /status, /openGames)"
echo "  go3d-lambda-role           (Phase 7: /new, /register, /set, /pass)"
echo ""
echo "Policies:"
echo "  $READ_POLICY_ARN"
echo "  $WRITE_POLICY_ARN"
