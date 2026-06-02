# AWS Phase 6 Evaluation And Lambda Migration Plan

Issue: #122
Date: June 2, 2026
Default region: `eu-central-1`
AWS CLI profile used for live data: `personal`

## Executive Decision

Proceed with AWS resource creation and read-only Lambda deployment in `eu-central-1`; do not treat absent `go3d` resources as a failure.

The live AWS checks show that the expected `go3d` Lambda, DynamoDB, S3, API Gateway, CloudWatch log groups, and CloudWatch alarms have not been created yet in the accessible account. That is a pre-creation baseline, not a reason to stop the migration. The immediate next step is to create the Phase 2/5 infrastructure, deploy the read-only Lambda, and then collect the latency, error, consistency, and cost metrics required before production write cutover.

The production write migration remains gated. Do not route production write traffic to Lambda until:

- the read-only Lambda stack exists in `eu-central-1`;
- 14 days of metrics are collected, or a shorter window is explicitly accepted;
- the file-backed server and DynamoDB read path are reconciled for active games;
- DynamoDB-backed token validation is implemented for Lambda writes;
- migration tooling verifies all existing save files before cutover.

## Current State

The repository implements the Phase 5 read-only Lambda surface:

- `GET /health`
- `GET /status/{gameId}`
- `GET /openGames`

The implementation in `src/main/scala/go3d/server/lambda/LambdaHandler.scala` reads `/status/{gameId}` from `DynamoDBGamesRepository.get` and reads `/openGames` from `DynamoDBPlayersRepository.openGames`. It does not fall back to the file-backed server.

The current http4s server remains file-backed:

- `Games.add` and `Games.update` persist with `fileIO.saveGame(gameId)` before calling `DynamoDBGamesRepository.put`.
- `Tokens.register` stores tokens in an in-memory `TrieMap` and then calls `DynamoDBPlayersRepository.put`.
- `Games.archive` removes active game state, deletes DynamoDB rows, and then calls `S3Client.archiveGame`.

The AWS repository clients are best-effort:

- `DynamoDBGamesRepository.put/delete` retry once, then log and swallow failures.
- `DynamoDBPlayersRepository.put/deleteGame` retry once, then log and swallow failures.
- `S3Client.archiveGame` retries once, then logs and swallows failures.

This is appropriate for shadow writes only. It is not safe as a production source of truth because DynamoDB or S3 drift can be silent. The read Lambda also reads only DynamoDB, so a missed dual-write can produce a false `404` even when the file-backed server still has the game.

One region issue must be corrected before resource creation: several infra scripts default `AWS_REGION` to `us-east-1`. For this migration, run them with `AWS_REGION=eu-central-1` or update the script defaults before execution.

## Live AWS Baseline

The default local AWS CLI profile is configured with dummy credentials and a dummy region. The temporary configured profiles were expired. The valid STS identity available during this evaluation was `--profile personal`; all live AWS checks below used that profile.

### Resource Inventory

| Resource | Source command | Observed value in `eu-central-1` | Interpretation |
|---|---|---:|---|
| STS identity | `aws sts get-caller-identity --profile personal --region eu-central-1` | Valid identity | CLI access available |
| Lambda `go3d-read` | `aws lambda get-function --function-name go3d-read --profile personal --region eu-central-1` | `ResourceNotFoundException` | Create in Phase 2/5 |
| Lambda functions | `aws lambda list-functions --profile personal --region eu-central-1 --query Functions[].FunctionName` | `demo_lambda`, `demo_lambda2` | No go3d Lambda yet |
| DynamoDB `go3d-active-games` | `aws dynamodb describe-table --table-name go3d-active-games --profile personal --region eu-central-1` | `ResourceNotFoundException` | Create table |
| DynamoDB `go3d-players` | `aws dynamodb describe-table --table-name go3d-players --profile personal --region eu-central-1` | `ResourceNotFoundException` | Create table |
| DynamoDB tables | `aws dynamodb list-tables --profile personal --region eu-central-1` | `[]` | No DynamoDB baseline spend |
| S3 bucket `go3d-game-archives` | `aws s3api head-bucket --bucket go3d-game-archives --profile personal --region eu-central-1` | `404 Not Found` | Create globally unique bucket or adjusted name |
| Lambda log groups | `aws logs describe-log-groups --log-group-name-prefix /aws/lambda/go3d --profile personal --region eu-central-1` | `[]` | Expected before deployment |
| CloudWatch alarms | `aws cloudwatch describe-alarms --alarm-name-prefix go3d --profile personal --region eu-central-1` | `[]` | Create alarms with stack |
| REST APIs | `aws apigateway get-rest-apis --profile personal --region eu-central-1` | `[]` | Create API Gateway route |
| HTTP APIs | `aws apigatewayv2 get-apis --profile personal --region eu-central-1` | `[]` | Consider HTTP API for lower cost if feature set is enough |

### Pre-Deployment Metrics Evaluation

Requested evaluation window: May 19, 2026 through June 2, 2026.

Because the `go3d` resources are not created yet, there are no application-specific latency or error metrics to evaluate. The absence of metrics is expected at this stage. The table below records the metric gates that must be populated after deployment.

| Metric | Source command or query after deployment | Current value | Threshold | Gate |
|---|---|---:|---:|---|
| Lambda cold start p50/p95/p99/max | Logs Insights over `/aws/lambda/go3d-read`, `@initDuration` from `REPORT` records | No log group yet | p95 <= 5s | Required before write cutover |
| Lambda warm duration p50/p95/p99/max | Logs Insights over `@duration` excluding cold-start records | No log group yet | p95 meets user-facing latency target | Required before write cutover |
| Lambda errors | CloudWatch `AWS/Lambda` `Errors` and route logs | No function yet | < 1% | Required before write cutover |
| API Gateway 4xx/5xx | CloudWatch `AWS/ApiGateway` `4XXError` and `5XXError` | No API yet | 5xx < 1%; 4xx explainable | Required before write cutover |
| DynamoDB latency | CloudWatch DynamoDB successful request latency by operation/table | No tables yet | p95 < 100ms preferred | Required before write cutover |
| DynamoDB throttles/system errors | CloudWatch `ThrottledRequests` and `SystemErrors` | No tables yet | 0 during normal load | Required before write cutover |
| S3 archive behavior | S3 `PutObject`, `HeadObject`, `GetObject` validation and access logs if enabled | No bucket yet | Archive write and retrieval verified | Required before archive cutover |
| Monthly cost | Cost Explorer filtered by tags/resources | No go3d resources yet | Below $10/month during low-traffic phase | Required after deployment |

## Resource Creation Effort And Cost

Pricing references checked on June 2, 2026:

- [AWS Lambda pricing](https://aws.amazon.com/lambda/pricing/)
- [Amazon DynamoDB pricing](https://aws.amazon.com/dynamodb/pricing/)
- [Amazon S3 pricing](https://aws.amazon.com/s3/pricing/)
- [Amazon API Gateway pricing](https://aws.amazon.com/api-gateway/pricing/)
- [Amazon CloudWatch pricing](https://aws.amazon.com/cloudwatch/pricing/)

The key cost point is that resource creation itself is inexpensive for this stack. Lambda, API Gateway, DynamoDB on-demand tables, and S3 buckets are primarily usage-priced. The first fixed monthly cost is CloudWatch alarms; the current five-alarm baseline in `infra/setup-cloudwatch.sh` is about $0.50/month if implemented as standard metric alarms at the current public example rate of $0.10 per alarm metric-month. Logs, API calls, Lambda duration, DynamoDB requests, and S3 storage then scale with traffic and retained data.

| Resource | Creation work | Direct idle cost | Usage cost driver | Notes |
|---|---|---:|---|---|
| IAM roles and policies | Run `infra/setup-iam.sh` with `AWS_REGION=eu-central-1`; verify least privilege | $0 | None | No direct IAM charge |
| DynamoDB tables | Run or update `infra/setup-dynamodb.sh`; create `go3d-active-games` and `go3d-players` with TTL | Near $0 before data/requests | On-demand reads/writes and storage | Use on-demand for unknown traffic |
| S3 archive bucket | Run or update `infra/setup-s3.sh`; create bucket in `eu-central-1`, encryption, versioning, lifecycle | Near $0 before objects | Storage, PUT/GET/LIST, lifecycle transitions, data transfer | Bucket name is global; choose a suffix if `go3d-game-archives` is unavailable |
| Lambda read function | Build assembly jar and create `go3d-read` | $0 without invocations/provisioned concurrency | Requests and duration | Java cold starts must be measured after deploy |
| API Gateway | Create read routes for `/health`, `/status/{gameId}`, `/openGames` | $0 without calls | API calls and data transfer | Prefer HTTP API unless REST API features are required |
| CloudWatch log groups | Created by Lambda or pre-created with retention | $0 before log ingestion | Log ingestion and retained storage | Keep 7-day retention during early phase |
| CloudWatch alarms | Run or update `infra/setup-cloudwatch.sh` | About $0.50/month for five standard alarms | Alarm count and alarm type | Add API Gateway and total cost alarms before cutover |

### Expected Low-Traffic Monthly Cost

This estimate assumes the original `AWS.md` low-traffic model: about 10 games/day, 10,000 API requests/month, 512 MB Lambda memory, 2s average duration, small DynamoDB items, and small JSON archives. Use AWS Pricing Calculator before production if traffic assumptions change.

| Service | Expected monthly cost after creation | Confidence | Notes |
|---|---:|---|---|
| Lambda | $0.20-$2.00 | Medium | Depends heavily on Java duration and cold-start/init billing |
| DynamoDB | <$0.10 | High | On-demand request volume and storage are tiny at this scale |
| S3 | <$0.10 | High | Archive JSON storage/request volume is tiny; lifecycle transitions may dominate object storage |
| API Gateway | $1.00-$3.00 | Medium | REST API costs more than HTTP API; verify selected API type in `eu-central-1` |
| CloudWatch | $0.50-$2.00 | Medium | Five alarms plus logs; more dashboards/alarms increase cost |
| Total | About $2-$7/month | Medium | Still under the $10/month optimization threshold at low traffic |

The live account-level Cost Explorer data for May 2026 showed no Lambda, DynamoDB, API Gateway, or CloudWatch application charges attributable to `go3d`, because the resources are absent. Account-level S3 spend was $0.3969, but there is no `go3d-game-archives` bucket, so that spend is unrelated.

## Consistency Risk Assessment

| Risk | Current evidence | Migration impact | Required fix before production writes |
|---|---|---|---|
| False Lambda `404` | Lambda `/status/{gameId}` reads only DynamoDB; current server source of truth is file-backed | Existing games can exist on disk but be missing from Lambda reads | Add reconciliation and repair tooling before promoting Lambda reads |
| Best-effort DynamoDB writes | Repository failures are retried once, logged, and swallowed | DynamoDB can diverge silently from files | Alert on write failures during shadow phase; make DynamoDB authoritative only after verified migration |
| Token mismatch | Current token validation is in-memory; Lambda write endpoints do not exist | Write Lambda cannot validate tokens after cold start or across hosts | Validate bearer tokens by hashing and reading `go3d-players` from DynamoDB |
| Active game TTL mismatch | Current `DynamoDBGamesRepository.put` sets `expiresAt` on every game write | Active games may expire even if design expects TTL only after completion | Decide and implement explicit active-game TTL semantics |
| Archive ordering | `Games.archive` deletes DynamoDB rows before S3 archive verification | Cloud archive can be missing after game completion | Upload and verify S3 archive before marking/deleting DynamoDB active state |
| Presigned URL existence | `S3Client.generatePresignedUrl` signs the key without `HeadObject` | Missing archives can produce signed URLs that fail later | Check object existence before signing |

## Full Lambda Migration Plan

### Infrastructure And Read-Only Deployment

1. Set `AWS_REGION=eu-central-1` for all infra commands, or change script defaults from `us-east-1` to `eu-central-1`.
2. Create IAM roles and policies with `infra/setup-iam.sh`.
3. Create DynamoDB tables with `infra/setup-dynamodb.sh`.
4. Create the S3 archive bucket with `infra/setup-s3.sh`; if `go3d-game-archives` is unavailable globally, select and document a unique production name and set `S3_ARCHIVE_BUCKET`.
5. Build the Lambda assembly jar.
6. Deploy `go3d-read`.
7. Create API Gateway routes for `/health`, `/status/{gameId}`, and `/openGames`.
8. Create CloudWatch alarms and set log retention.
9. Tag resources with at least `Project=go3d`, `Environment=prod`, and `Phase=lambda-migration` so Cost Explorer can separate go3d spend from account-level spend.

### Phase 7 Write Endpoints

Implement Lambda write routes for:

- `GET /new/{size}`
- `GET /register/{gameId}/{color}`
- `GET /set/{gameId}/{x}/{y}/{z}`
- `GET /pass/{gameId}`

Keep the existing server and file saves intact as rollback assets until the production grace period is complete. DynamoDB becomes the source of truth only after migration verification passes and the cutover checklist is complete.

### Data Model And Write Semantics

Use DynamoDB as the authoritative write target for Lambda:

- `go3d-active-games`
  - Partition key: `gameId`
  - Store serialized `SaveGame` or normalized game state.
  - Store `version` for optimistic locking.
  - Store `lastModified`.
  - Do not set active-game TTL unless the intended policy is explicit.
  - On game completion, set `expiresAt = completedAt + 7 days` and `archivedS3Key`.

- `go3d-players`
  - Partition key: `gameId`
  - Sort key: `color`
  - Store `authTokenHash`, `createdAt`, `expiresAt`.
  - Use conditional writes to prevent duplicate registration.
  - Validate bearer tokens by hashing the provided token and reading the player record from DynamoDB.

Use conditional writes or transactions for write endpoints:

- `/new/{size}` creates a new game item with `attribute_not_exists(gameId)`.
- `/register/{gameId}/{color}` verifies the game exists and is not over, then conditionally creates the player row.
- `/set/{gameId}/{x}/{y}/{z}` validates token hash, color, game readiness, turn, bounds, suicide/capture rules, and version; then updates with optimistic locking.
- `/pass/{gameId}` validates token hash, color, turn, and version; then updates with optimistic locking.

Return explicit client errors for duplicate registration, invalid token, wrong turn, outside-board move, suicide, nonexistent game, and game-over writes. Return retryable errors for conditional write conflicts where the client should refetch status.

### Archive Flow

On game completion:

1. Serialize the completed game.
2. Write the archive to S3.
3. Verify the archive with `HeadObject` and checksum or ETag where appropriate.
4. Update the DynamoDB game item with `archivedS3Key`, `completedAt`, and `expiresAt = completedAt + 7 days`.
5. Retain the DynamoDB record during the grace period so `/status` and archive links remain available.
6. Delete or expire player token records only after game-over behavior is verified.

Do not delete the DynamoDB active game before the S3 archive write is verified.

### Migration Tooling

Add a migration tool for existing `saves/*.json`:

- Dry run:
  - Read all save files.
  - Report game count, player count, active/archived count, decode failures, duplicate IDs, invalid players, and planned DynamoDB/S3 writes.
  - Compute a checksum for every source file and serialized game payload.

- Real migration:
  - Back up original save files to S3 under `backups/file-saves/{timestamp}/`.
  - Write active games to `go3d-active-games`.
  - Write player/token rows to `go3d-players` where valid token data exists.
  - Write completed archives to S3.
  - Verify every migrated game by reading back from DynamoDB and comparing canonical JSON/checksum.
  - Verify S3 archives with `HeadObject`.
  - Emit a machine-readable migration report.

- Repair mode:
  - Compare file saves against DynamoDB.
  - Identify missing, stale, or malformed DynamoDB rows.
  - Re-write only failed or drifted records.

### Cutover Plan

1. Deploy the read-only stack in `eu-central-1`.
2. Observe at least 14 days of read-only traffic, or explicitly document why the available window is shorter.
3. Compare file-backed `/status` and Lambda `/status` for a representative sample of active games.
4. Implement and deploy write Lambda routes behind a non-production API Gateway stage.
5. Run migration dry-run and fix all decode or verification failures.
6. Run real migration and verify every migrated record.
7. Route a controlled percentage of write traffic to Lambda if routing supports it; otherwise schedule a maintenance cutover.
8. During the grace period, preserve rollback by mirroring Lambda writes into file saves or running a verified DynamoDB-to-file export before routing traffic back to the old server.
9. After the grace period, remove file-backed production writes and keep archival exports/backups.

### Rollback Plan

Rollback triggers:

- Lambda write error rate exceeds 1%.
- API Gateway 5xx exceeds 1%.
- DynamoDB throttles or system errors appear during normal load.
- Migration verification detects missing or mismatched games.
- Any data loss, write inconsistency, or auth-token mismatch is detected.

Rollback steps:

1. Disable or remove write Lambda routes from the production API stage.
2. Route traffic back to the existing server/API.
3. If Lambda accepted production writes, export affected DynamoDB games back to file saves and verify checksums before relying on the old server.
4. Keep DynamoDB and S3 data intact for investigation.
5. Preserve CloudWatch logs and migration reports.
6. Fix the root cause in staging before attempting another cutover.

## Operational Plan

Add or revise monitoring before production writes:

| Area | Required controls |
|---|---|
| Lambda latency | Logs Insights queries for `@initDuration` and warm `@duration`; dashboard p50/p95/p99/max; alarm when cold-start p95 exceeds 5s |
| Lambda errors | Alarm on errors and error percentage for `go3d-read` and future `go3d-write`; dashboard by route |
| API Gateway | Dashboard and alarms for `4XXError`, `5XXError`, latency, integration latency, and throttles |
| DynamoDB | Alarms for throttles, system errors, consumed read/write volume, conditional check failures, and p95 successful request latency by operation |
| S3 archive | Alarm or canary for archive write failures, missing archive verification, and failed archive retrieval |
| Logs | 7-day minimum retention during early migration; longer retention during cutover if investigation needs it |
| Cost | Monthly spend alarm for total go3d spend, not only `ServiceName=AWSLambda`; tag resources and use Cost Explorer tag filters |
| Consistency | Scheduled reconciliation job comparing file saves, DynamoDB active games, player rows, and S3 archives until file storage is retired |

The existing `infra/setup-cloudwatch.sh` creates useful initial alarms, but it should be expanded before cutover. Its current billing alarm filters to `ServiceName=AWSLambda`, which misses DynamoDB, S3, API Gateway, and CloudWatch spend.

## Test And Validation Plan

Local validation:

- `sbt Test/compile`
- `sbt test`
- `sbt assembly`

AWS read-only validation:

- Call `/health`, `/status/{knownGameId}`, and `/openGames` through API Gateway.
- Compare Lambda `/status` output against the file-backed server for a sample of active games.
- Confirm Lambda `404` responses are true misses by checking file saves and DynamoDB.

Migration validation:

- Dry-run migration reads all local save files and reports game count, player count, decode failures, duplicate IDs, and planned writes.
- Real migration writes to DynamoDB, backs up originals to S3, then verifies every migrated game by round-tripping from DynamoDB.
- Archive verification checks S3 object existence and payload checksums.

Write endpoint validation:

- Create a game.
- Register both players.
- Make valid moves.
- Pass twice.
- Verify game-over behavior and archive creation.
- Test duplicate registration, invalid token, wrong turn, outside-board move, suicide, nonexistent game, and writes after game-over.
- Test concurrent writes to the same game and verify optimistic locking behavior.

Cutover validation:

- Run the load target from `AWS.md`: 100 concurrent games and 1000 status requests per minute.
- Verify warm p95 latency target is met.
- Verify no DynamoDB throttles, no data corruption, and expected `429` behavior under throttling.
- Verify rollback export can materialize DynamoDB state back into file saves before production traffic depends on it.

## Phase 6 Exit Criteria

Phase 6 can approve the next implementation step when this report is committed and the team accepts that the current state is pre-resource-creation.

The next implementation step is not production write cutover. It is creation of the `eu-central-1` read-only stack, followed by metric collection. Production write cutover remains blocked until the deployed stack passes the latency, error, cost, consistency, migration, and rollback gates above.
