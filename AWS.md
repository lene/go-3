# AWS Lambda Migration Strategy

## Executive Summary

This document outlines the comprehensive strategy for migrating the Go-3D server to AWS Lambda with a hybrid DynamoDB + S3 storage architecture. The migration is designed as a phased approach to minimize risk while maximizing cost savings and security improvements.

**Key Benefits**:
- **Cost Reduction**: $2-5/month total (vs current hosting costs)
- **Security**: Token expiration, rate limiting, encryption at rest/transit, immutable archives
- **Scalability**: Auto-scaling Lambda functions, managed database
- **Risk Mitigation**: Gradual phased approach, each phase independently valuable

## Table of Contents

1. [Background Analysis](#background-analysis)
2. [Storage Architecture](#storage-architecture)
3. [Cost Analysis](#cost-analysis)
4. [Security Analysis](#security-analysis)
5. [Implementation Phases](#implementation-phases)
6. [Technology Stack](#technology-stack)
7. [References](#references)

---

## Background Analysis

### Current Architecture

The existing server runs on http4s with Cats Effect, using:
- **In-memory state**: `mutable.Map` for Games and Players (Games.scala, Players.scala)
- **File-based persistence**: JSON files in `saves/` directory
- **Authentication**: Bearer tokens (10-char base62, SecureRandom) with no expiration
- **No rate limiting**: Vulnerable to DoS attacks

### Critical Issues Identified

1. **Security Vulnerabilities**:
   - Token logging in plaintext (RegisterPlayer.scala:16) - **CRITICAL**
   - No token expiration (tokens valid forever)
   - No rate limiting (DoS vulnerable)
   - No optimistic locking (race conditions possible)

2. **Scalability Limitations**:
   - Stateful server (cannot horizontally scale)
   - File I/O bottleneck for high traffic
   - No automatic cleanup of archived games

3. **Operational Complexity**:
   - Manual server management
   - No automatic backups
   - Limited monitoring/alerting

### Requirements Analysis

Based on user feedback:
- **Traffic**: Unknown (prioritize flexibility)
- **Cold Starts**: Unsure (test and decide later)
- **Complexity**: Gradual hybrid approach preferred
- **Security**: Fix token logging, implement rate limiting

---

## Storage Architecture

### Hybrid DynamoDB + S3 Strategy

The optimal architecture uses **tiered storage** based on access patterns:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Game Lifecycle                            │
└─────────────────────────────────────────────────────────────────┘

  New Game
     │
     ▼
┌──────────────────┐
│   DynamoDB       │ ← Active games (frequent reads/writes)
│  (0-7 days)      │ ← Recently completed games
│  TTL enabled     │ ← Auto-delete after 7 days
└────────┬─────────┘
         │ On game completion
         ▼
┌──────────────────┐
│  S3 Standard     │ ← Archives (7-30 days)
│  (7-30 days)     │ ← Occasional access
└────────┬─────────┘
         │ Automatic transition (lifecycle policy)
         ▼
┌──────────────────┐
│ S3 Standard-IA   │ ← Archives (30-365 days)
│  (30-365 days)   │ ← Rare access
└────────┬─────────┘
         │ Automatic transition (lifecycle policy)
         ▼
┌──────────────────┐
│ S3 Glacier       │ ← Long-term archives (365+ days)
│ Deep Archive     │ ← Very rare access (compliance/history)
└──────────────────┘
```

### Data Flow

1. **New game created** → Written to DynamoDB with `expiresAt = null`
2. **Moves made** → Updated in DynamoDB (high-frequency writes)
3. **Status checks** → Read from DynamoDB (high-frequency reads, <100ms latency)
4. **Game completes** → DynamoDB updated: `expiresAt = now + 7 days`, triggers archive Lambda
5. **Archive Lambda** → Writes to S3: `s3://bucket/archives/YYYY/MM/gameId.json`
6. **After 7 days** → DynamoDB TTL auto-deletes record
7. **Access archived game** → Lambda fetches from S3, generates pre-signed URL (5-min expiry)

### DynamoDB Schema

**Table: go3d-active-games**
```
Partition Key: gameId (String)
Attributes:
  - gameState (String, JSON-serialized Game object)
  - expiresAt (Number, Unix timestamp, TTL attribute)
  - lastModified (Number, Unix timestamp for optimistic locking)
  - version (Number, increment on each update)
  - archivedS3Key (String, populated on archive: "archives/2025/01/gameId.json")

TTL Attribute: expiresAt
On-Demand Pricing: Yes
Encryption: AWS-managed keys (default)

Indexes: None (simple key-value lookup by gameId)
```

**Table: go3d-players**
```
Partition Key: gameId (String)
Sort Key: color (String, values: "@" or "O")
Attributes:
  - authTokenHash (String, SHA-256 hash of token)
  - createdAt (Number, Unix timestamp)
  - expiresAt (Number, Unix timestamp, TTL = createdAt + 24 hours)

TTL Attribute: expiresAt
On-Demand Pricing: Yes
Encryption: AWS-managed keys (default)

Access Pattern: GetItem(gameId, color), conditional writes for registration
```

### S3 Bucket Structure

```
s3://go3d-game-archives/
  ├── archives/
  │   ├── 2025/
  │   │   ├── 01/
  │   │   │   ├── gameId1.json (50KB, Standard storage class)
  │   │   │   ├── gameId2.json
  │   │   │   └── gameId3.json
  │   │   ├── 02/
  │   │   └── 03/
  │   └── 2024/
  │       ├── 12/ (Standard-IA storage class after 30 days)
  │       ├── 11/ (Glacier Deep Archive after 365 days)
  │       └── 10/
  └── .metadata/ (optional, for analytics)
```

### S3 Lifecycle Policy

```json
{
  "Rules": [
    {
      "Id": "TransitionOldGames",
      "Status": "Enabled",
      "Filter": {
        "Prefix": "archives/"
      },
      "Transitions": [
        {
          "Days": 30,
          "StorageClass": "STANDARD_IA"
        },
        {
          "Days": 365,
          "StorageClass": "DEEP_ARCHIVE"
        }
      ],
      "NoncurrentVersionTransitions": [
        {
          "NoncurrentDays": 30,
          "StorageClass": "GLACIER"
        }
      ]
    }
  ]
}
```

**Why NOT Intelligent-Tiering?**
- Monitoring fee: $0.0025 per object per month
- For 10 games/day = 3,650 objects/year = **$9.13/month** in monitoring fees alone!
- Only cost-effective at **>100,000 objects** where storage savings outweigh monitoring costs
- Standard → IA → Deep Archive lifecycle is **cheaper for this scale**

---

## Cost Analysis

### Comparison: Pure DynamoDB vs Hybrid vs S3 Intelligent-Tiering

**Scenario: 10 games/day for 1 year**

| Storage Strategy | Year 1 Cost | Breakdown |
|-----------------|-------------|-----------|
| **Pure DynamoDB** | $0.96/year | 182MB storage × $0.25/GB = $0.046/mo<br>+ $0.03/mo reads/writes |
| **Hybrid (DynamoDB + S3)** | **$0.36/year** ✅ | $0.03/mo DynamoDB + $0.002/mo S3 |
| **S3 Intelligent-Tiering** | $109.92/year ❌ | 3,650 objects × $0.0025/mo = **$9.13/mo** monitoring fee! |

**Conclusion**: Hybrid approach saves **62% vs pure DynamoDB**, while Intelligent-Tiering is **100x more expensive** at this scale.

### Full Lambda Migration Costs (10 games/day)

**Monthly Cost Breakdown**:

| Service | Usage | Cost |
|---------|-------|------|
| **Lambda** | 10,000 requests/mo, 512MB, 2s avg | $0.20-2.00 |
| **DynamoDB** | 9,000 writes, 30,000 reads, 2.5MB storage | $0.03 |
| **S3** | 300 writes, 1,000 reads, 15MB Standard + 135MB IA | $0.002 |
| **API Gateway** | 10,000 requests | $1.00-3.00 |
| **CloudWatch** | Logs + metrics | $0.50-1.00 |
| **Total** | | **$2-5/month** |

**Scalability**:
- 100 games/day: ~$10-15/month
- 1,000 games/day: ~$50-75/month
- Break-even vs dedicated server (~$100/mo): ~50-100 games/day

### Cost Optimization Strategies

1. **Use On-Demand DynamoDB**: No upfront commitment, scales with usage
2. **Lifecycle Policies**: Standard → IA → Deep Archive (no monitoring fees)
3. **Lambda Memory Tuning**: Test 256MB, 512MB, 1024MB to find sweet spot
4. **API Gateway Caching**: Cache /status responses for 10-30 seconds (reduce Lambda invocations)
5. **CloudWatch Log Retention**: 7 days for debugging, delete old logs
6. **Cost Alerts**: CloudWatch alarm when monthly spend >$10

---

## Security Analysis

### Current Vulnerabilities

1. **Token Logging (CRITICAL)**:
   - Location: `RegisterPlayer.scala:16`
   - Issue: `logger.info(s"received request with token $token")`
   - Risk: Tokens exposed in plaintext logs
   - Impact: Attacker with log access can impersonate players

2. **No Token Expiration (HIGH)**:
   - Issue: Tokens valid forever once generated
   - Risk: Stolen token永久有效
   - Impact: Compromised tokens never expire

3. **No Rate Limiting (HIGH)**:
   - Risk: DoS attacks (unlimited requests)
   - Impact: Server overload, legitimate users blocked

4. **Race Conditions (MEDIUM)**:
   - Issue: No optimistic locking on game state updates
   - Risk: Concurrent moves corrupt game state
   - Impact: Data inconsistency

### Security Improvements (Hybrid Architecture)

#### DynamoDB Security

**Encryption**:
- ✅ Encryption at rest: AWS-managed keys (KMS) enabled by default
- ✅ Encryption in transit: TLS 1.2+ enforced for all API calls
- ✅ Point-in-time recovery (PITR): 35-day backup window

**Access Control**:
- ✅ IAM policies: Least privilege (Lambda can only read/write specific tables)
- ✅ Row-level security: Conditional writes (prevent duplicate player registration)
- ✅ Attribute-level encryption: Hash tokens before storage (SHA-256)

**Audit**:
- ✅ CloudTrail: All DynamoDB API calls logged
- ✅ CloudWatch alarms: Alert on unusual activity (e.g., >1000 writes/min)

#### S3 Security

**Encryption**:
- ✅ Server-side encryption (SSE-S3): Automatic encryption of all objects
- ✅ Bucket default encryption: Enforced via bucket policy
- ✅ TLS 1.2+: Required for all data transfers

**Access Control**:
- ✅ Bucket policy: Deny public access, VPC-only access
- ✅ Pre-signed URLs: Time-limited access (5 minutes to download archived game)
- ✅ IAM roles: Lambda generates URLs, users cannot access S3 directly

**Immutability**:
- ✅ Object Lock (WORM): Archived games cannot be modified or deleted
- ✅ Versioning: Protects against accidental overwrites
- ✅ MFA Delete: Require multi-factor auth for destructive operations

**Audit**:
- ✅ S3 Access Logs: Track all object retrievals
- ✅ CloudTrail: S3 API calls logged

#### API Gateway Security

**Rate Limiting**:
- ✅ Throttling: 10 requests/sec per client IP
- ✅ Burst limit: 20 requests (temporary spike)
- ✅ 429 responses: "Too Many Requests" when exceeded

**Authentication**:
- ✅ Bearer token validation: Lambda verifies token hash against DynamoDB
- ✅ Token expiration: 24-hour TTL enforced

**DDoS Protection**:
- ✅ AWS WAF: Block malicious IPs, SQL injection attempts
- ✅ CloudWatch alarms: Alert on >1000 requests/min from single IP

### Security Checklist (Phase 1)

- [ ] Remove token logging (RegisterPlayer.scala:16)
- [ ] Hash tokens before storage (SHA-256)
- [ ] Implement token expiration (24-hour TTL)
- [ ] Add rate limiting middleware (10 req/sec per IP)
- [ ] Add optimistic locking (version field in Game state)
- [ ] Enable CloudTrail for audit logging
- [ ] Set up CloudWatch alarms for security events

---

## Implementation Phases

The migration is broken into 8 phases, each with independent value and minimal risk. Each phase has a dedicated GitLab issue for tracking.

### Phase 1: Security Hardening (Current Server)
**Issue**: [#117](https://gitlab.com/go-3/go-3/-/issues/117)
**Timeline**: 3-5 days
**Risk**: Low

**Goals**:
- Fix token logging vulnerability (CRITICAL)
- Implement rate limiting (10 req/sec per IP)
- Add token expiration (24-hour TTL)
- Add optimistic locking (version field)

**Files Modified**:
- `src/main/scala/go3d/server/http4s/RegisterPlayer.scala` (remove token logging)
- `src/main/scala/go3d/server/http4s/RateLimitMiddleware.scala` (create)
- `src/main/scala/go3d/server/Players.scala` (add expiration, hash tokens)
- `src/main/scala/go3d/server/Games.scala` (add version field)
- `src/main/scala/go3d/server/http4s/GoHttpService.scala` (apply middleware)

**Success Criteria**:
- ✅ No tokens in logs (verified by grep)
- ✅ Rate limiting returns 429 after 10 req/sec
- ✅ Tokens expire after 24 hours (automated cleanup)
- ✅ Concurrent updates detected and rejected

---

### Phase 2: AWS Infrastructure Setup
**Issue**: [#118](https://gitlab.com/go-3/go-3/-/issues/118)
**Timeline**: 3-4 days
**Risk**: Low (infrastructure only, no code changes)

**Goals**:
- Create DynamoDB tables
- Create S3 bucket with lifecycle policies
- Set up IAM roles and policies
- Configure VPC endpoints
- Enable encryption and monitoring

**DynamoDB Tables**:
```bash
# Create go3d-active-games table
aws dynamodb create-table \
  --table-name go3d-active-games \
  --attribute-definitions AttributeName=gameId,AttributeType=S \
  --key-schema AttributeName=gameId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --sse-specification Enabled=true \
  --tags Key=Project,Value=go3d Key=Environment,Value=production

# Enable TTL
aws dynamodb update-time-to-live \
  --table-name go3d-active-games \
  --time-to-live-specification Enabled=true,AttributeName=expiresAt

# Create go3d-players table
aws dynamodb create-table \
  --table-name go3d-players \
  --attribute-definitions \
    AttributeName=gameId,AttributeType=S \
    AttributeName=color,AttributeType=S \
  --key-schema \
    AttributeName=gameId,KeyType=HASH \
    AttributeName=color,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --sse-specification Enabled=true \
  --tags Key=Project,Value=go3d

# Enable TTL
aws dynamodb update-time-to-live \
  --table-name go3d-players \
  --time-to-live-specification Enabled=true,AttributeName=expiresAt
```

**S3 Bucket**:
```bash
# Create bucket
aws s3api create-bucket \
  --bucket go3d-game-archives \
  --region us-east-1 \
  --object-ownership BucketOwnerEnforced

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket go3d-game-archives \
  --versioning-configuration Status=Enabled

# Enable encryption
aws s3api put-bucket-encryption \
  --bucket go3d-game-archives \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'

# Block public access
aws s3api put-public-access-block \
  --bucket go3d-game-archives \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# Apply lifecycle policy (see S3 Lifecycle Policy section above)
aws s3api put-bucket-lifecycle-configuration \
  --bucket go3d-game-archives \
  --lifecycle-configuration file://lifecycle.json
```

**IAM Policies**:
- Lambda execution role with DynamoDB + S3 + CloudWatch access
- Least privilege: separate roles for read-only vs write endpoints

**Success Criteria**:
- ✅ Tables created and accessible
- ✅ S3 bucket encrypted, versioned, public access blocked
- ✅ CloudWatch alarms configured (cost >$10/mo, error rate >1%)

---

### Phase 3: DynamoDB Integration Layer
**Issue**: [#119](https://gitlab.com/go-3/go-3/-/issues/119)
**Timeline**: 5-7 days
**Risk**: Medium (dual-write complexity)

**Goals**:
- Add AWS SDK for Scala to build.sbt
- Implement DynamoDB client wrapper
- Dual-write: file-based + DynamoDB (file is source of truth)
- Hash tokens before DynamoDB storage

**Files Created**:
- `src/main/scala/go3d/server/aws/DynamoDBClient.scala`
- `src/main/scala/go3d/server/aws/DynamoDBGamesRepository.scala`
- `src/main/scala/go3d/server/aws/DynamoDBPlayersRepository.scala`
- `src/test/scala/go3d/server/aws/TestDynamoDB.scala`

**Files Modified**:
- `build.sbt` (add AWS SDK dependencies)
- `src/main/scala/go3d/server/Games.scala` (dual-write on add/update)
- `src/main/scala/go3d/server/Players.scala` (dual-write + SHA-256 hashing)

**build.sbt additions**:
```scala
libraryDependencies ++= Seq(
  "software.amazon.awssdk" % "dynamodb" % "2.20.0",
  "software.amazon.awssdk" % "s3" % "2.20.0",
  "com.github.pureconfig" %% "pureconfig" % "0.17.4" // for AWS config
)
```

**Success Criteria**:
- ✅ All writes succeed to both file + DynamoDB (or rollback on failure)
- ✅ Reads prioritize file-based (DynamoDB as backup)
- ✅ Tokens hashed with SHA-256 before DynamoDB storage
- ✅ Integration tests pass (TestDynamoDB.scala)

---

### Phase 4: S3 Archival System
**Issue**: [#120](https://gitlab.com/go-3/go-3/-/issues/120)
**Timeline**: 4-6 days
**Risk**: Low (archival is non-critical path)

**Goals**:
- Implement archive Lambda function
- Archive completed games to S3
- Implement retrieval endpoint with pre-signed URLs
- Enable S3 versioning and Object Lock

**Files Created**:
- `src/main/scala/go3d/server/aws/S3Client.scala`
- `src/main/scala/go3d/server/aws/ArchiveGameLambda.scala`
- `src/main/scala/go3d/server/http4s/GetArchivedGame.scala`

**Files Modified**:
- `src/main/scala/go3d/server/Games.scala` (trigger archive on game completion)
- `src/main/scala/go3d/server/http4s/GoHttpService.scala` (add `/archived/{gameId}` route)

**Archive Lambda Trigger**:
```scala
// In Games.scala archive() method
private def archive(gameId: String): Unit =
  Logger(Games.getClass).info(s"Archiving $gameId")
  archivedGames += (gameId -> activeGames(gameId))
  activeGames -= gameId
  fileIO.foreach(_.archiveGame(gameId))
  Players.unregister(gameId)

  // NEW: Trigger S3 archive
  S3Client.archiveGame(gameId, activeGames(gameId)) // async
```

**Pre-Signed URL Generation**:
```scala
// GetArchivedGame.scala
def handle: GoResponse =
  val s3Key = s"archives/${gameId.take(4)}/${gameId.substring(4, 6)}/${gameId}.json"
  val presignedUrl = S3Client.generatePresignedUrl(s3Key, expiresIn = 5.minutes)
  RedirectResponse(presignedUrl) // 302 redirect
```

**Success Criteria**:
- ✅ Completed games archived to S3 within 1 minute
- ✅ S3 objects have versioning enabled
- ✅ Object Lock enabled (cannot delete for 7 days)
- ✅ Pre-signed URLs work and expire after 5 minutes

---

### Phase 5: Lambda Functions (Read-Only Endpoints)
**Issue**: [#121](https://gitlab.com/go-3/go-3/-/issues/121)
**Timeline**: 5-7 days
**Risk**: Low (read-only, existing server as fallback)

**Goals**:
- Package server as Lambda JAR (sbt assembly)
- Deploy read-only endpoints to Lambda
- Set up API Gateway
- Monitor cold starts and costs

**Endpoints Migrated**:
- `GET /health` (healthcheck)
- `GET /status/{gameId}` (game state query)
- `GET /openGames` (list available games)

**Files Created**:
- `src/main/scala/go3d/server/lambda/LambdaHandler.scala`
- `src/main/scala/go3d/server/lambda/LambdaRequest.scala`
- `src/main/scala/go3d/server/lambda/LambdaResponse.scala`

**Files Modified**:
- `build.sbt` (add sbt-assembly plugin, Lambda handler config)

**build.sbt additions**:
```scala
enablePlugins(AssemblyPlugin)

assembly / assemblyJarName := "go3d-lambda.jar"
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
```

**Lambda Handler**:
```scala
class LambdaHandler extends RequestStreamHandler {
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val request = parseLambdaRequest(input)
    val response = request.path match {
      case "/health" => GetHealth().response
      case s"/status/$gameId" => GetStatus(gameId, request).response
      case "/openGames" => ListOpenGames().response
      case _ => NotFound
    }
    writeResponse(response, output)
  }
}
```

**API Gateway Setup**:
```bash
# Create REST API
aws apigateway create-rest-api --name go3d-api --region us-east-1

# Create resources and methods
# Configure Lambda proxy integration
# Enable throttling: 10 req/sec per IP, burst 20
# Deploy to "prod" stage
```

**Success Criteria**:
- ✅ Lambda cold starts <3 seconds (acceptable for read-only)
- ✅ Lambda warm invocations <100ms
- ✅ API Gateway throttling works (429 after 10 req/sec)
- ✅ Costs within budget ($0.20-2/month for Lambda)
- ✅ Existing server still running (no disruption)

---

### Phase 6: Evaluate & Optimize
**Issue**: [#122](https://gitlab.com/go-3/go-3/-/issues/122)
**Timeline**: 2 weeks (monitoring period)
**Risk**: None (evaluation only)

**Goals**:
- Collect 2 weeks of metrics from Phase 5
- Analyze cold starts, costs, consistency
- Decide: proceed with full migration or optimize

**Metrics to Track**:
- Lambda cold start latency (p50, p95, p99)
- Lambda warm invocation latency
- DynamoDB read/write latency
- S3 retrieval latency
- Cost breakdown (Lambda, DynamoDB, S3, API Gateway)
- Error rates (4xx, 5xx)

**Decision Tree**:
```
IF cold_starts > 5s THEN
  Explore GraalVM native image (50-200ms cold starts)
ELSE IF costs > 2x estimate ($10/month) THEN
  Consider provisioned concurrency OR stay hybrid
ELSE IF consistency_issues THEN
  Fix before proceeding (investigate dual-write bugs)
ELSE
  Proceed to Phase 7 (full migration)
END IF
```

**CloudWatch Queries**:
```
# Cold start latency
fields @timestamp, @duration, @initDuration
| filter @type = "REPORT"
| stats avg(@initDuration) as avg_cold_start, max(@initDuration) as max_cold_start by bin(5m)

# Error rate
fields @timestamp, @message
| filter @message like /ERROR/
| stats count() as error_count by bin(1h)
```

**Success Criteria**:
- ✅ 2 weeks of clean metrics (no gaps)
- ✅ Decision made based on data (not guessing)
- ✅ Cost dashboard created for ongoing monitoring

---

### Phase 7: Lambda Functions (Write Endpoints)
**Issue**: [#123](https://gitlab.com/go-3/go-3/-/issues/123)
**Timeline**: 5-7 days
**Risk**: Medium (write path critical, need rollback plan)

**Goals**:
- Migrate write endpoints to Lambda
- Remove dual-write (DynamoDB becomes source of truth)
- Migrate existing file-based saves to DynamoDB

**Endpoints Migrated**:
- `GET /new/{size}` (create game)
- `GET /register/{gameId}/{color}` (register player)
- `GET /set/{gameId}/{x}/{y}/{z}` (place stone)
- `GET /pass/{gameId}` (pass turn)

**Migration Script**:
```scala
// scripts/migrate-to-dynamodb.scala
object MigrateToD

ynamoDB extends App {
  val saveDir = "saves/"
  val files = new File(saveDir).listFiles.filter(_.getName.endsWith(".json"))

  files.foreach { file =>
    val saveGame = Games.readGame(file)
    val gameId = saveGame.players.last._2.gameId

    // Write to DynamoDB
    DynamoDBGamesRepository.put(gameId, saveGame.game)
    DynamoDBPlayersRepository.put(gameId, saveGame.players)

    // Verify write
    val retrieved = DynamoDBGamesRepository.get(gameId)
    assert(retrieved == saveGame.game, s"Migration failed for $gameId")

    println(s"Migrated $gameId")
  }

  // Backup originals to S3
  S3Client.uploadDirectory(saveDir, "backups/file-based-saves/")
}
```

**Files Modified**:
- `src/main/scala/go3d/server/Games.scala` (remove fileIO, use DynamoDB only)
- `src/main/scala/go3d/server/Players.scala` (remove dual-write)

**Rollback Plan**:
```
IF critical_issues_found THEN
  1. Switch API Gateway to point to existing server
  2. Re-enable dual-write to file system
  3. Investigate and fix issues
  4. Re-test in staging before retry
END IF
```

**Success Criteria**:
- ✅ All file-based saves migrated to DynamoDB (verified)
- ✅ No data loss (checksums match)
- ✅ Write endpoints working in Lambda
- ✅ Integration tests pass against Lambda

---

### Phase 8: Production Cutover
**Issue**: [#124](https://gitlab.com/go-3/go-3/-/issues/124)
**Timeline**: 3-5 days
**Risk**: Low (existing server available as fallback)

**Goals**:
- Update DNS/routing to API Gateway
- Run load tests
- Decommission existing server (after 30-day grace period)

**Load Tests**:
```bash
# 100 concurrent games
for i in {1..100}; do
  curl -s http://api.go3d.example.com/new/5 &
done
wait

# 1000 status requests/minute
ab -n 1000 -c 10 http://api.go3d.example.com/status/testgame

# Verify rate limiting
ab -n 100 -c 100 http://api.go3d.example.com/health
# Expect 429 responses
```

**Documentation**:
- Architecture diagram (DynamoDB + S3 + Lambda + API Gateway)
- Deployment runbook (how to deploy new Lambda versions)
- Rollback procedure (switch DNS back to existing server)
- Cost monitoring dashboard (CloudWatch + billing alerts)
- Operational alarms (error rate, cold starts, costs)

**Success Criteria**:
- ✅ DNS updated, all traffic routed to API Gateway
- ✅ Load tests pass (no errors, <100ms p95 latency)
- ✅ Rate limiting works (429 responses)
- ✅ Documentation complete
- ✅ Existing server kept as backup for 30 days
- ✅ After 30 days: existing server decommissioned

---

## Technology Stack

### AWS Services

| Service | Purpose | Configuration |
|---------|---------|---------------|
| **Lambda** | Serverless compute for HTTP handlers | 512MB memory, 30s timeout, http4s runtime |
| **DynamoDB** | Active game/player state storage | On-Demand pricing, TTL enabled, encryption at rest |
| **S3** | Archived game storage | Versioning, Object Lock, lifecycle policies |
| **API Gateway** | HTTP routing, rate limiting, authentication | REST API, Lambda proxy, 10 req/sec throttle |
| **CloudWatch** | Logs, metrics, alarms | 7-day retention, cost/error alarms |
| **CloudTrail** | Audit logging | S3 delivery, all API calls logged |
| **IAM** | Access control | Least privilege roles for Lambda |
| **VPC** | Network isolation (optional) | VPC endpoints for DynamoDB/S3 |

### Scala Libraries

```scala
// build.sbt
libraryDependencies ++= Seq(
  // Existing
  "org.http4s" %% "http4s-ember-server" % "1.0.0-M45",
  "org.http4s" %% "http4s-circe" % "1.0.0-M45",
  "org.http4s" %% "http4s-dsl" % "1.0.0-M45",
  "io.circe" %% "circe-generic" % "0.14.10",
  "org.typelevel" %% "cats-effect" % "3.5.7",

  // NEW: AWS SDK
  "software.amazon.awssdk" % "dynamodb" % "2.20.0",
  "software.amazon.awssdk" % "s3" % "2.20.0",
  "software.amazon.awssdk" % "sts" % "2.20.0", // for IAM role assumption

  // NEW: Lambda runtime
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
  "com.amazonaws" % "aws-lambda-java-events" % "3.11.0",

  // Utilities
  "com.github.pureconfig" %% "pureconfig" % "0.17.4", // AWS config
  "org.typelevel" %% "log4cats-slf4j" % "2.7.0" // structured logging
)
```

### Build Configuration

```scala
// project/plugins.sbt
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.0") // Lambda JAR packaging
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16") // Docker (if needed)
```

---

## References

### GitLab Issues

- [#112 - Set server up as Lambda (Epic)](https://gitlab.com/go-3/go-3/-/issues/112)
- [#117 - Phase 1: Security Hardening](https://gitlab.com/go-3/go-3/-/issues/117)
- [#118 - Phase 2: AWS Infrastructure Setup](https://gitlab.com/go-3/go-3/-/issues/118)
- [#119 - Phase 3: DynamoDB Integration Layer](https://gitlab.com/go-3/go-3/-/issues/119)
- [#120 - Phase 4: S3 Archival System](https://gitlab.com/go-3/go-3/-/issues/120)
- [#121 - Phase 5: Lambda Functions (Read-Only)](https://gitlab.com/go-3/go-3/-/issues/121)
- [#122 - Phase 6: Evaluate & Optimize](https://gitlab.com/go-3/go-3/-/issues/122)
- [#123 - Phase 7: Lambda Functions (Write Endpoints)](https://gitlab.com/go-3/go-3/-/issues/123)
- [#124 - Phase 8: Production Cutover](https://gitlab.com/go-3/go-3/-/issues/124)

### AWS Documentation

- [DynamoDB Best Practices](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/best-practices.html)
- [S3 Lifecycle Configuration](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html)
- [Lambda Best Practices](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html)
- [API Gateway Throttling](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-throttling.html)

### Cost Calculators

- [AWS Pricing Calculator](https://calculator.aws/)
- [DynamoDB Pricing](https://aws.amazon.com/dynamodb/pricing/)
- [S3 Pricing](https://aws.amazon.com/s3/pricing/)
- [Lambda Pricing](https://aws.amazon.com/lambda/pricing/)

### Analysis Documents

- This document (AWS.md) - comprehensive strategy
- S3 vs DynamoDB cost analysis (above in Cost Analysis section)
- Security implications (above in Security Analysis section)
- Access pattern analysis (above in Storage Architecture section)

---

## Appendix: Key Decisions

### Why Hybrid DynamoDB + S3?

**DynamoDB for Active Games**:
- ✅ Low latency (<10ms reads/writes) for frequent status checks
- ✅ TTL auto-cleanup (no manual archiving logic)
- ✅ Conditional writes prevent race conditions
- ✅ Scales automatically with traffic

**S3 for Archives**:
- ✅ Cheaper storage for infrequently accessed data ($0.023/GB vs $0.25/GB)
- ✅ Immutability via Object Lock (compliance, audit)
- ✅ Pre-signed URLs (secure, time-limited access without Lambda)
- ✅ Lifecycle policies (automatic cost optimization)

**Pure DynamoDB Alternative**:
- ❌ More expensive for large archives (10x storage cost)
- ❌ No immutability guarantees (can be overwritten)
- ❌ Cannot use lifecycle policies (manual cleanup required)

**Pure S3 Alternative**:
- ❌ Higher latency for active games (50-100ms vs <10ms)
- ❌ No TTL (manual cleanup required)
- ❌ No conditional writes (race conditions possible)
- ❌ More complex querying (no indexes)

### Why NOT S3 Intelligent-Tiering?

**Math**:
- 10 games/day × 365 days = 3,650 objects
- 3,650 objects × $0.0025/object/month = **$9.13/month** monitoring fee
- Storage cost: 182MB × $0.004/GB (Archive tier) = **$0.0007/month**
- **Monitoring fee is 13,000x more expensive than storage!**

**Break-even**:
- Intelligent-Tiering only makes sense when storage savings > monitoring fees
- Estimated break-even: **>100,000 objects** (where monitoring = $250/mo but storage savings = $300/mo)

**Conclusion**: Use lifecycle policies (Standard → IA → Deep Archive) until reaching 100K+ objects.

### Why Phased Approach?

**Risk Mitigation**:
- Each phase delivers independent value (can stop at any phase)
- Existing server runs alongside Lambda (zero downtime)
- Rollback at any phase boundary (DNS switch, disable dual-write)

**Learning**:
- Phase 5 metrics inform Phase 6 decision (data-driven, not guessing)
- Cold starts acceptable? → Proceed. Too slow? → GraalVM native image.
- Costs reasonable? → Proceed. Too high? → Optimize or stay hybrid.

**Complexity Management**:
- Small, focused changes (easier to review, test, debug)
- Each phase: 3-7 days (momentum, fast feedback)
- 8 phases × 5 days avg = **40 days** total (vs 3-6 months big-bang)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-19
**Author**: Claude Code (with human oversight)
**Status**: Draft (pending Phase 1 implementation)
