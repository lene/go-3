locals {
  required_tags = {
    Project     = "go3d"
    Environment = var.environment
    Phase       = "lambda-migration"
  }

  common_tags = merge(var.tags, local.required_tags)

  # appended to names that are fixed per account, so a staging stack does not collide with prod
  name_suffix = var.environment == "prod" ? "" : "-${var.environment}"

  archive_bucket_name = coalesce(
    var.archive_bucket_name,
    join("-", [
      "go3d-game-archives", data.aws_caller_identity.current.account_id,
      "${var.aws_region}${local.name_suffix}"
    ])
  )

  read_routes = toset([
    "GET /health",
    "GET /status/{gameId}",
    "GET /openGames",
  ])

  write_routes = toset([
    "GET /new/{size}",
    "GET /register/{gameId}/{color}",
    "GET /set/{gameId}/{x}/{y}/{z}",
    "GET /pass/{gameId}",
  ])

  api_routes = (
    var.enable_write_routes ? setunion(local.read_routes, local.write_routes) : local.read_routes
  )

  api_error_alarms = {
    "4xx" = {
      threshold   = var.api_4xx_alarm_threshold
      description = "HTTP API client errors for go3d read-only routes"
    }
    "5xx" = {
      threshold   = var.api_5xx_alarm_threshold
      description = "HTTP API server errors for go3d read-only routes"
    }
  }

  dynamodb_read_operations = {
    active_games_get_item = {
      table_name = aws_dynamodb_table.active_games.name
      operation  = "GetItem"
    }
    players_scan = {
      table_name = aws_dynamodb_table.players.name
      operation  = "Scan"
    }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_dynamodb_table" "active_games" {
  name         = var.active_games_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "gameId"

  attribute {
    name = "gameId"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }

  server_side_encryption {
    enabled = true
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = var.active_games_table_name
  }
}

resource "aws_dynamodb_table" "players" {
  name         = var.players_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "gameId"
  range_key    = "color"

  attribute {
    name = "gameId"
    type = "S"
  }

  attribute {
    name = "color"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }

  server_side_encryption {
    enabled = true
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = var.players_table_name
  }
}

# No access logging: private bucket of finished games; a log bucket is not worth the cost.
resource "aws_s3_bucket" "archive" { # NOSONAR
  bucket = local.archive_bucket_name

  tags = {
    Name = local.archive_bucket_name
  }
}

resource "aws_s3_bucket_ownership_controls" "archive" {
  bucket = aws_s3_bucket.archive.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "archive" {
  bucket = aws_s3_bucket.archive.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

data "aws_iam_policy_document" "archive_tls_only" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.archive.arn,
      "${aws_s3_bucket.archive.arn}/*",
    ]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "archive_tls_only" {
  bucket = aws_s3_bucket.archive.id
  policy = data.aws_iam_policy_document.archive_tls_only.json

  depends_on = [aws_s3_bucket_public_access_block.archive]
}

resource "aws_s3_bucket_versioning" "archive" {
  bucket = aws_s3_bucket.archive.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "archive" {
  bucket = aws_s3_bucket.archive.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }

    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "archive" {
  bucket = aws_s3_bucket.archive.id

  rule {
    id     = "archives-transition"
    status = "Enabled"

    filter {
      prefix = "archives/"
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 365
      storage_class = "DEEP_ARCHIVE"
    }

    noncurrent_version_transition {
      noncurrent_days = 30
      storage_class   = "GLACIER"
    }
  }

  depends_on = [aws_s3_bucket_versioning.archive]
}

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda_readonly" {
  name               = var.lambda_role_name
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
  description        = "Read-only Lambda role for go3d status, openGames, and health endpoints"

  tags = {
    Name = var.lambda_role_name
  }
}

resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_readonly.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "lambda_read" {
  statement {
    sid = "DynamoDBReadOnly"

    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:DescribeTable",
      "dynamodb:GetItem",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]

    resources = [
      aws_dynamodb_table.active_games.arn,
      aws_dynamodb_table.players.arn,
    ]
  }

  statement {
    sid = "S3ReadOnly"

    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
    ]

    resources = [aws_s3_bucket.archive.arn]
  }

  statement {
    sid = "S3ObjectReadOnly"

    actions = ["s3:GetObject"]

    resources = ["${aws_s3_bucket.archive.arn}/*"]
  }
}

resource "aws_iam_role_policy" "lambda_read" {
  name   = "go3d-lambda-readonly"
  role   = aws_iam_role.lambda_readonly.id
  policy = data.aws_iam_policy_document.lambda_read.json
}

# Writes of the game service (GameService, DynamoDBGameStore, S3GameArchive): conditional puts
# and updates, the completion transaction, and archive uploads.
data "aws_iam_policy_document" "lambda_write" {
  statement {
    sid = "DynamoDBWrite"

    actions = [
      "dynamodb:ConditionCheckItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
    ]

    resources = [
      aws_dynamodb_table.active_games.arn,
      aws_dynamodb_table.players.arn,
    ]
  }

  statement {
    sid = "S3ArchiveWrite"

    actions = ["s3:PutObject"]

    resources = ["${aws_s3_bucket.archive.arn}/archives/*"]
  }
}

resource "aws_iam_role_policy" "lambda_write" {
  count = var.enable_write_routes ? 1 : 0

  name   = "go3d-lambda-write"
  role   = aws_iam_role.lambda_readonly.id
  policy = data.aws_iam_policy_document.lambda_write.json
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.lambda_function_name}"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "/aws/lambda/${var.lambda_function_name}"
  }
}

resource "aws_lambda_function" "read" {
  function_name    = var.lambda_function_name
  role             = aws_iam_role.lambda_readonly.arn
  runtime          = var.lambda_runtime
  handler          = var.lambda_handler
  filename         = var.lambda_jar_path
  source_code_hash = filebase64sha256(var.lambda_jar_path)
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout_seconds

  environment {
    variables = {
      AWS_REGION             = var.aws_region
      DYNAMODB_GAMES_TABLE   = aws_dynamodb_table.active_games.name
      DYNAMODB_PLAYERS_TABLE = aws_dynamodb_table.players.name
      S3_ARCHIVE_BUCKET      = aws_s3_bucket.archive.bucket
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.lambda,
    aws_iam_role_policy.lambda_read,
    aws_iam_role_policy.lambda_write,
    aws_iam_role_policy_attachment.lambda_basic_execution,
  ]

  tags = {
    Name = var.lambda_function_name
  }
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/apigateway/${var.api_name}"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "/aws/apigateway/${var.api_name}"
  }
}

resource "aws_apigatewayv2_api" "read" {
  name          = var.api_name
  protocol_type = "HTTP"

  tags = {
    Name = var.api_name
  }
}

resource "aws_apigatewayv2_integration" "read_lambda" {
  api_id                 = aws_apigatewayv2_api.read.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.read.invoke_arn
  integration_method     = "POST"
  payload_format_version = "1.0"
  timeout_milliseconds   = 30000
}

resource "aws_apigatewayv2_route" "read" {
  for_each = local.api_routes

  api_id    = aws_apigatewayv2_api.read.id
  route_key = each.value
  target    = "integrations/${aws_apigatewayv2_integration.read_lambda.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.read.id
  name        = "$default"
  auto_deploy = true

  # Throttling at the API replaces the per-IP RateLimiter of the http4s server, which Lambda
  # cannot use because its state is per instance.
  default_route_settings {
    throttling_burst_limit = var.api_throttling_burst_limit
    throttling_rate_limit  = var.api_throttling_rate_limit
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api.arn
    format = jsonencode({
      error              = "$context.error.message"
      integrationError   = "$context.integrationErrorMessage"
      integrationLatency = "$context.integrationLatency"
      ip                 = "$context.identity.sourceIp"
      protocol           = "$context.protocol"
      requestId          = "$context.requestId"
      requestTime        = "$context.requestTime"
      routeKey           = "$context.routeKey"
      status             = "$context.status"
    })
  }

  tags = {
    Name = "${var.api_name}-$default"
  }
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.read.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.read.execution_arn}/*/*"
}

resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.lambda_function_name}-errors"
  alarm_description   = "Lambda errors for ${var.lambda_function_name}"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = var.lambda_error_alarm_threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.read.function_name
  }

  alarm_actions = var.alarm_actions
  ok_actions    = var.alarm_actions

  tags = {
    Name = "${var.lambda_function_name}-errors"
  }
}

resource "aws_cloudwatch_metric_alarm" "lambda_init_duration" {
  count = var.lambda_insights_init_duration_alarm_enabled ? 1 : 0

  alarm_name          = "${var.lambda_function_name}-init-duration-p95"
  alarm_description   = "Lambda Insights init_duration p95 for ${var.lambda_function_name}"
  namespace           = "LambdaInsights"
  metric_name         = "init_duration"
  extended_statistic  = "p95"
  period              = 3600
  evaluation_periods  = 3
  datapoints_to_alarm = 1
  threshold           = var.lambda_init_duration_alarm_threshold_ms
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  unit                = "Milliseconds"

  dimensions = {
    function_name = aws_lambda_function.read.function_name
    version       = var.lambda_insights_function_version_dimension
  }

  alarm_actions = var.alarm_actions
  ok_actions    = var.alarm_actions

  tags = {
    Name = "${var.lambda_function_name}-init-duration-p95"
  }
}

resource "aws_cloudwatch_metric_alarm" "api_errors" {
  for_each = local.api_error_alarms

  alarm_name          = "${var.api_name}-api-${each.key}"
  alarm_description   = each.value.description
  namespace           = "AWS/ApiGateway"
  metric_name         = each.key
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = each.value.threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    ApiId = aws_apigatewayv2_api.read.id
    Stage = aws_apigatewayv2_stage.default.name
  }

  alarm_actions = var.alarm_actions
  ok_actions    = var.alarm_actions

  tags = {
    Name = "${var.api_name}-api-${each.key}"
  }
}

resource "aws_cloudwatch_metric_alarm" "dynamodb_throttles" {
  for_each = local.dynamodb_read_operations

  alarm_name          = "go3d-dynamodb-${replace(each.key, "_", "-")}-throttles${local.name_suffix}"
  alarm_description   = "DynamoDB throttled requests for ${each.value.table_name} ${each.value.operation}"
  namespace           = "AWS/DynamoDB"
  metric_name         = "ThrottledRequests"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = var.dynamodb_alarm_threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    Operation = each.value.operation
    TableName = each.value.table_name
  }

  alarm_actions = var.alarm_actions
  ok_actions    = var.alarm_actions

  tags = {
    Name = "go3d-dynamodb-${replace(each.key, "_", "-")}-throttles${local.name_suffix}"
  }
}

resource "aws_cloudwatch_metric_alarm" "dynamodb_system_errors" {
  for_each = local.dynamodb_read_operations

  alarm_name = (
    "go3d-dynamodb-${replace(each.key, "_", "-")}-system-errors${local.name_suffix}"
  )
  alarm_description   = "DynamoDB system errors for ${each.value.table_name} ${each.value.operation}"
  namespace           = "AWS/DynamoDB"
  metric_name         = "SystemErrors"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = var.dynamodb_alarm_threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    Operation = each.value.operation
    TableName = each.value.table_name
  }

  alarm_actions = var.alarm_actions
  ok_actions    = var.alarm_actions

  tags = {
    Name = "go3d-dynamodb-${replace(each.key, "_", "-")}-system-errors${local.name_suffix}"
  }
}

resource "aws_cloudwatch_metric_alarm" "monthly_cost" {
  count    = var.billing_alarm_enabled ? 1 : 0
  provider = aws.billing

  alarm_name          = "go3d-account-monthly-cost"
  alarm_description   = "Account-level estimated monthly AWS spend"
  namespace           = "AWS/Billing"
  metric_name         = "EstimatedCharges"
  statistic           = "Maximum"
  period              = 21600
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = var.billing_alarm_threshold_usd
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    Currency = var.billing_currency
  }

  alarm_actions = var.alarm_actions
  ok_actions    = var.alarm_actions

  tags = {
    Name = "go3d-account-monthly-cost"
  }
}

resource "aws_cloudwatch_query_definition" "lambda_baseline" {
  name = "go3d/read/lambda-baseline${local.name_suffix}"

  log_group_names = [
    aws_cloudwatch_log_group.lambda.name,
  ]

  query_string = <<-EOT
    filter @type = "REPORT"
    | stats
        pct(@duration, 50) as duration_p50_ms,
        pct(@duration, 95) as duration_p95_ms,
        pct(@duration, 99) as duration_p99_ms,
        max(@duration) as duration_max_ms,
        pct(@initDuration, 50) as init_p50_ms,
        pct(@initDuration, 95) as init_p95_ms,
        pct(@initDuration, 99) as init_p99_ms,
        max(@initDuration) as init_max_ms,
        count(*) as invocation_reports
  EOT
}
