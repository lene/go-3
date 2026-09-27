variable "aws_region" {
  description = "AWS region for the go3d read-only stack."
  type        = string
  default     = "eu-central-1"
}

variable "environment" {
  description = "Deployment environment tag value."
  type        = string
  default     = "prod"
}

variable "tags" {
  description = "Additional tags to apply to go3d resources. Required go3d tags override conflicting keys."
  type        = map(string)
  default     = {}
}

variable "active_games_table_name" {
  description = "DynamoDB table name for active game state."
  type        = string
  default     = "go3d-active-games"
}

variable "players_table_name" {
  description = "DynamoDB table name for player registrations."
  type        = string
  default     = "go3d-players"
}

variable "archive_bucket_name" {
  description = "S3 archive bucket name. Defaults to go3d-game-archives-<account_id>-<region>."
  type        = string
  default     = null

  validation {
    condition     = var.archive_bucket_name == null || length(trimspace(var.archive_bucket_name)) > 0
    error_message = "archive_bucket_name must be null or a non-empty string."
  }
}

variable "lambda_function_name" {
  description = "Read-only Lambda function name."
  type        = string
  default     = "go3d-read"
}

variable "lambda_role_name" {
  description = "IAM role name for the read-only Lambda function."
  type        = string
  default     = "go3d-lambda-readonly-role"
}

variable "lambda_runtime" {
  description = "Lambda runtime for the assembled Scala/JVM artifact."
  type        = string
  default     = "java21"
}

variable "lambda_handler" {
  description = "Lambda handler entry point."
  type        = string
  default     = "go3d.server.lambda.LambdaHandler::handleRequest"
}

variable "lambda_jar_path" {
  description = "Path to the sbt assembly JAR to deploy."
  type        = string

  validation {
    condition     = length(trimspace(var.lambda_jar_path)) > 0
    error_message = "lambda_jar_path must point to an assembly JAR."
  }
}

variable "lambda_memory_mb" {
  description = "Lambda memory size in MB."
  type        = number
  default     = 512

  validation {
    condition     = var.lambda_memory_mb >= 128 && var.lambda_memory_mb <= 10240
    error_message = "lambda_memory_mb must be between 128 and 10240."
  }
}

variable "lambda_timeout_seconds" {
  description = "Lambda timeout in seconds."
  type        = number
  default     = 30

  validation {
    condition     = var.lambda_timeout_seconds >= 1 && var.lambda_timeout_seconds <= 900
    error_message = "lambda_timeout_seconds must be between 1 and 900."
  }
}

variable "api_name" {
  description = "HTTP API Gateway name."
  type        = string
  default     = "go3d-read"
}

variable "api_throttling_burst_limit" {
  description = "Maximum concurrent request burst the HTTP API accepts before returning 429."
  type        = number
  default     = 20
}

variable "api_throttling_rate_limit" {
  description = "Steady-state requests per second the HTTP API accepts before returning 429."
  type        = number
  default     = 10
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days."
  type        = number
  default     = 7
}

variable "alarm_actions" {
  description = "SNS topic ARNs or other action ARNs to notify when alarms fire."
  type        = list(string)
  default     = []
}

variable "lambda_error_alarm_threshold" {
  description = "Lambda error count threshold over the alarm period."
  type        = number
  default     = 1
}

variable "api_4xx_alarm_threshold" {
  description = "HTTP API 4xx count threshold over the alarm period."
  type        = number
  default     = 20
}

variable "api_5xx_alarm_threshold" {
  description = "HTTP API 5xx count threshold over the alarm period."
  type        = number
  default     = 1
}

variable "dynamodb_alarm_threshold" {
  description = "DynamoDB throttle/system error count threshold over the alarm period."
  type        = number
  default     = 1
}

variable "billing_alarm_enabled" {
  description = "Create an account-level AWS Billing alarm in us-east-1."
  type        = bool
  default     = true
}

variable "billing_alarm_threshold_usd" {
  description = "Account-level estimated monthly spend threshold for the billing alarm."
  type        = number
  default     = 10
}

variable "billing_currency" {
  description = "Currency dimension for the AWS Billing EstimatedCharges metric."
  type        = string
  default     = "USD"
}

variable "lambda_insights_init_duration_alarm_enabled" {
  description = "Create a Lambda Insights init_duration p95 alarm. Enable only if Lambda Insights metrics are emitted for this function."
  type        = bool
  default     = false
}

variable "lambda_insights_function_version_dimension" {
  description = "Lambda Insights version dimension to use when the init_duration alarm is enabled."
  type        = string
  default     = "$LATEST"
}

variable "lambda_init_duration_alarm_threshold_ms" {
  description = "Lambda Insights init_duration p95 threshold in milliseconds."
  type        = number
  default     = 5000
}
