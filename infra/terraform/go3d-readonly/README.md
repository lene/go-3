# go3d Read-Only Lambda Terraform

This stack deploys the issue #127 read-only Lambda migration baseline in `eu-central-1`.

Managed resources:

- DynamoDB tables `go3d-active-games` and `go3d-players`
- S3 archive bucket `go3d-game-archives-<account_id>-eu-central-1`
- IAM role `go3d-lambda-readonly-role`
- Lambda function `go3d-read`
- HTTP API routes `GET /health`, `GET /status/{gameId}`, and `GET /openGames`
- CloudWatch log groups, alarms, and a Logs Insights baseline query

All resources receive `Project=go3d`, `Environment=prod`, and `Phase=lambda-migration` tags. Use those tags for Cost Explorer cost allocation after enabling the `Project`, `Environment`, and `Phase` cost allocation tags in the AWS Billing console. The optional `go3d-account-monthly-cost` alarm is account-level because CloudWatch billing metrics are not tag-scoped.

## Build

```bash
sbt assembly
```

The default artifact path for version `0.7.29` is:

```text
target/scala-3.8.2/go-3d-lambda-0.7.29.jar
```

## Init

Use GitLab HTTP state. Do not commit `backend.hcl`, credentials, plans, or local state.

```bash
cd infra/terraform/go3d-readonly
terraform init \
  -backend-config=backend.hcl \
  -backend-config=username="$GITLAB_USERNAME" \
  -backend-config=password="$GITLAB_ACCESS_TOKEN"
```

`backend.hcl.example` contains the state addresses for project `6643214` and state name `go3d-eu-central-1-prod`.

## Plan And Apply

```bash
terraform fmt
terraform validate
terraform plan \
  -var lambda_jar_path="../../../target/scala-3.8.2/go-3d-lambda-0.7.29.jar" \
  -out=tfplan
terraform apply tfplan
```

Review the plan before applying. It should include only go3d resources.

## GitLab CI/CD

`#100` requires CI to produce the reviewed plan before `#127` is applied. The pipeline jobs are:

- `BuildLambdaAssembly`: runs `sbt assembly` and publishes `go-3d-lambda.jar`
- `TerraformValidate`: runs `terraform init -backend=false`, `terraform fmt -check`, and `terraform validate`
- `TerraformPlan`: initialises GitLab-managed HTTP state, plans with `lambda_jar_path=../../../go-3d-lambda.jar`, and publishes `tfplan` plus `tfplan.txt`
- `TerraformApply`: protected/manual production job that applies the reviewed `tfplan`

Required protected CI/CD variables:

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_SESSION_TOKEN` when temporary AWS credentials are used

Optional CI/CD variables:

- `TF_STATE_USERNAME` defaults to `gitlab-ci-token`
- `TF_STATE_PASSWORD` defaults to `CI_JOB_TOKEN`
- `TF_STATE_NAME` defaults to `go3d-eu-central-1-prod`

Configure the GitLab `production` environment as protected so only approved maintainers can run `TerraformApply`.

## Runtime Checks

```bash
API_BASE_URL="$(terraform output -raw api_base_url)"
curl -i "$API_BASE_URL/health"
curl -i "$API_BASE_URL/openGames"
curl -i "$API_BASE_URL/status/missing-game-id"
```

Expected results:

- `/health` returns HTTP 200 and body `1`
- `/openGames` returns HTTP 200 JSON
- `/status/missing-game-id` returns HTTP 404 JSON

## Baseline Metrics

After invoking the API enough times to include cold and warm executions, use the `go3d/read/lambda-baseline` Logs Insights query definition for:

- Lambda cold start p50, p95, p99, and max from `@initDuration`
- Lambda warm duration p50, p95, p99, and max from `@duration`
- Invocation report count

Use CloudWatch metrics and alarms for:

- Lambda errors
- HTTP API `4xx` and `5xx`
- DynamoDB `ThrottledRequests` and `SystemErrors`
- S3 bucket object/storage baseline
- Account-level estimated monthly spend
