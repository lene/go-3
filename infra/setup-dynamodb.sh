#!/usr/bin/env bash
# Setup DynamoDB tables for go3d (issue #118)
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"

echo "Creating DynamoDB tables in region $REGION..."

# go3d-active-games: stores active game state, TTL-based auto-expiry
aws dynamodb create-table \
  --region "$REGION" \
  --table-name go3d-active-games \
  --attribute-definitions AttributeName=gameId,AttributeType=S \
  --key-schema AttributeName=gameId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --sse-specification Enabled=true \
  --tags Key=Project,Value=go3d Key=Environment,Value=production

echo "Enabling TTL on go3d-active-games..."
aws dynamodb update-time-to-live \
  --region "$REGION" \
  --table-name go3d-active-games \
  --time-to-live-specification Enabled=true,AttributeName=expiresAt

# go3d-players: stores player registrations, TTL = 24h after creation
aws dynamodb create-table \
  --region "$REGION" \
  --table-name go3d-players \
  --attribute-definitions \
    AttributeName=gameId,AttributeType=S \
    AttributeName=color,AttributeType=S \
  --key-schema \
    AttributeName=gameId,KeyType=HASH \
    AttributeName=color,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --sse-specification Enabled=true \
  --tags Key=Project,Value=go3d Key=Environment,Value=production

echo "Enabling TTL on go3d-players..."
aws dynamodb update-time-to-live \
  --region "$REGION" \
  --table-name go3d-players \
  --time-to-live-specification Enabled=true,AttributeName=expiresAt

# Enable point-in-time recovery on both tables
echo "Enabling point-in-time recovery..."
aws dynamodb update-continuous-backups \
  --region "$REGION" \
  --table-name go3d-active-games \
  --point-in-time-recovery-specification PointInTimeRecoveryEnabled=true

aws dynamodb update-continuous-backups \
  --region "$REGION" \
  --table-name go3d-players \
  --point-in-time-recovery-specification PointInTimeRecoveryEnabled=true

echo "DynamoDB setup complete."
echo ""
echo "Tables created:"
echo "  go3d-active-games  (partition key: gameId, TTL: expiresAt)"
echo "  go3d-players       (partition key: gameId, sort key: color, TTL: expiresAt)"
