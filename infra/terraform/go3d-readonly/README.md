# go3d Read-Only Lambda Terraform

This stack deploys the issue #128 read-only Lambda migration baseline in `eu-central-1`.

Managed resources:

- DynamoDB tables `go3d-active-games` and `go3d-players`
- S3 archive bucket `go3d-game-archives-<account_id>-eu-central-1`
- IAM role `go3d-lambda-readonly-role`
- Lambda function `go3d-read`
- HTTP API routes `GET /health`, `GET /status/{gameId}`, `GET /status/{gameId}/{moveCount}`,
  `GET /archived/{gameId}` and `GET /openGames`, throttled to
  `api_throttling_rate_limit` requests per second (burst `api_throttling_burst_limit`); clients
  over the limit get HTTP 429
- CloudWatch log groups, alarms, and a Logs Insights baseline query

All resources receive `Project=go3d`, `Environment=prod`, and `Phase=lambda-migration` tags. Use those tags for Cost Explorer cost allocation after enabling the `Project`, `Environment`, and `Phase` cost allocation tags in the AWS Billing console. The optional `go3d-account-monthly-cost` alarm is account-level because CloudWatch billing metrics are not tag-scoped.

## One-time setup

1. **Bootstrap** (an AWS administrator, once): `../bootstrap` creates the S3 state bucket
   (versioned, encrypted, S3 native locking), the GitHub Actions OIDC provider and two roles:
   - `go3d-github-terraform-plan`: `ReadOnlyAccess` plus state access; assumable from pull
     requests and `master`.
   - `go3d-github-terraform-apply`: `PowerUserAccess`, IAM rights limited to `go3d-*` roles,
     and state access; assumable only by jobs in the GitHub `production` environment.

   ```bash
   cd infra/terraform/bootstrap
   terraform init && terraform apply
   terraform output
   ```
   Set `create_github_oidc_provider=false` if the account already has the GitHub OIDC provider.
   Keep the bootstrap's local `terraform.tfstate` safe (it is git-ignored).
2. **GitHub settings**:
   - Repository variables: `TF_STATE_BUCKET`, `AWS_TF_PLAN_ROLE_ARN`, `AWS_TF_APPLY_ROLE_ARN`
     (from `terraform output`).
   - Environment `production` with required reviewers (and deployment branch `master`).
3. **Existing resources**: run `./check-existing.sh` with admin credentials (and `GITLAB_TOKEN`
   to check the old GitLab-managed state). Earlier runs of the former `infra/setup-*.sh` scripts
   or an earlier apply may already have created the tables, bucket, role or function. If
   anything exists, either migrate the old state (`terraform init -migrate-state` from the GitLab
   HTTP backend) or `terraform import` the resources before the first apply.
4. **Canary**: after the first apply, set the repository variable `GO3D_API_URL` to
   `terraform output -raw api_base_url`. `.github/workflows/canary.yml` then calls the read routes
   every 15 minutes (see Baseline Metrics).

## CI/CD (`.github/workflows/terraform.yml`)

- **Terraform validate** (every PR and master push touching Terraform, the workflow, `src/main`
  or `build.sbt`): `terraform fmt -check`, `init -backend=false` and `validate` for this stack and
  the bootstrap.
- **Terraform plan** (once the variables are set): builds the Lambda assembly with `sbt assembly`,
  plans against the S3 state with the plan role, shows the plan in the job summary and uploads
  `tfplan`, `tfplan.txt` and the JAR.
- **Terraform apply** (master only, `production` environment): after a reviewer approves, applies
  exactly the uploaded plan with the apply role.

Issue #101 requires CI to produce the reviewed plan before #128 is applied.

## Staging stack

`staging.tfvars` describes a second copy of the stack with `-staging` names, its own state key
(`go3d-staging/terraform.tfstate`) and `enable_write_routes = true`: the Lambda then also serves
`GET /new/{size}`, `/register/{gameId}/{color}`, `/set/{gameId}/{x}/{y}/{z}` and
`/pass/{gameId}`, with write access to its own tables and to `archives/*` in its own bucket.
Production keeps `enable_write_routes = false` until the cutover (#125).

One-time setup:

1. Re-apply `../bootstrap`, so the apply role also trusts the GitHub environment `staging`.
2. Create the GitHub environment `staging` (deployment branch `master`; reviewers optional).

Deploy: run the Terraform workflow manually on `master` with `environment = staging`. The plan
and apply jobs use the staging state and tfvars; apply runs in the `staging` environment.

Smoke test (tokens from the register responses):

```bash
API="$(terraform output -raw api_base_url)"   # after init with the staging state key
ID=$(curl -s "$API/new/3" | jq -r .id)
BLACK=$(curl -s "$API/register/$ID/%40" | jq -r .authToken)
WHITE=$(curl -s "$API/register/$ID/O" | jq -r .authToken)
curl -s -H "Authentication: Bearer $BLACK" "$API/set/$ID/2/2/2"
curl -s -H "Authentication: Bearer $WHITE" "$API/pass/$ID"
curl -s -H "Authentication: Bearer $BLACK" "$API/pass/$ID"   # game over: archived to S3
curl -s "$API/status/$ID" | jq .over
```

Full game: set the repository variable `GO3D_STAGING_API_URL` to the staging `api_base_url`, then
run the `Staging gameplay` workflow (`.github/workflows/staging-gameplay.yml`) by hand. It builds
the clients and plays one bot-vs-bot game through the API with `ci/test-gameplay.sh` and
`API_URL`; the bots poll every 500 ms to stay below the API throttling limit. The same works
locally:

```bash
sbt "Universal / packageBin" && unzip -q target/universal/go-3d-*.zip
API_URL="$API" INSTALL_DIR="$(ls -d "$PWD"/go-3d-*/)" BOARD_SIZE=5 POLL_INTERVAL_MS=500 \
  ci/test-gameplay.sh
```

## Local use

```bash
sbt assembly
cd infra/terraform/go3d-readonly
cp backend.hcl.example backend.hcl   # fill in the bucket name
terraform init -backend-config=backend.hcl
terraform plan -var lambda_jar_path="$(ls ../../../target/scala-*/go-3d-lambda-*.jar)" -out=tfplan
terraform apply tfplan
```

Review the plan before applying. It should include only go3d resources.

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

The canary workflow (`.github/workflows/canary.yml`) calls `/health`, `/openGames` and
`/status/CANARY` every 15 minutes, which includes cold and warm executions. Each run records
status codes and response times in its job summary, and fails on a 5xx or on a response slower
than 10 seconds. GitHub disables scheduled workflows after 60 days without repository activity;
re-enable it in the Actions tab if that happens.

After the canary has run for at least a week, use the `go3d/read/lambda-baseline` Logs Insights
query definition for:

- Lambda cold start p50, p95, p99, and max from `@initDuration`
- Lambda warm duration p50, p95, p99, and max from `@duration`
- Invocation report count

Use CloudWatch metrics and alarms for:

- Lambda errors
- HTTP API `4xx` and `5xx`
- DynamoDB `ThrottledRequests` and `SystemErrors`
- S3 bucket object/storage baseline
- Account-level estimated monthly spend
