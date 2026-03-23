#!/usr/bin/env bash
# Tear down go3d AWS infrastructure (use with care!)
# Only run this to completely remove all go3d AWS resources.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
BUCKET="go3d-game-archives"

echo "WARNING: This will DELETE all go3d AWS infrastructure including game data!"
read -r -p "Type 'yes' to confirm: " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "Aborted."
  exit 1
fi

echo "Deleting DynamoDB tables..."
aws dynamodb delete-table --region "$REGION" --table-name go3d-active-games 2>/dev/null && \
  echo "  Deleted: go3d-active-games" || echo "  Skipped: go3d-active-games (not found)"

aws dynamodb delete-table --region "$REGION" --table-name go3d-players 2>/dev/null && \
  echo "  Deleted: go3d-players" || echo "  Skipped: go3d-players (not found)"

echo "Emptying and deleting S3 bucket (including all versions)..."
aws s3api list-object-versions --bucket "$BUCKET" \
  --query '{Objects: Versions[].{Key:Key,VersionId:VersionId}}' \
  --output json 2>/dev/null | \
  aws s3api delete-objects --bucket "$BUCKET" --delete - 2>/dev/null || true

aws s3api list-object-versions --bucket "$BUCKET" \
  --query '{Objects: DeleteMarkers[].{Key:Key,VersionId:VersionId}}' \
  --output json 2>/dev/null | \
  aws s3api delete-objects --bucket "$BUCKET" --delete - 2>/dev/null || true

aws s3api delete-bucket --bucket "$BUCKET" --region "$REGION" 2>/dev/null && \
  echo "  Deleted: s3://$BUCKET" || echo "  Skipped: $BUCKET (not found)"

echo "Deleting IAM roles and policies..."
for ROLE in go3d-lambda-role go3d-lambda-readonly-role; do
  # Detach all managed policies
  ATTACHED=$(aws iam list-attached-role-policies --role-name "$ROLE" \
    --query 'AttachedPolicies[].PolicyArn' --output text 2>/dev/null || echo "")
  for POLICY_ARN in $ATTACHED; do
    aws iam detach-role-policy --role-name "$ROLE" --policy-arn "$POLICY_ARN"
  done
  aws iam delete-role --role-name "$ROLE" 2>/dev/null && \
    echo "  Deleted role: $ROLE" || echo "  Skipped role: $ROLE (not found)"
done

for POLICY_NAME in go3d-lambda-read go3d-lambda-write; do
  ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
  POLICY_ARN="arn:aws:iam::${ACCOUNT_ID}:policy/${POLICY_NAME}"
  aws iam delete-policy --policy-arn "$POLICY_ARN" 2>/dev/null && \
    echo "  Deleted policy: $POLICY_NAME" || echo "  Skipped policy: $POLICY_NAME (not found)"
done

echo "Deleting CloudWatch alarms..."
aws cloudwatch delete-alarms --region "$REGION" \
  --alarm-names \
    go3d-dynamodb-errors \
    go3d-dynamodb-throttles \
    go3d-lambda-errors \
    go3d-lambda-cold-start 2>/dev/null || true

aws cloudwatch delete-alarms --region us-east-1 \
  --alarm-names go3d-monthly-cost-alarm 2>/dev/null || true

echo "Teardown complete."
