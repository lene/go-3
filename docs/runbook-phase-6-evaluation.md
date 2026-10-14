# Runbook: Evaluate the Lambda migration (#123, Phase 6)

Goal of #123: analyse real metrics and decide whether to proceed with the full migration
(Phase 7, #124) or optimise first. This runbook turns the issue's task list into steps you can
run, with the thresholds and the decision at the end. Background and the exit gates are in
[aws-phase-6-evaluation-and-lambda-migration-plan.md](aws-phase-6-evaluation-and-lambda-migration-plan.md).

All commands use account `192477613230`, region `eu-central-1` and an AWS profile with read
access to CloudWatch, Cost Explorer, DynamoDB and S3 (for example `--profile lene`).

## 0. What can be measured where

| Question | Stack | Why |
|---|---|---|
| Cold/warm latency, errors, 4xx/5xx, cost | **production** (read-only) | the stack #128 deploys; `canary.yml` gives it steady traffic |
| DynamoDB consistency, write races, S3 archive retrieval | **staging** (write routes on) | production has no write routes before the cutover (#125) |

The canary only calls `/health`, `/openGames` and `/status/CANARY`. Its traffic is synthetic, so
it says nothing about real load; read the results with that in mind (section 7).

## 1. Prerequisites (start the clock)

The 2-week window starts when production is deployed and the canary is running.

1. Deploy production (this is #128): run the **Terraform** workflow on `master` with
   `environment = production`, read the plan in the plan job's summary, approve the apply.
2. Check it: `GET <api_base_url>/health` returns 200 `1`, `/openGames` returns 200 JSON,
   `/status/missing-game-id` returns 404 JSON.
3. Set the repository variable `GO3D_API_URL` to the production `api_base_url`. Without it the
   canary job is skipped. Run the **Canary** workflow once by hand and check its job summary.
4. In the AWS Billing console, activate the cost allocation tags `Project`, `Environment` and
   `Phase`. They only start to show in Cost Explorer after activation, with up to 24 hours delay,
   and are not back-filled, so do this on day 1.
5. Write down the start date and the `api_base_url` in the results section (section 9).

Scheduled workflows are disabled after 60 days without repository activity; two weeks is safe.

## 2. Cold start latency (target < 3 s, escalate above 5 s)

The user-visible cold start is `@initDuration + @duration` (plus API Gateway). In the console,
open CloudWatch, Logs Insights, log group `/aws/lambda/go3d-read`, time range 14 days, or use the
saved query `go3d/read/lambda-baseline`. Cold starts:

```
filter @type = "REPORT" and ispresent(@initDuration)
| stats count() as coldStarts,
        pct(@initDuration, 50) as init_p50, pct(@initDuration, 95) as init_p95,
        pct(@initDuration, 99) as init_p99, max(@initDuration) as init_max,
        pct(@initDuration + @duration, 50) as total_p50,
        pct(@initDuration + @duration, 95) as total_p95,
        pct(@initDuration + @duration, 99) as total_p99
```

Record `total_p50/p95/p99` and compare with the table in section 8. The Lambda is `java21`, 512 MB.
If the numbers are bad, memory is the first thing to try: more memory also means more CPU during
the JVM start, and the cost change is often small.

## 3. Warm latency

At a 15 minute canary interval most invocations are cold, so warm samples are few. Generate a
burst first, within the API throttle (rate 10/s, burst 20), for example 200 requests of
`/openGames` at about 5 per second:

```bash
API=<production api_base_url>
seq 200 | xargs -P 1 -I{} sh -c "curl -s -o /dev/null -w '%{time_total}\n' $API/openGames; sleep 0.2" > warm.txt
sort -n warm.txt | awk '{a[NR]=$1} END {print "p50", a[int(NR*0.5)], "p95", a[int(NR*0.95)], "max", a[NR]}'
```

Then, in Logs Insights:

```
filter @type = "REPORT" and !ispresent(@initDuration)
| stats count() as warm, pct(@duration, 50) as p50, pct(@duration, 95) as p95,
        pct(@duration, 99) as p99, max(@duration) as max
```

The report's gate is "warm p95 meets the user-facing latency target". The client polls every
500 ms, so a warm p95 well below that is the practical target; decide the number and write it
down before you look at the result.

## 4. Errors and API Gateway

Use the AWS/Lambda metrics (`Errors`, `Invocations`, `Throttles`, dimension
`FunctionName = go3d-read`) and the AWS/ApiGateway metrics (`Count`, `4xx`, `5xx`, `Latency`,
`IntegrationLatency`, dimension `ApiId`; find it with
`aws apigatewayv2 get-apis --query "Items[?Name=='go3d-read'].ApiId"`). Daily sums over 14 days:

```bash
aws cloudwatch get-metric-statistics --namespace AWS/Lambda --metric-name Errors \
  --dimensions Name=FunctionName,Value=go3d-read --statistics Sum --period 86400 \
  --start-time "$(date -u -d '14 days ago' +%FT%TZ)" --end-time "$(date -u +%FT%TZ)"
```

Error rate = `Errors / Invocations`. The `/status/CANARY` call is a deliberate 404: it counts as
a 4xx, so subtract about 96 per day when you read the 4xx numbers. Also check that the alarms
(`go3d-read-errors`, `go3d-read-api-4xx`, `go3d-read-api-5xx`, `go3d-dynamodb-*`) did not fire:
`aws cloudwatch describe-alarm-history --history-item-type StateUpdate`.

## 5. DynamoDB

Tables `go3d-active-games` and `go3d-players`. From the CloudWatch DynamoDB metrics:

- `SuccessfulRequestLatency` by `Operation` (`GetItem`, `Scan`) and `TableName`, statistic
  `p95` (preferred < 100 ms).
- `ThrottledRequests` and `SystemErrors`: expected 0.

**Consistency (needs staging, because only staging writes).** The report lists these risks:
best-effort DynamoDB writes that are retried once and then swallowed, no token validation across
cold starts, and archive ordering. Test them on staging (`GO3D_STAGING_API_URL`):

1. Play a game with the **Staging gameplay** workflow and confirm `/status/<id>` agrees with the
   moves sent.
2. Race test: create a game, register both colours, then send the same `/set` for the same
   position several times at once with the right token. Expected: exactly one move is accepted,
   the others are rejected, and the board in `/status` shows a single stone.

   ```bash
   seq 10 | xargs -P 10 -I{} curl -s -o /dev/null -w '%{http_code}\n' \
     -H "Authentication: Bearer $BLACK" "$API/set/$ID/2/2/2" | sort | uniq -c
   ```

   I have not run this; the acceptance wording is yours to set. Any state that differs from the
   outcome above is a "fix before proceeding" finding.
3. Search the staging Lambda logs for swallowed write errors (log group `/aws/lambda/go3d-staging`):

   ```
   fields @timestamp, @message | filter @message like /ERROR|Exception|retry/ | sort @timestamp desc | limit 50
   ```

## 6. S3 archive retrieval

On staging, a finished game is archived to the bucket
`go3d-game-archives-192477613230-eu-central-1-staging` under `archives/`.

```bash
aws s3 ls s3://go3d-game-archives-192477613230-eu-central-1-staging/archives/
for i in $(seq 20); do curl -s -o /dev/null -w '%{http_code} %{time_total}\n' "$API/archived/<gameId>"; done
```

Expected: HTTP 200 and a time_total in the same range as a warm API call; the first call is a
cold start. Note that S3 request metrics are not enabled by default, so measure through the API.
Also compare the archive object with the game's `/status` (same moves, same result).

## 7. Cost

Cost Explorer, filtered by the tag `Project = go3d`, grouped by service, over the 14 days
(needs the tag activation from step 1.4). The Cost Explorer API costs $0.01 per request:

```bash
aws ce get-cost-and-usage --region us-east-1 --granularity DAILY --metrics UnblendedCost \
  --time-period Start=<start-date>,End=<end-date> \
  --group-by Type=DIMENSION,Key=SERVICE \
  --filter '{"Tags":{"Key":"Project","Values":["go3d"]}}'
```

Take the monthly projection as (14-day total / 14) x 30. Two caveats:

- Staging has the same tags except `Environment`; filter on `Environment = prod` to leave it out.
- The canary is about 4 x 3 calls an hour. Real traffic will be higher, so scale the request-based
  parts (Lambda, API Gateway, CloudWatch ingestion) with the traffic model in the report
  (about 10 games a day, 10,000 requests a month) and say what you assumed.

**Decide which estimate you compare with.** The issue states $0.03 a month, which only covers
Lambda-style usage; the Phase 6 report estimates $2-$7 a month in total with a $10 optimisation
threshold. This runbook uses the report's range, but ">2x the estimate" is your call: write the
baseline you chose in section 9.

## 8. Thresholds and decisions

| Metric | Accept | Act |
|---|---|---|
| Cold start total p95 | < 3 s | 3-5 s: try more memory; > 5 s: explore GraalVM native image |
| Warm p95 | agreed target (section 3) | investigate before write migration |
| Lambda errors / API 5xx | < 1 % | find the cause first |
| DynamoDB p95 latency | < 100 ms | check key design and item size |
| Throttles and SystemErrors | 0 | fix before proceeding |
| Cost | within the chosen baseline | > 2x: provisioned concurrency or stay hybrid |
| Consistency (staging race test) | one winner, state agrees | fix before proceeding |
| S3 retrieval | works, comparable to a warm call | investigate |

Decision (from the issue):

- cold starts > 5 s: explore GraalVM native image,
- costs > 2x the estimate: consider provisioned concurrency or stay hybrid,
- consistency issues: fix them before Phase 7,
- everything acceptable: proceed to Phase 7 (#124).

## 9. Results (fill in)

| Item | Value |
|---|---|
| Window | `<start>` to `<end>` |
| Production `api_base_url` | |
| Cold start total p50 / p95 / p99 / max | |
| Warm p50 / p95 / p99 / max | |
| Lambda error rate, API 5xx rate | |
| DynamoDB p95 (GetItem, Scan), throttles | |
| Staging race test | |
| S3 retrieval | |
| Monthly cost projection and baseline used | |
| Decision | |

Post the filled table as a comment on #123 (or update the Phase 6 report), tick the task list in
the issue, and close #123 when the decision is Phase 7.

## 10. Afterwards

If staging is no longer needed, tear it down with the **Terraform destroy** workflow (see the
"Teardown" section of `infra/terraform/go3d-readonly/README.md`) and unset
`GO3D_STAGING_API_URL`. Production stays up for the cutover (#125).
