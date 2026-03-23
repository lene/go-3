#!/usr/bin/env bash
# Setup CloudWatch alarms for go3d cost and error monitoring (issue #118)
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Optional: SNS topic ARN for alarm notifications (leave empty to skip notifications)
ALARM_SNS_ARN="${ALARM_SNS_ARN:-}"

echo "Setting up CloudWatch alarms in region $REGION..."

# Helper: create alarm with optional SNS notification
create_alarm() {
  local name="$1"
  shift
  if [ -n "$ALARM_SNS_ARN" ]; then
    aws cloudwatch put-metric-alarm --region "$REGION" \
      --alarm-name "$name" \
      --alarm-actions "$ALARM_SNS_ARN" \
      "$@"
  else
    aws cloudwatch put-metric-alarm --region "$REGION" \
      --alarm-name "$name" \
      "$@"
  fi
  echo "  Created alarm: $name"
}

echo "Creating cost alarm (alert when monthly spend exceeds \$10)..."
# Note: AWS Billing metrics are only available in us-east-1
aws cloudwatch put-metric-alarm \
  --region us-east-1 \
  --alarm-name go3d-monthly-cost-alarm \
  --alarm-description "Alert when go3d monthly AWS spend exceeds \$10" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 86400 \
  --evaluation-periods 1 \
  --threshold 10.0 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=ServiceName,Value=AWSLambda \
  --treat-missing-data notBreaching \
  ${ALARM_SNS_ARN:+--alarm-actions "$ALARM_SNS_ARN"}
echo "  Created alarm: go3d-monthly-cost-alarm"

echo "Creating DynamoDB error rate alarm..."
create_alarm "go3d-dynamodb-errors" \
  --alarm-description "DynamoDB system errors exceed 1% for go3d tables" \
  --metric-name SystemErrors \
  --namespace AWS/DynamoDB \
  --statistic Sum \
  --period 300 \
  --evaluation-periods 3 \
  --threshold 10 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=TableName,Value=go3d-active-games \
  --treat-missing-data notBreaching

echo "Creating DynamoDB throttle alarm..."
create_alarm "go3d-dynamodb-throttles" \
  --alarm-description "DynamoDB request throttles detected for go3d tables" \
  --metric-name ThrottledRequests \
  --namespace AWS/DynamoDB \
  --statistic Sum \
  --period 60 \
  --evaluation-periods 5 \
  --threshold 50 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=TableName,Value=go3d-active-games \
  --treat-missing-data notBreaching

echo "Creating Lambda error rate alarm..."
create_alarm "go3d-lambda-errors" \
  --alarm-description "Lambda error rate exceeds 1% for go3d functions" \
  --metric-name Errors \
  --namespace AWS/Lambda \
  --statistic Sum \
  --period 300 \
  --evaluation-periods 3 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=FunctionName,Value=go3d-read \
  --treat-missing-data notBreaching

echo "Creating Lambda cold start duration alarm..."
create_alarm "go3d-lambda-cold-start" \
  --alarm-description "Lambda init duration (cold start) exceeds 5 seconds" \
  --metric-name InitDuration \
  --namespace AWS/Lambda \
  --statistic p95 \
  --extended-statistic p95 \
  --period 3600 \
  --evaluation-periods 3 \
  --threshold 5000 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=FunctionName,Value=go3d-read \
  --treat-missing-data notBreaching

echo "Setting CloudWatch log retention to 7 days for Lambda logs..."
aws logs put-retention-policy \
  --region "$REGION" \
  --log-group-name /aws/lambda/go3d-read \
  --retention-in-days 7 2>/dev/null || true

aws logs put-retention-policy \
  --region "$REGION" \
  --log-group-name /aws/lambda/go3d-write \
  --retention-in-days 7 2>/dev/null || true

echo ""
echo "CloudWatch setup complete."
echo ""
echo "Alarms created:"
echo "  go3d-monthly-cost-alarm    (billing > \$10/month, us-east-1)"
echo "  go3d-dynamodb-errors       (DynamoDB system errors)"
echo "  go3d-dynamodb-throttles    (DynamoDB throttled requests)"
echo "  go3d-lambda-errors         (Lambda error rate)"
echo "  go3d-lambda-cold-start     (Lambda p95 cold start > 5s)"
if [ -z "$ALARM_SNS_ARN" ]; then
  echo ""
  echo "Note: No SNS topic configured. Set ALARM_SNS_ARN to receive alarm notifications."
fi
