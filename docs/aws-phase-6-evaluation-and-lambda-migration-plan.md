# AWS Phase 6 Evaluation And Lambda Migration Plan

Issue: #122
Date: June 2, 2026
Region evaluated: `us-east-1`
AWS CLI profile used for live data: `personal`

## Executive Decision

Do not proceed to Phase 7 from the currently accessible AWS environment.

The valid AWS CLI profile available in this workspace does not contain the expected production Phase 5 resources:

- Lambda function `go3d-read` is absent.
- DynamoDB tables `go3d-active-games` and `go3d-players` are absent.
- S3 bucket `go3d-game-archives` is absent.
- CloudWatch log groups and alarms for `go3d` are absent.
- No retained CloudWatch metrics exist for the expected Lambda, DynamoDB, S3, or API Gateway resources.

Because there is no deployed read-only stack or retained production telemetry in the accessible account, Phase 6 cannot prove cold-start latency, warm latency, API error rates, DynamoDB latency, archive behavior, or end-to-end consistency. The codebase also still has a real consistency blocker: the file-backed server remains the source of truth, while DynamoDB and S3 writes are best-effort and failures are logged and swallowed. The read Lambda reads only DynamoDB, so missing or stale dual-write data can return false `404` responses for games that still exist on the file-backed server.

Proceed only after the correct AWS profile/account is available or the Phase 5 stack is redeployed, instrumented, and observed for a full metrics window.

## Current State

The current repository implements the Phase 5 read-only Lambda surface:

- `GET /health`
- `GET /status/{gameId}`
- `GET /openGames`

The implementation in `src/main/scala/go3d/server/lambda/LambdaHandler.scala` reads `/status/{gameId}` from `DynamoDBGamesRepository.get` and reads `/openGames` from `DynamoDBPlayersRepository.openGames`. It does not fall back to the file-backed server.

The existing http4s server remains file-backed:

- `Games.add` and `Games.update` persist through `fileIO.saveGame(gameId)` before calling `DynamoDBGamesRepository.put`.
- `Tokens.register` stores tokens in an in-memory `TrieMap` and then calls `DynamoDBPlayersRepository.put`.
- `Games.archive` removes the active game from memory and disk state, deletes the DynamoDB game and player rows, then calls `S3Client.archiveGame`.

The AWS repository clients are intentionally best-effort:

- `DynamoDBGamesRepository.put/delete` retry once, then log and swallow failures.
- `DynamoDBPlayersRepository.put/deleteGame` retry once, then log and swallow failures.
- `S3Client.archiveGame` retries once, then log and swallow failures.

This is acceptable for a shadow dual-write phase, but it is not safe as a production source of truth. A DynamoDB write failure can make the Lambda read path stale. An S3 archive failure after DynamoDB deletion can lose the cloud archive copy. The current S3 presigned URL helper also signs `archives/{gameId}.json` without first checking object existence, despite its comment saying it returns `None` when the object does not exist.

## Live AWS Evidence

The default local AWS CLI profile is not usable for production evaluation: it is configured with dummy credentials and a dummy region. The temporary configured profiles are expired. The only valid STS identity available during this evaluation was `--profile personal`; all live AWS checks below used that profile.

### Resource Inventory

| Resource | Source command | Observed value | Result |
|---|---|---:|---|
| STS identity | `aws sts get-caller-identity --profile personal --region us-east-1` | Valid identity | Pass for CLI access |
| Lambda `go3d-read` | `aws lambda get-function --function-name go3d-read --profile personal --region us-east-1` | `ResourceNotFoundException` | Fail |
| Lambda functions in `us-east-1` | `aws lambda list-functions --profile personal --region us-east-1 --query Functions[].FunctionName` | `[]` | Fail |
| Lambda functions in `eu-central-1` | `aws lambda list-functions --profile personal --region eu-central-1 --query Functions[].FunctionName` | `demo_lambda`, `demo_lambda2` | Not go3d |
| DynamoDB `go3d-active-games` | `aws dynamodb describe-table --table-name go3d-active-games --profile personal --region us-east-1` | `ResourceNotFoundException` | Fail |
| DynamoDB `go3d-players` | `aws dynamodb describe-table --table-name go3d-players --profile personal --region us-east-1` | `ResourceNotFoundException` | Fail |
| DynamoDB tables in `us-east-1` | `aws dynamodb list-tables --profile personal --region us-east-1` | `[]` | Fail |
| S3 bucket `go3d-game-archives` | `aws s3api head-bucket --bucket go3d-game-archives --profile personal --region us-east-1` | `404 Not Found` | Fail |
| S3 buckets | `aws s3api list-buckets --profile personal --query Buckets[].Name` | No `go3d` bucket | Fail |
| Lambda log groups | `aws logs describe-log-groups --log-group-name-prefix /aws/lambda/go3d --profile personal --region us-east-1` | `[]` | Fail |
| CloudWatch alarms | `aws cloudwatch describe-alarms --alarm-name-prefix go3d --profile personal --region us-east-1` | `[]` | Fail |
| REST APIs | `aws apigateway get-rest-apis --profile personal --region us-east-1` | One unrelated `DemoRESTAPI` | No go3d API |
| HTTP APIs | `aws apigatewayv2 get-apis --profile personal --region us-east-1` | `[]` | Fail |

### Metrics Evaluation

Requested metrics window: May 19, 2026 through June 2, 2026. Because the expected resources and log groups do not exist in the accessible account, the 14-day CloudWatch metric queries have no resource identity to evaluate. Retained metric discovery also returned no metrics for the expected resource dimensions.

| Metric | Source command or query | Observed value | Threshold | Result |
|---|---|---:|---:|---|
| Lambda cold start p50/p95/p99/max | `aws logs describe-log-groups --log-group-name-prefix /aws/lambda/go3d ...`; expected Logs Insights query over `@initDuration` | No `/aws/lambda/go3d-read` log group | p95 <= 5s | Fail: no telemetry |
| Lambda warm duration p50/p95/p99/max | Expected Logs Insights query over `@duration` excluding records with `@initDuration` | No Lambda log group | p95 within Phase 5 target; verify before cutover | Fail: no telemetry |
| Lambda invocations/errors | `aws cloudwatch list-metrics --namespace AWS/Lambda --metric-name Invocations --dimensions Name=FunctionName,Value=go3d-read ...` | `[]` | Error rate < 1% | Fail: no metrics |
| API Gateway 4xx/5xx | `aws cloudwatch list-metrics --namespace AWS/ApiGateway ...` and API enumeration | No go3d API or metrics | 5xx < 1%, 4xx explainable | Fail: no telemetry |
| DynamoDB read/write latency | `aws cloudwatch list-metrics --namespace AWS/DynamoDB --dimensions Name=TableName,Value=go3d-active-games ...` | `[]` | p95 < 100ms preferred; no throttles | Fail: no table or metrics |
| DynamoDB throttles/system errors | Same DynamoDB metric discovery | `[]` | 0 throttles and 0 system errors during normal load | Fail: no table or metrics |
| DynamoDB request volume | `aws dynamodb list-tables` and CloudWatch metric discovery | No tables | Volume supports cost and capacity model | Fail: no data |
| S3 archive bucket | `aws s3api head-bucket --bucket go3d-game-archives ...` | `404 Not Found` | Bucket exists with lifecycle, versioning, encryption | Fail |
| S3 object count/storage classes | `aws s3api list-buckets --profile personal --query Buckets[].Name` | No `go3d-game-archives` bucket | Archive object inventory available | Fail |
| S3 retrieval/presigned URL behavior | Code review plus missing bucket | No live retrieval metric; code signs URLs without existence check | Existing archive can be retrieved and nonexistent archive does not produce misleading success | Fail |
| CloudWatch alarms | `aws cloudwatch describe-alarms --alarm-name-prefix go3d ...` | No alarms | Cost, Lambda, DynamoDB, API alarms exist | Fail |

### Cost Evaluation

Cost Explorer was queried for the last completed month and the current month-to-date. These are account-level service costs for the valid profile, not go3d-specific tags or resources.

| Service | Source command | May 2026 observed cost | June 1-2, 2026 observed cost | Phase 6 interpretation |
|---|---|---:|---:|---|
| Lambda | `aws ce get-cost-and-usage --time-period Start=2026-05-01,End=2026-06-01 --granularity MONTHLY --metrics UnblendedCost --group-by Type=DIMENSION,Key=SERVICE ...` | Service absent from grouped results, interpreted as $0.00 | Service absent, interpreted as $0.00 | No deployed go3d Lambda cost to evaluate |
| DynamoDB | Same Cost Explorer query | Service absent, interpreted as $0.00 | Service absent, interpreted as $0.00 | No deployed go3d DynamoDB cost to evaluate |
| S3 | Same Cost Explorer query | $0.3969 account-level S3 cost | $0.0132 account-level S3 cost | Not attributable to go3d; no go3d bucket exists |
| API Gateway | Same Cost Explorer query | Service absent, interpreted as $0.00 | Service absent, interpreted as $0.00 | No go3d API cost to evaluate |
| CloudWatch | Same Cost Explorer query | $0.00 | $0.00 | No go3d logs/alarms cost |
| Relevant go3d total | Resource inventory plus Cost Explorer | $0.00 observed because resources are absent | $0.00 observed because resources are absent | Cost is not a pass signal |

The original `AWS.md` estimate of $2-5/month for the full migration remains plausible at low traffic, but it was not validated by production Phase 5 telemetry in this account.

## Consistency Risk Assessment

| Risk | Current evidence | Migration impact | Required fix before Phase 7 |
|---|---|---|---|
| False Lambda `404` | Lambda `/status/{gameId}` reads only DynamoDB; server source of truth is file-backed | Existing games can exist on disk but be missing from Lambda reads | Add consistency verification and repair tooling before promoting Lambda reads |
| Best-effort DynamoDB writes | Repository failures are retried once, logged, and swallowed | Data can diverge silently from files | Make DynamoDB writes authoritative only after migration; before then, alert on failures and reconcile |
| Token mismatch | Lambda write endpoints are not implemented; current token validation is in-memory | A write Lambda cannot validate existing in-memory tokens after cold start or on another host | Store and validate token hashes in DynamoDB for all write endpoints |
| Active game TTL mismatch | Current `DynamoDBGamesRepository.put` sets `expiresAt` on every game write | Active games can be TTL-deleted even if the design expects `expiresAt = null` until completion | Make active games non-expiring or update TTL semantics explicitly |
| Archive ordering | `Games.archive` deletes DynamoDB rows before S3 archive upload | S3 failure can leave no cloud active record and no cloud archive | Upload and verify S3 archive before marking/deleting DynamoDB active state |
| Presigned URL existence | `S3Client.generatePresignedUrl` does not `HeadObject` first | Nonexistent archives can produce signed URLs that fail later | Check object existence before signing |

## Full Lambda Migration Plan

### Phase 7 Scope

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
  - Do not set active-game TTL unless the intended policy is explicit. If TTL is used for stale active games, document and monitor it.
  - On game completion, set `expiresAt = completedAt + 7 days` and `archivedS3Key`.

- `go3d-players`
  - Partition key: `gameId`
  - Sort key: `color`
  - Store `authTokenHash`, `createdAt`, `expiresAt`.
  - Use conditional writes to prevent duplicate registration.
  - Validate bearer tokens by hashing the provided token and reading the player record from DynamoDB.

Use conditional writes or transactions for write endpoints:

- `/new/{size}`: create a new game item with `attribute_not_exists(gameId)`.
- `/register/{gameId}/{color}`: verify game exists and is not over, then conditionally create the player row.
- `/set/{gameId}/{x}/{y}/{z}`: validate token hash, color, game readiness, turn, bounds, suicide/capture rules, and version; update game with optimistic locking.
- `/pass/{gameId}`: validate token hash, color, turn, and version; update game with optimistic locking.

Return explicit client errors for duplicate registration, invalid token, wrong turn, outside-board move, suicide, nonexistent game, and game-over writes. Return retryable errors for conditional write conflicts where the client should refetch status.

### Archive Flow

On game completion:

1. Serialize the completed game.
2. Write the archive to `s3://go3d-game-archives/archives/{gameId}.json` or the dated key convention selected for production.
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

1. Redeploy or locate the Phase 5 read-only AWS stack in the correct account.
2. Enable dashboards, alarms, and log retention.
3. Observe at least 14 days of read-only traffic, or explicitly document why the available window is shorter.
4. Run consistency comparison between file-backed `/status` and Lambda `/status` for a representative sample of active games.
5. Implement and deploy write Lambda routes behind non-production API Gateway stages.
6. Run migration dry-run and fix all decode or verification failures.
7. Run real migration and verify every migrated record.
8. Route a controlled percentage of write traffic to Lambda, if routing supports it; otherwise schedule a maintenance cutover.
9. During the grace period, preserve rollback by either:
   - mirroring Lambda writes into file saves, or
   - running a verified DynamoDB-to-file export before routing traffic back to the old server.
10. After the grace period, remove file-backed production writes and keep archival exports/backups.

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

Add or revise monitoring before Phase 7:

| Area | Required controls |
|---|---|
| Lambda latency | Logs Insights queries for `@initDuration` and warm `@duration`; dashboard p50/p95/p99/max; alarm when cold-start p95 exceeds 5s |
| Lambda errors | Alarm on errors and error percentage for `go3d-read` and `go3d-write`; dashboard by route |
| API Gateway | Dashboard and alarms for `4XXError`, `5XXError`, latency, integration latency, and throttles |
| DynamoDB | Alarms for throttles, system errors, consumed read/write volume, conditional check failures, and p95 successful request latency by operation |
| S3 archive | Alarm or canary for archive write failures, missing archive verification, and failed archive retrieval |
| Logs | 7-day minimum retention during early migration; longer retention during cutover if investigation needs it |
| Cost | Monthly spend alarm for total go3d spend, not only `ServiceName=AWSLambda`; tag resources and use Cost Explorer tag filters |
| Consistency | Scheduled reconciliation job comparing file saves, DynamoDB active games, player rows, and S3 archives until file storage is retired |

The existing `infra/setup-cloudwatch.sh` creates useful initial alarms, but it should be expanded before cutover. In particular, the cost alarm currently filters billing metrics to `ServiceName=AWSLambda`, which does not cover DynamoDB, S3, API Gateway, or CloudWatch spend.

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

Phase 6 can be accepted only after one of these is true:

- The correct production AWS account/profile is provided and the report is refreshed with real `go3d` metrics from a deployed Phase 5 stack.
- The Phase 5 stack is redeployed in `us-east-1`, monitored for the agreed window, and this report is updated with actual latency, error, consistency, archive, and cost observations.

Until then, Phase 7 should not start. The implementation work can be prepared locally, but production cutover must wait for deployed-stack telemetry and consistency verification.
