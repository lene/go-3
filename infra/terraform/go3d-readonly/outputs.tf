output "api_base_url" {
  description = "Base invoke URL for the read-only HTTP API."
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "api_id" {
  description = "API Gateway HTTP API ID."
  value       = aws_apigatewayv2_api.read.id
}

output "lambda_function_arn" {
  description = "ARN of the read-only Lambda function."
  value       = aws_lambda_function.read.arn
}

output "lambda_function_name" {
  description = "Name of the read-only Lambda function."
  value       = aws_lambda_function.read.function_name
}

output "active_games_table_name" {
  description = "DynamoDB active games table name."
  value       = aws_dynamodb_table.active_games.name
}

output "players_table_name" {
  description = "DynamoDB players table name."
  value       = aws_dynamodb_table.players.name
}

output "archive_bucket_name" {
  description = "S3 archive bucket name."
  value       = aws_s3_bucket.archive.bucket
}

output "lambda_log_group_name" {
  description = "CloudWatch log group for the read-only Lambda function."
  value       = aws_cloudwatch_log_group.lambda.name
}

output "api_log_group_name" {
  description = "CloudWatch log group for HTTP API access logs."
  value       = aws_cloudwatch_log_group.api.name
}

output "lambda_baseline_query_name" {
  description = "CloudWatch Logs Insights query definition for Lambda baseline metrics."
  value       = aws_cloudwatch_query_definition.lambda_baseline.name
}

output "alarm_names" {
  description = "CloudWatch alarm names managed by this stack."
  value = concat(
    [aws_cloudwatch_metric_alarm.lambda_errors.alarm_name],
    [for alarm in aws_cloudwatch_metric_alarm.api_errors : alarm.alarm_name],
    [for alarm in aws_cloudwatch_metric_alarm.dynamodb_throttles : alarm.alarm_name],
    [for alarm in aws_cloudwatch_metric_alarm.dynamodb_system_errors : alarm.alarm_name],
    [for alarm in aws_cloudwatch_metric_alarm.lambda_init_duration : alarm.alarm_name],
    [for alarm in aws_cloudwatch_metric_alarm.monthly_cost : alarm.alarm_name],
  )
}
