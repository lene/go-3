#!/usr/bin/env bash
# Setup S3 bucket for go3d game archives (issue #118)
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
BUCKET="go3d-game-archives"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Creating S3 bucket $BUCKET in region $REGION..."

if [ "$REGION" = "us-east-1" ]; then
  # us-east-1 does not accept a LocationConstraint
  aws s3api create-bucket \
    --bucket "$BUCKET" \
    --region "$REGION" \
    --object-ownership BucketOwnerEnforced
else
  aws s3api create-bucket \
    --bucket "$BUCKET" \
    --region "$REGION" \
    --object-ownership BucketOwnerEnforced \
    --create-bucket-configuration LocationConstraint="$REGION"
fi

echo "Blocking public access..."
aws s3api put-public-access-block \
  --bucket "$BUCKET" \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

echo "Enabling versioning..."
aws s3api put-bucket-versioning \
  --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled

echo "Enabling server-side encryption (AES-256)..."
aws s3api put-bucket-encryption \
  --bucket "$BUCKET" \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      },
      "BucketKeyEnabled": true
    }]
  }'

echo "Applying lifecycle policy (Standard -> Standard-IA at 30d -> Deep Archive at 365d)..."
aws s3api put-bucket-lifecycle-configuration \
  --bucket "$BUCKET" \
  --lifecycle-configuration "file://${SCRIPT_DIR}/s3-lifecycle-policy.json"

echo "Enabling S3 access logging..."
aws s3api put-bucket-logging \
  --bucket "$BUCKET" \
  --bucket-logging-status '{
    "LoggingEnabled": {
      "TargetBucket": "'"$BUCKET"'",
      "TargetPrefix": ".access-logs/"
    }
  }'

echo "S3 setup complete."
echo ""
echo "Bucket: s3://$BUCKET"
echo "  - Public access: blocked"
echo "  - Versioning: enabled"
echo "  - Encryption: AES-256 (SSE-S3)"
echo "  - Lifecycle: Standard -> Standard-IA (30d) -> Deep Archive (365d)"
echo "  - Access logging: enabled (.access-logs/ prefix)"
