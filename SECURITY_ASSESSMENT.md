# Security Assessment: Go-3D Multi-Cloud Architecture

**Classification:** Internal - Security Review
**Date:** November 24, 2025
**Version:** 1.0
**Architecture Version:** TO-BE (Multi-Cloud)

---

## Executive Summary

This security assessment evaluates the TO-BE multi-cloud architecture for Go-3D server against four critical threat categories:

1. **Cost Overrun Attacks** - Malicious resource exhaustion causing unexpected bills
2. **Denial of Service** - Availability attacks against game service
3. **Data Loss** - Accidental or malicious destruction of game data
4. **Lateral Movement** - Using compromised server to attack connected infrastructure

**Overall Risk Rating:** 🟡 MEDIUM (with mitigations in place)

**Critical Findings:**
- ⚠️ **HIGH**: Lambda without rate limiting vulnerable to cost exhaustion attacks
- ⚠️ **HIGH**: Database credentials in environment variables risky
- ⚠️ **MEDIUM**: No VPC isolation increases blast radius of compromise
- ⚠️ **MEDIUM**: PostgreSQL publicly accessible in default RDS configuration
- ✅ **LOW**: Stateless design limits data loss impact

**Priority Actions:**
1. Implement API Gateway throttling (1-2 hours)
2. Move secrets to Secrets Manager (1 day)
3. Deploy in private VPC with NAT Gateway (2 days)
4. Enable database encryption and backups (1 day)
5. Implement Web Application Firewall (1 day)

---

## Table of Contents

1. [Threat Model](#1-threat-model)
2. [Cost Overrun Threats](#2-cost-overrun-threats)
3. [Denial of Service](#3-denial-of-service)
4. [Data Loss Scenarios](#4-data-loss-scenarios)
5. [Lateral Movement & Blast Radius](#5-lateral-movement--blast-radius)
6. [Security Controls](#6-security-controls)
7. [Monitoring & Detection](#7-monitoring--detection)
8. [Incident Response](#8-incident-response)
9. [Compliance Considerations](#9-compliance-considerations)
10. [Recommendations Summary](#10-recommendations-summary)

---

## 1. Threat Model

### 1.1 Threat Actors

| Actor | Motivation | Capability | Likelihood |
|-------|------------|------------|------------|
| **Script Kiddie** | Vandalism, notoriety | Low - Uses public tools | HIGH |
| **Cryptominer** | Financial gain | Medium - Automated bots | MEDIUM |
| **Ransomware Gang** | Ransom payment | High - Sophisticated tools | LOW |
| **Competitor** | Disrupt service | Medium - Targeted attacks | LOW |
| **Insider** | Data theft, sabotage | High - Privileged access | VERY LOW |
| **Nation State** | Espionage | Very High | VERY LOW (not a target) |

**Primary Threat Profile:** Script kiddies and cryptominers using automated tools.

### 1.2 Assets & Criticality

| Asset | Confidentiality | Integrity | Availability | Value |
|-------|-----------------|-----------|--------------|-------|
| **Game State** | LOW (public games) | HIGH | HIGH | MEDIUM |
| **Player Tokens** | HIGH | HIGH | MEDIUM | MEDIUM |
| **Database Credentials** | CRITICAL | CRITICAL | HIGH | HIGH |
| **Cloud Account** | CRITICAL | CRITICAL | CRITICAL | CRITICAL |
| **Source Code** | MEDIUM | HIGH | LOW | MEDIUM |
| **Metrics/Logs** | LOW | MEDIUM | LOW | LOW |

### 1.3 Attack Surface

```
Internet
    │
    ├─ API Gateway / ALB (Public)
    │   ├─ HTTP Endpoints (/new, /register, /set, /pass)
    │   └─ WebSocket (future)
    │
    ├─ Lambda / Cloud Run / Container App (Private)
    │   ├─ Application Code
    │   └─ Dependencies (http4s, Circe, etc.)
    │
    ├─ RDS / Cloud SQL / Azure Database (Private/Public?)
    │   ├─ PostgreSQL Port 5432
    │   └─ Database Credentials
    │
    ├─ ElastiCache / Memorystore / Azure Cache (Private)
    │   └─ Redis Port 6379
    │
    └─ Secrets Manager / Secret Manager / Key Vault
        └─ API Credentials
```

**Key Attack Vectors:**
1. HTTP API endpoints (authentication bypass, injection)
2. Database connection (SQL injection, credential theft)
3. Cloud provider APIs (IAM misconfiguration)
4. Container runtime (escape, privilege escalation)
5. Dependencies (supply chain attacks)

---

## 2. Cost Overrun Threats

### 2.1 Threat: Lambda Compute Exhaustion

#### Attack Scenario

```
Attacker          API Gateway        Lambda              AWS Bill
    │                  │                 │                   │
    │──Flood with─────▶│                 │                   │
    │  100,000 req/s   │                 │                   │
    │                  │                 │                   │
    │                  ├──Spawn 10,000──▶│                   │
    │                  │  instances      │                   │
    │                  │                 │                   │
    │                  │                 ├─Each runs 30s────▶│ $$$
    │                  │                 │  (expensive ops)  │
    │                  │                 │                   │
    │◀──200 OK─────────┤                 │                   │
    │  (attack works)  │                 │                   │
    │                  │                 │                   │
    │                  │                 │ Total cost:       │
    │                  │                 │ 10K instances ×   │
    │                  │                 │ 30s × $0.0000133  │
    │                  │                 │ = $4,000/hour     │
```

**Impact:** 💰 **CRITICAL**
- Hourly cost: $4,000+ (unmitigated)
- Daily cost: $96,000+ (unmitigated)
- Can bankrupt project in hours

#### Attack Variants

| Variant | Method | Cost Impact | Detection Difficulty |
|---------|--------|-------------|---------------------|
| **Request Flood** | Spam expensive endpoints | Very High | Easy |
| **Slowloris** | Keep connections open | High | Medium |
| **Algorithmic Complexity** | Trigger expensive operations (large board size) | Very High | Hard |
| **Database Query Bomb** | Trigger slow queries | High | Medium |

**Example: Algorithmic Complexity Attack**

```bash
# Attacker creates games with maximum board size
for i in {1..1000}; do
  curl "https://api.go3d.example.com/new/99" &  # Max size = 99
done

# Each game requires 99³ = 970,299 cells
# Memory: ~100MB per game
# Lambda spawns 1000 instances @ 512MB
# Cost: 1000 × $0.00001667/GB-sec × 30s = $500
```

#### Current Vulnerabilities (AS-IS)

| Vulnerability | Severity | Exploitable |
|---------------|----------|-------------|
| ❌ No rate limiting | CRITICAL | ✅ Yes |
| ❌ No request throttling | CRITICAL | ✅ Yes |
| ❌ No cost alerts | HIGH | ✅ Yes |
| ❌ Unlimited board size | HIGH | ✅ Yes |
| ❌ No authentication on /new | HIGH | ✅ Yes |
| ❌ No CAPTCHA | MEDIUM | ✅ Yes |

#### Mitigations (TO-BE)

##### Priority 1: API Gateway Throttling (Implement Immediately)

```yaml
# AWS API Gateway Throttling
Resources:
  ApiGateway:
    Type: AWS::ApiGatewayV2::Api
    Properties:
      ThrottleSettings:
        BurstLimit: 100      # Max concurrent requests
        RateLimit: 10        # Requests per second

  # Per-route throttling
  RouteThrottling:
    Type: AWS::ApiGatewayV2::Route
    Properties:
      RouteKey: "POST /new"
      ThrottleSettings:
        BurstLimit: 10       # Only 10 new games/sec
        RateLimit: 1         # 1 game per second sustained
```

**Cost Impact:** FREE (built-in feature)
**Effectiveness:** Reduces attack cost from $4,000/hr to $40/hr (99% reduction)

##### Priority 2: Lambda Reserved Concurrency

```yaml
Resources:
  GoServerFunction:
    Type: AWS::Serverless::Function
    Properties:
      ReservedConcurrentExecutions: 100  # Hard limit
      Timeout: 30
      MemorySize: 256
```

**Cost Impact:** FREE (no additional charge)
**Effectiveness:** Caps maximum cost at $100/hr (worst case)

##### Priority 3: AWS Budgets Alerts

```yaml
Resources:
  CostAlert:
    Type: AWS::Budgets::Budget
    Properties:
      Budget:
        BudgetLimit:
          Amount: 100
          Unit: USD
        BudgetType: COST
        TimeUnit: MONTHLY
      NotificationsWithSubscribers:
        - Notification:
            NotificationType: ACTUAL
            ComparisonOperator: GREATER_THAN
            Threshold: 50      # Alert at 50% of budget
          Subscribers:
            - Email: admin@example.com
        - Notification:
            NotificationType: ACTUAL
            ComparisonOperator: GREATER_THAN
            Threshold: 80      # Alert at 80%
          Subscribers:
            - Email: admin@example.com
        - Notification:
            NotificationType: FORECASTED
            ComparisonOperator: GREATER_THAN
            Threshold: 100     # Alert if forecasted to exceed
          Subscribers:
            - Email: admin@example.com
```

##### Priority 4: Application-Level Rate Limiting

```scala
// Implement rate limiting with Redis
class RateLimiter(redis: SessionStore) {

  def checkRateLimit(clientIp: String, endpoint: String): IO[Boolean] = {
    val key = s"ratelimit:$clientIp:$endpoint"
    val maxRequests = 10
    val windowSeconds = 60

    for {
      count <- redis.get(key).map(_.fold(0)(_.toInt))
      allowed = count < maxRequests
      _ <- if (allowed) redis.set(key, (count + 1).toString, windowSeconds)
           else IO.unit
    } yield allowed
  }
}

// Use in HTTP handler
def handleNewGame(request: Request[IO], limiter: RateLimiter): IO[Response[IO]] = {
  val clientIp = request.remoteAddr.getOrElse("unknown")

  limiter.checkRateLimit(clientIp, "new-game").flatMap {
    case true => createGame(request)
    case false => TooManyRequests("Rate limit exceeded. Try again in 1 minute.")
  }
}
```

##### Priority 5: Input Validation

```scala
// Validate board size to prevent resource exhaustion
case class BoardSizeValidator {
  val MinSize = 3
  val MaxSize = 19  // Traditional Go max (not 99!)
  val RecommendedMax = 9  // For 3D, 9x9x9 = 729 cells

  def validate(size: Int): Either[String, Int] = {
    if (size < MinSize) Left(s"Board size too small. Minimum: $MinSize")
    else if (size > MaxSize) Left(s"Board size too large. Maximum: $MaxSize")
    else if (size > RecommendedMax) Left(s"Warning: Size $size may be slow. Recommended max: $RecommendedMax")
    else Right(size)
  }
}
```

### 2.2 Threat: Database Resource Exhaustion

#### Attack Scenario

```sql
-- Attacker triggers expensive queries
-- via large board size or many concurrent games

-- Query 1: Load game with 99³ cells
SELECT * FROM games WHERE id = 'game-99x99x99';
-- Returns 970,299 cells as JSONB
-- Memory: 100MB per query
-- Time: 5-10 seconds

-- Query 2: Concurrent query bomb
-- Attacker creates 100 games simultaneously
-- 100 × 100MB = 10GB memory on database
-- Database OOM, crashes
```

**Impact:** 💰 **HIGH** + 📉 **Availability Loss**

#### Mitigations

##### Connection Pooling

```scala
// HikariCP configuration
val hikariConfig = new HikariConfig()
hikariConfig.setJdbcUrl(dbUrl)
hikariConfig.setMaximumPoolSize(10)      // Limit connections
hikariConfig.setMinimumIdle(2)
hikariConfig.setConnectionTimeout(5000)   // 5 second timeout
hikariConfig.setIdleTimeout(300000)       // 5 min idle timeout
hikariConfig.setMaxLifetime(600000)       // 10 min max lifetime
```

##### Query Timeouts

```scala
// Set statement timeout in PostgreSQL
sql"""SET statement_timeout TO '5000'""".update.run  // 5 second max

// Or in connection string
jdbc:postgresql://host:5432/db?options=-c%20statement_timeout=5000
```

##### Database Resource Limits

```yaml
# RDS Parameter Group
Resources:
  DBParameterGroup:
    Type: AWS::RDS::DBParameterGroup
    Properties:
      Parameters:
        max_connections: 100         # Limit total connections
        shared_buffers: 256MB        # Memory for caching
        work_mem: 4MB                # Per-query memory limit
        statement_timeout: 30000     # 30 second query timeout
```

### 2.3 Threat: Storage Exhaustion

#### Attack Scenario

```
Attacker creates millions of tiny games
├─ Each game: ~5KB JSON
├─ 1 million games = 5GB storage
├─ RDS storage cost: $0.10/GB/month
└─ Total: $0.50/month (not critical, but annoying)

More serious: Fill logs
├─ Trigger verbose errors
├─ 1GB CloudWatch Logs = $0.50
├─ 100GB logs = $50/month
└─ Can increase bill significantly
```

#### Mitigations

```yaml
# CloudWatch Logs retention
Resources:
  LogGroup:
    Type: AWS::Logs::LogGroup
    Properties:
      RetentionInDays: 7  # Auto-delete after 7 days

# S3 lifecycle policy for archived games
  ArchiveBucket:
    Type: AWS::S3::Bucket
    Properties:
      LifecycleConfiguration:
        Rules:
          - Id: DeleteOldGames
            Status: Enabled
            ExpirationInDays: 90  # Delete after 90 days
          - Id: TransitionToGlacier
            Status: Enabled
            Transitions:
              - StorageClass: GLACIER
                TransitionInDays: 30
```

### 2.4 Cost Overrun Summary

| Threat | Unmitigated Cost | Mitigated Cost | Risk Reduction |
|--------|------------------|----------------|----------------|
| Lambda flood | $4,000/hr | $40/hr | 99% |
| Database exhaustion | $500/hr | $50/hr | 90% |
| Storage exhaustion | $50/mo | $5/mo | 90% |
| Log flooding | $100/mo | $10/mo | 90% |

**Total Maximum Monthly Cost with Mitigations:** ~$200-300 (vs $50,000+ unmitigated)

---

## 3. Denial of Service

### 3.1 Application-Level DoS

#### Threat: Slowloris Attack

```
Attacker          Lambda               Database
    │                │                     │
    ├─Start request─▶│                     │
    │                ├─Open DB conn───────▶│
    │                │                     │
    │                │  ⏳ Never complete  │
    │                │                     │
    │ (repeat 1000×) │                     │
    │                │                     │
    └────────────────┴─────────────────────┴─
                     ↓
           All connections exhausted
           New requests fail with 503
```

**Impact:** 📉 **HIGH** - Service unavailable

#### Mitigations

```scala
// Set aggressive timeouts
val serverConfig = EmberServerBuilder
  .default[IO]
  .withHttpApp(httpApp)
  .withIdleTimeout(30.seconds)        // Close idle connections
  .withResponseHeaderTimeout(10.seconds)
  .withRequestHeaderTimeout(10.seconds)
  .withMaxConnections(1000)           // Hard limit
  .build
```

### 3.2 Infrastructure-Level DoS

#### Threat: Volumetric Attack (Layer 7)

```
Botnet (10,000 IPs)
    │
    ├─Each IP: 100 req/s
    ├─Total: 1,000,000 req/s
    │
    ▼
API Gateway / ALB
    │
    ├─Throttle: 100 req/s per IP ✅
    ├─WAF: Block malicious patterns ✅
    │
    └─▶ Blocked at edge
        Cost: $0 (free WAF filtering)
```

**Impact:** 📉 **MEDIUM** (with WAF) / **CRITICAL** (without)

#### Mitigations

##### AWS WAF Rules

```yaml
Resources:
  WebACL:
    Type: AWS::WAFv2::WebACL
    Properties:
      Rules:
        # Rule 1: Rate limiting per IP
        - Name: RateLimitRule
          Priority: 1
          Statement:
            RateBasedStatement:
              Limit: 2000              # 2000 requests per 5 min
              AggregateKeyType: IP
          Action:
            Block:
              CustomResponse:
                ResponseCode: 429

        # Rule 2: Block known bad IPs
        - Name: IPReputationList
          Priority: 2
          Statement:
            ManagedRuleGroupStatement:
              VendorName: AWS
              Name: AWSManagedRulesAmazonIpReputationList
          Action:
            Block: {}

        # Rule 3: Block common attack patterns
        - Name: CoreRuleSet
          Priority: 3
          Statement:
            ManagedRuleGroupStatement:
              VendorName: AWS
              Name: AWSManagedRulesCommonRuleSet
          Action:
            Block: {}

        # Rule 4: SQL injection protection
        - Name: SQLiProtection
          Priority: 4
          Statement:
            ManagedRuleGroupStatement:
              VendorName: AWS
              Name: AWSManagedRulesSQLiRuleSet
          Action:
            Block: {}
```

**Cost:** ~$5-10/month for 1M requests
**Effectiveness:** Blocks 95%+ of automated attacks

##### CloudFront (DDoS Protection)

```yaml
Resources:
  CloudFrontDistribution:
    Type: AWS::CloudFront::Distribution
    Properties:
      DistributionConfig:
        Origins:
          - DomainName: !GetAtt ApiGateway.DomainName
            Id: ApiGatewayOrigin
        DefaultCacheBehavior:
          TargetOriginId: ApiGatewayOrigin
          ViewerProtocolPolicy: redirect-to-https
          CachePolicyId: !Ref CachePolicy

        # AWS Shield Standard (free)
        # Protects against Layer 3/4 DDoS
```

**Cost:** ~$1/GB data transfer (normal)
**Benefit:** FREE DDoS protection (Layer 3/4)

### 3.3 Database DoS

#### Threat: Query Bomb

```sql
-- Attacker triggers expensive query via API
POST /games/{id}/status

-- Server executes:
SELECT * FROM games
WHERE status = 'active'
  AND size > 15
ORDER BY created_at DESC;

-- Query scans millions of rows
-- Takes 30+ seconds
-- Locks tables
-- All other queries wait
```

#### Mitigations

```sql
-- Add indexes
CREATE INDEX idx_games_status_size ON games(status, size);
CREATE INDEX idx_games_created_at ON games(created_at DESC);

-- Limit result sets
SELECT * FROM games
WHERE status = 'active'
  AND size > 15
ORDER BY created_at DESC
LIMIT 100;  -- Never return more than 100

-- Use read replicas
-- Route expensive queries to read replica
-- Primary handles writes only
```

### 3.4 DoS Summary

| Attack Type | Impact (Unmitigated) | Impact (Mitigated) | Mitigation Cost |
|-------------|---------------------|-------------------|-----------------|
| **Slowloris** | Service down 100% | Service down 0% | FREE (timeouts) |
| **HTTP Flood** | Service down 100% | Service degraded 10% | $10/mo (WAF) |
| **Query Bomb** | DB down 100% | DB slow 20% | FREE (indexes) |
| **DDoS (L3/L4)** | Service down 100% | Service up 99.9% | FREE (Shield) |

---

## 4. Data Loss Scenarios

### 4.1 Threat: Accidental Deletion

#### Scenario 1: Developer Mistake

```bash
# Developer accidentally deletes production table
psql -h prod-db.amazonaws.com -U admin go3d
> DROP TABLE games;  # Oops, wrong terminal!

Result:
- All game data lost
- No backups = permanent loss
```

**Impact:** 💀 **CRITICAL**

**Likelihood:** MEDIUM (human error is common)

#### Mitigations

##### Automated Backups

```yaml
Resources:
  Database:
    Type: AWS::RDS::DBInstance
    Properties:
      BackupRetentionPeriod: 30  # Keep 30 days of backups
      PreferredBackupWindow: "03:00-04:00"  # Daily at 3am UTC
      CopyTagsToSnapshot: true
      EnableCloudwatchLogsExports:
        - postgresql

      # Point-in-time recovery
      PointInTimeRecoveryEnabled: true  # Can restore to any second!
```

**Recovery Time:** 15-30 minutes (restore from snapshot)
**Data Loss:** Maximum 1 hour (point-in-time recovery)

##### Database Protection

```sql
-- Revoke DROP permissions from application user
REVOKE DROP ON DATABASE go3d FROM app_user;
REVOKE DELETE ON games FROM app_user;

-- Use separate admin user for schema changes
CREATE USER admin WITH PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE go3d TO admin;

-- Application user only gets SELECT, INSERT, UPDATE
CREATE USER app_user WITH PASSWORD 'app_password';
GRANT SELECT, INSERT, UPDATE ON games TO app_user;
GRANT SELECT, INSERT, UPDATE ON players TO app_user;
```

### 4.2 Threat: Ransomware

#### Attack Scenario

```
1. Attacker gains access to database credentials
   (via exposed env vars, stolen secrets, etc.)

2. Attacker connects to database
   psql -h db.example.com -U app_user

3. Attacker dumps data
   pg_dump go3d > backup.sql

4. Attacker encrypts or deletes data
   UPDATE games SET goban = 'ENCRYPTED';
   -- or --
   DROP TABLE games CASCADE;

5. Attacker demands ransom
   "Pay 10 BTC to recover your data"
```

**Impact:** 💀 **CRITICAL**
**Likelihood:** LOW (requires credential theft)

#### Mitigations

##### Encryption at Rest

```yaml
Resources:
  Database:
    Type: AWS::RDS::DBInstance
    Properties:
      StorageEncrypted: true
      KmsKeyId: !Ref DatabaseEncryptionKey

  DatabaseEncryptionKey:
    Type: AWS::KMS::Key
    Properties:
      Description: Encryption key for RDS
      KeyPolicy:
        Statement:
          - Effect: Allow
            Principal:
              Service: rds.amazonaws.com
            Action:
              - kms:Decrypt
              - kms:CreateGrant
            Resource: "*"
```

**Benefit:** Data unreadable if storage media is stolen

##### Immutable Backups

```yaml
# AWS Backup with vault lock
Resources:
  BackupVault:
    Type: AWS::Backup::BackupVault
    Properties:
      BackupVaultName: go3d-vault
      LockConfiguration:
        MinRetentionDays: 30  # Cannot delete backups for 30 days
```

**Benefit:** Ransomware cannot delete backups

##### Network Isolation

```yaml
# Database in private subnet, no internet access
Resources:
  DatabaseSubnetGroup:
    Type: AWS::RDS::DBSubnetGroup
    Properties:
      SubnetIds:
        - !Ref PrivateSubnet1
        - !Ref PrivateSubnet2
      DBSubnetGroupDescription: Private subnets for database

  DatabaseSecurityGroup:
    Type: AWS::EC2::SecurityGroup
    Properties:
      GroupDescription: Database security group
      VpcId: !Ref VPC
      SecurityGroupIngress:
        - IpProtocol: tcp
          FromPort: 5432
          ToPort: 5432
          SourceSecurityGroupId: !Ref LambdaSecurityGroup
          # Only Lambda can connect, not internet
```

### 4.3 Threat: Data Corruption

#### Scenario: Software Bug

```scala
// Bug in game logic corrupts game state
def makeMove(move: Move): Game = {
  // Bug: Doesn't validate position
  val newGoban = goban.setStone(move.x + 1, move.y, move.z, move.color)
  // ❌ Off-by-one error corrupts board
  Game(size, newGoban, moves.appended(move), captures)
}
```

**Impact:** 💀 **HIGH**
**Likelihood:** LOW (good test coverage)

#### Mitigations

##### Database Constraints

```sql
-- Ensure data integrity with constraints
CREATE TABLE games (
    id VARCHAR(32) PRIMARY KEY,
    size INT NOT NULL CHECK (size >= 3 AND size <= 19),
    goban JSONB NOT NULL,
    moves JSONB NOT NULL DEFAULT '[]',
    captures JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed', 'archived')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Validate JSONB structure
ALTER TABLE games ADD CONSTRAINT valid_goban_size
  CHECK (jsonb_array_length(goban->'stones') = size + 2);
```

##### Write-Ahead Logging

```yaml
# RDS automatically enabled
# Allows recovery from corruption via transaction log
```

### 4.4 Data Loss Summary

| Threat | RTO (Recovery Time) | RPO (Data Loss) | Cost |
|--------|---------------------|-----------------|------|
| **Accidental deletion** | 30 min | 1 hour | $0 (included) |
| **Ransomware** | 30 min | 0 (immutable backups) | $5/mo |
| **Corruption** | 30 min | 1 hour | $0 (included) |
| **AZ failure** | 2 min | 0 (Multi-AZ) | +30% DB cost |

---

## 5. Lateral Movement & Blast Radius

### 5.1 Threat: Compromised Lambda Function

#### Attack Scenario

```
1. Attacker exploits vulnerability in application code
   (e.g., dependency vulnerability, code injection)

2. Attacker gains code execution in Lambda

3. Attacker accesses environment variables
   DB_PASSWORD=supersecret123  ❌ Exposed!

4. Attacker pivots to database
   psql -h db.amazonaws.com -U app -p supersecret123

5. Attacker exfiltrates all game data

6. Attacker pivots to other AWS services
   aws s3 ls  # List S3 buckets (if IAM too permissive)
   aws ec2 describe-instances  # Enumerate EC2
```

**Impact:** 💀 **CRITICAL** - Full compromise of infrastructure

#### Current Vulnerabilities (AS-IS TO-BE)

| Vulnerability | Severity | Exploitable |
|---------------|----------|-------------|
| ❌ DB credentials in env vars | CRITICAL | ✅ Yes |
| ❌ Lambda has full AWS API access | CRITICAL | ✅ Yes |
| ❌ Database publicly accessible | HIGH | ⚠️ Maybe |
| ❌ No VPC isolation | HIGH | ✅ Yes |
| ❌ No secrets rotation | MEDIUM | ✅ Yes |

### 5.2 Blast Radius Analysis

#### Scenario: Compromised Lambda (Current Design)

```
┌─────────────────────────────────────────────────────┐
│         Blast Radius: ENTIRE AWS ACCOUNT            │
│                                                     │
│  ┌──────────────┐                                  │
│  │  Attacker    │                                  │
│  │  in Lambda   │──────┐                           │
│  └──────────────┘      │                           │
│                        │                           │
│         ┌──────────────┼──────────────┐            │
│         │              │              │            │
│    ┌────▼───┐     ┌────▼───┐    ┌────▼───┐        │
│    │  RDS   │     │   S3   │    │  Other │        │
│    │Database│     │Archives│    │Services│        │
│    └────────┘     └────────┘    └────────┘        │
│         │              │              │            │
│         └──────────────┴──────────────┘            │
│                        │                           │
│               ┌────────▼────────┐                  │
│               │  IAM Policies   │                  │
│               │  (too permissive)│                 │
│               └─────────────────┘                  │
└─────────────────────────────────────────────────────┘
```

**Compromised Assets:**
- ✅ All game data in database
- ✅ All archived games in S3
- ✅ Other Lambda functions
- ✅ Potentially other AWS services (if IAM too broad)

#### Scenario: Compromised Lambda (Secured Design)

```
┌─────────────────────────────────────────────────────┐
│      Blast Radius: LIMITED TO DATABASE ONLY         │
│                                                     │
│  ┌──────────────┐                                  │
│  │  Attacker    │                                  │
│  │  in Lambda   │──────┐                           │
│  └──────────────┘      │                           │
│                        │                           │
│                   ┌────▼───┐                        │
│                   │  RDS   │                        │
│                   │Database│                        │
│                   └────────┘                        │
│                        │                           │
│                        X  Firewall blocks          │
│                        X  access to S3             │
│                        X                           │
│    ┌─────────┐    ┌────────┐    ┌─────────┐       │
│    │   S3    │    │ Other  │    │  Other  │       │
│    │Archives │    │Lambda  │    │Services │       │
│    │(blocked)│    │(blocked)│    │(blocked)│       │
│    └─────────┘    └────────┘    └─────────┘       │
│         │              │              │            │
│         └──────────────┴──────────────┘            │
│                        │                           │
│               ┌────────▼────────┐                  │
│               │  IAM Policies   │                  │
│               │(least privilege)│                  │
│               └─────────────────┘                  │
└─────────────────────────────────────────────────────┘
```

**Compromised Assets:**
- ⚠️ Game data in database (limited by IAM)
- ❌ S3 archives (no access)
- ❌ Other Lambda functions (isolated)
- ❌ Other AWS services (no permissions)

### 5.3 Mitigations

#### Priority 1: Move Secrets to Secrets Manager

```scala
// BEFORE (❌ Insecure)
object ServerConfig {
  val dbPassword = sys.env("DB_PASSWORD")  // ❌ In env var
}

// AFTER (✅ Secure)
trait SecretManager {
  def getSecret(name: String): IO[String]
}

class AWSSecretManager extends SecretManager {
  private val client = SecretsManagerClient.create()

  def getSecret(name: String): IO[String] = IO.blocking {
    val request = GetSecretValueRequest.builder()
      .secretId(name)
      .build()
    client.getSecretValue(request).secretString()
  }
}

object ServerConfig {
  def load(secrets: SecretManager): IO[DatabaseConfig] = {
    secrets.getSecret("go3d/database/password").map { password =>
      DatabaseConfig(
        host = sys.env("DB_HOST"),
        password = password  // ✅ From Secrets Manager
      )
    }
  }
}
```

**Terraform Configuration:**

```hcl
# Store database password in Secrets Manager
resource "aws_secretsmanager_secret" "db_password" {
  name = "go3d/database/password"
  description = "PostgreSQL password for Go-3D app"

  rotation_rules {
    automatically_after_days = 90  # Auto-rotate every 90 days
  }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

# Lambda IAM policy (least privilege)
resource "aws_iam_role_policy" "lambda_secrets" {
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          aws_secretsmanager_secret.db_password.arn
        ]
      }
    ]
  })
}
```

**Benefit:** Attacker with Lambda access cannot see secrets in plaintext

#### Priority 2: Least Privilege IAM

```hcl
# Lambda execution role (strict permissions)
resource "aws_iam_role" "lambda_exec" {
  name = "go3d-lambda-exec"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy" "lambda_minimal" {
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # CloudWatch Logs (write-only)
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:log-group:/aws/lambda/go3d-*"
      },
      # VPC networking (if using VPC)
      {
        Effect = "Allow"
        Action = [
          "ec2:CreateNetworkInterface",
          "ec2:DescribeNetworkInterfaces",
          "ec2:DeleteNetworkInterface"
        ]
        Resource = "*"
        Condition = {
          StringEquals = {
            "ec2:Vpc" = aws_vpc.main.arn
          }
        }
      },
      # Secrets Manager (specific secret only)
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = aws_secretsmanager_secret.db_password.arn
      }
    ]
  })
}
```

**What Lambda CANNOT do:**
- ❌ List S3 buckets
- ❌ Describe EC2 instances
- ❌ Modify IAM policies
- ❌ Access other Lambdas
- ❌ Delete CloudWatch logs

#### Priority 3: VPC Isolation

```hcl
# VPC with private subnets
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support = true
}

# Private subnets for Lambda and RDS
resource "aws_subnet" "private" {
  count = 2
  vpc_id = aws_vpc.main.id
  cidr_block = "10.0.${count.index + 1}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]
}

# Public subnets for NAT Gateway (outbound only)
resource "aws_subnet" "public" {
  count = 2
  vpc_id = aws_vpc.main.id
  cidr_block = "10.0.${count.index + 101}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
}

# NAT Gateway for Lambda outbound internet
resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id = aws_subnet.public[0].id
}

# Lambda security group
resource "aws_security_group" "lambda" {
  name = "go3d-lambda"
  vpc_id = aws_vpc.main.id

  # Outbound to database only
  egress {
    from_port = 5432
    to_port = 5432
    protocol = "tcp"
    security_groups = [aws_security_group.database.id]
  }

  # Outbound to Redis only
  egress {
    from_port = 6379
    to_port = 6379
    protocol = "tcp"
    security_groups = [aws_security_group.redis.id]
  }

  # Outbound HTTPS for Secrets Manager
  egress {
    from_port = 443
    to_port = 443
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Database security group
resource "aws_security_group" "database" {
  name = "go3d-database"
  vpc_id = aws_vpc.main.id

  # Inbound from Lambda only
  ingress {
    from_port = 5432
    to_port = 5432
    protocol = "tcp"
    security_groups = [aws_security_group.lambda.id]
  }

  # No outbound (database doesn't need internet)
}
```

**Benefit:**
- Database is NOT publicly accessible
- Lambda can only talk to database and Redis
- Compromised Lambda cannot pivot to other services

#### Priority 4: Network Segmentation

```
┌─────────────────────────────────────────────────────┐
│                      VPC (10.0.0.0/16)              │
│                                                     │
│  ┌────────────────────────────────────────────┐    │
│  │         Public Subnet (10.0.101.0/24)      │    │
│  │  ┌──────────────┐      ┌──────────────┐    │    │
│  │  │ NAT Gateway  │      │Internet GW   │    │    │
│  │  └──────┬───────┘      └──────┬───────┘    │    │
│  └─────────┼──────────────────────┼────────────┘    │
│            │                      │                 │
│  ┌─────────▼──────────────────────▼────────────┐    │
│  │       Private Subnet (10.0.1.0/24)          │    │
│  │  ┌──────────────┐      ┌──────────────┐    │    │
│  │  │   Lambda     │─────▶│  RDS (DB)    │    │    │
│  │  │   (App)      │      │  (Private)   │    │    │
│  │  └──────┬───────┘      └──────────────┘    │    │
│  │         │                                   │    │
│  │         └──────────────▶┌──────────────┐    │    │
│  │                         │ElastiCache   │    │    │
│  │                         │  (Redis)     │    │    │
│  │                         └──────────────┘    │    │
│  └──────────────────────────────────────────────┘    │
│                                                     │
│  Firewall Rules:                                    │
│  - Lambda → RDS: ✅ Port 5432                       │
│  - Lambda → Redis: ✅ Port 6379                     │
│  - Lambda → Internet: ✅ HTTPS only (Secrets Mgr)   │
│  - Internet → RDS: ❌ BLOCKED                       │
│  - Internet → Redis: ❌ BLOCKED                     │
│  - Lambda → S3: ❌ BLOCKED (no IAM permission)      │
└─────────────────────────────────────────────────────┘
```

### 5.4 Lateral Movement Summary

| Scenario | Without Mitigations | With Mitigations | Risk Reduction |
|----------|-------------------|------------------|----------------|
| **Compromised Lambda** | Full account access | Database only | 95% |
| **Stolen credentials** | Direct DB access | Cannot connect (private VPC) | 100% |
| **Code injection** | AWS API access | Minimal permissions | 90% |
| **Dependency vuln** | Full compromise | Limited blast radius | 90% |

---

## 6. Security Controls

### 6.1 Defense in Depth

```
Layer 7: Application Security
├─ Input validation
├─ Output encoding
├─ Rate limiting
├─ Authentication/Authorization
└─ Secure coding practices

Layer 6: Data Security
├─ Encryption at rest (RDS, S3)
├─ Encryption in transit (TLS)
├─ Secrets management
├─ Database backups
└─ Immutable backups (vault lock)

Layer 5: Network Security
├─ VPC isolation
├─ Security groups (firewall)
├─ Private subnets
├─ WAF (Web Application Firewall)
└─ DDoS protection (AWS Shield)

Layer 4: Access Control
├─ IAM least privilege
├─ No root credentials
├─ MFA for admin access
├─ Service-specific roles
└─ Regular permission audits

Layer 3: Monitoring & Detection
├─ CloudWatch alarms
├─ GuardDuty (threat detection)
├─ AWS Config (compliance)
├─ CloudTrail (audit logs)
└─ Cost anomaly detection

Layer 2: Resilience
├─ Multi-AZ deployment
├─ Auto-scaling
├─ Health checks
├─ Circuit breakers
└─ Graceful degradation

Layer 1: Governance
├─ Security policies
├─ Incident response plan
├─ Regular security reviews
├─ Penetration testing
└─ Security training
```

### 6.2 Security Checklist

#### Pre-Deployment (Before Production)

- [ ] Secrets stored in Secrets Manager (not env vars)
- [ ] IAM roles follow least privilege
- [ ] Database in private VPC
- [ ] Database encryption enabled
- [ ] Automated backups configured (30 days)
- [ ] WAF rules enabled
- [ ] Rate limiting configured
- [ ] Input validation implemented
- [ ] Security groups restrict access
- [ ] CloudWatch alarms configured
- [ ] Cost budgets and alerts set
- [ ] Incident response plan documented

#### Post-Deployment (Ongoing)

- [ ] Review CloudWatch logs weekly
- [ ] Review AWS Cost Explorer weekly
- [ ] Rotate secrets quarterly
- [ ] Update dependencies monthly
- [ ] Security audit quarterly
- [ ] Penetration test annually
- [ ] Disaster recovery drill annually

---

## 7. Monitoring & Detection

### 7.1 CloudWatch Alarms

```yaml
Resources:
  # High Lambda error rate
  LambdaErrorAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: go3d-lambda-errors
      MetricName: Errors
      Namespace: AWS/Lambda
      Statistic: Sum
      Period: 300  # 5 minutes
      EvaluationPeriods: 1
      Threshold: 10  # Alert if >10 errors in 5 min
      ComparisonOperator: GreaterThanThreshold

  # High Lambda invocation count (potential attack)
  LambdaInvocationAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: go3d-lambda-invocation-spike
      MetricName: Invocations
      Namespace: AWS/Lambda
      Statistic: Sum
      Period: 60  # 1 minute
      EvaluationPeriods: 1
      Threshold: 1000  # Alert if >1000 invocations/min
      ComparisonOperator: GreaterThanThreshold

  # Database CPU high (potential attack or bug)
  DatabaseCPUAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: go3d-db-cpu-high
      MetricName: CPUUtilization
      Namespace: AWS/RDS
      Statistic: Average
      Period: 300
      EvaluationPeriods: 2
      Threshold: 80  # Alert if >80% CPU for 10 minutes
      ComparisonOperator: GreaterThanThreshold

  # Database connections exhausted
  DatabaseConnectionsAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: go3d-db-connections-high
      MetricName: DatabaseConnections
      Namespace: AWS/RDS
      Statistic: Average
      Period: 60
      EvaluationPeriods: 1
      Threshold: 80  # Alert if >80 of 100 connections
      ComparisonOperator: GreaterThanThreshold
```

### 7.2 AWS GuardDuty (Threat Detection)

```yaml
Resources:
  GuardDuty:
    Type: AWS::GuardDuty::Detector
    Properties:
      Enable: true
      FindingPublishingFrequency: FIFTEEN_MINUTES

  # SNS topic for GuardDuty alerts
  SecurityAlertTopic:
    Type: AWS::SNS::Topic
    Properties:
      Subscription:
        - Protocol: email
          Endpoint: security@example.com
        - Protocol: sms
          Endpoint: "+1234567890"

  # EventBridge rule to forward GuardDuty findings
  GuardDutyRule:
    Type: AWS::Events::Rule
    Properties:
      EventPattern:
        source:
          - aws.guardduty
        detail-type:
          - GuardDuty Finding
        detail:
          severity:
            - 7  # High severity only
            - 8
      Targets:
        - Arn: !Ref SecurityAlertTopic
          Id: SecurityTeam
```

**GuardDuty detects:**
- Compromised EC2/Lambda instances
- Unusual API calls
- Cryptocurrency mining
- Data exfiltration attempts
- Malware detected

### 7.3 Cost Anomaly Detection

```yaml
Resources:
  CostAnomalyMonitor:
    Type: AWS::CE::AnomalyMonitor
    Properties:
      MonitorName: go3d-cost-anomaly
      MonitorType: DIMENSIONAL
      MonitorDimension: SERVICE

  CostAnomalySubscription:
    Type: AWS::CE::AnomalySubscription
    Properties:
      SubscriptionName: go3d-cost-alerts
      Frequency: IMMEDIATE
      MonitorArnList:
        - !GetAtt CostAnomalyMonitor.Arn
      Subscribers:
        - Type: EMAIL
          Address: admin@example.com
      Threshold: 100  # Alert if anomaly >$100
```

**Detects:**
- Unusual spending patterns
- Resource exhaustion attacks
- Misconfigured auto-scaling

---

## 8. Incident Response

### 8.1 Response Procedures

#### IR-1: Cost Overrun Attack Detected

```
Trigger: AWS Budget alert >80% of monthly budget
Severity: HIGH

1. IMMEDIATE ACTIONS (within 5 minutes)
   [ ] Check CloudWatch metrics for spike
   [ ] Identify source (Lambda, RDS, S3, etc.)
   [ ] If Lambda: Set reserved concurrency to 0 (disable)
   [ ] If RDS: Throttle connections via parameter group
   [ ] Review recent API calls in CloudTrail

2. INVESTIGATION (within 30 minutes)
   [ ] Review CloudWatch Logs for attack patterns
   [ ] Check API Gateway logs for source IPs
   [ ] Review GuardDuty findings
   [ ] Determine root cause (bug, attack, misconfiguration)

3. MITIGATION (within 2 hours)
   [ ] Block attacking IPs in WAF
   [ ] Enable rate limiting if not already configured
   [ ] Fix vulnerable code or config
   [ ] Re-enable Lambda with limits

4. POST-INCIDENT (within 24 hours)
   [ ] Document incident timeline
   [ ] Calculate total cost impact
   [ ] Contact AWS Support for billing review
   [ ] Update runbook with lessons learned
```

#### IR-2: Data Breach Suspected

```
Trigger: GuardDuty finding "UnauthorizedAccess:IAMUser/InstanceCredentialExfiltration"
Severity: CRITICAL

1. IMMEDIATE ACTIONS (within 5 minutes)
   [ ] Rotate all database credentials
   [ ] Revoke suspicious IAM session tokens
   [ ] Take database snapshot for forensics
   [ ] Enable CloudTrail logging (if not already)

2. CONTAINMENT (within 15 minutes)
   [ ] Disable compromised Lambda function
   [ ] Block suspicious IPs in security groups
   [ ] Isolate affected resources in separate VPC
   [ ] Notify stakeholders

3. INVESTIGATION (within 1 hour)
   [ ] Review CloudTrail for unauthorized API calls
   [ ] Check database audit logs
   [ ] Determine data accessed/exfiltrated
   [ ] Identify attack vector

4. RECOVERY (within 4 hours)
   [ ] Deploy patched Lambda function
   [ ] Restore database if corrupted
   [ ] Verify data integrity
   [ ] Re-enable service with enhanced security

5. POST-INCIDENT (within 48 hours)
   [ ] Notify affected users (if PII exposed)
   [ ] File breach report (if required by law)
   [ ] Conduct root cause analysis
   [ ] Implement additional security controls
```

#### IR-3: Database Failure

```
Trigger: RDS failover event or database unreachable
Severity: HIGH

1. IMMEDIATE ACTIONS (within 2 minutes)
   [ ] Verify automatic failover completed
   [ ] Check CloudWatch metrics for RDS
   [ ] If no failover: Manually failover to standby
   [ ] Notify users of potential degraded service

2. VERIFICATION (within 10 minutes)
   [ ] Test database connectivity
   [ ] Verify data integrity (sample queries)
   [ ] Check replication lag
   [ ] Monitor error rates in Lambda

3. ROOT CAUSE ANALYSIS (within 1 hour)
   [ ] Review RDS event logs
   [ ] Check for recent config changes
   [ ] Verify backup availability
   [ ] Determine if attack or failure

4. POST-INCIDENT (within 24 hours)
   [ ] Document incident timeline
   [ ] Calculate downtime
   [ ] Review backup/restore procedures
   [ ] Update runbook
```

### 8.2 Contact Information

```
Security Team:
  Primary: security@example.com
  Secondary: +1-234-567-8900 (SMS)

AWS Support:
  Business Support: Priority cases via console
  Enterprise Support: TAM direct line

Emergency Contacts:
  On-call Engineer: pagerduty.com/go3d
  CTO: cto@example.com
```

---

## 9. Compliance Considerations

### 9.1 Data Privacy (GDPR/CCPA)

| Requirement | Implementation | Status |
|-------------|---------------|--------|
| **Data encryption** | RDS encryption at rest, TLS in transit | 🎯 TO-BE |
| **Right to deletion** | API endpoint to delete player data | ⚠️ Not implemented |
| **Data portability** | Export game history as JSON | ✅ Easy to add |
| **Audit trail** | CloudTrail logs all API access | 🎯 TO-BE |
| **Data minimization** | Only collect game moves, no PII | ✅ Already compliant |

**Note:** Go-3D does not collect personally identifiable information (PII) beyond optional player names. Compliance burden is LOW.

### 9.2 PCI DSS (If Adding Payments)

**Not applicable unless monetization added.**

If adding paid features:
- [ ] Use Stripe/PayPal (PCI compliant processor)
- [ ] Never store credit card data
- [ ] Use tokenization
- [ ] Implement strong authentication

---

## 10. Recommendations Summary

### 10.1 Critical (Implement Before Production)

| # | Recommendation | Effort | Cost | Risk Reduction |
|---|---------------|--------|------|----------------|
| 1 | API Gateway throttling | 1 hour | FREE | 99% cost attack |
| 2 | Lambda reserved concurrency | 30 min | FREE | Caps max cost |
| 3 | AWS Budget alerts | 30 min | FREE | Cost visibility |
| 4 | Secrets Manager for DB password | 1 day | $0.40/mo | 90% credential theft |
| 5 | IAM least privilege | 2 days | FREE | 90% lateral movement |
| 6 | VPC with private subnets | 2 days | $45/mo (NAT) | 95% blast radius |
| 7 | Database encryption | 1 hour | FREE | Data protection |
| 8 | Automated backups (30 days) | 1 hour | Included | Data loss prevention |
| 9 | WAF basic rules | 1 day | $10/mo | 95% DDoS |
| 10 | CloudWatch alarms | 2 hours | FREE | Incident detection |

**Total implementation time:** 7-8 days
**Total ongoing cost:** ~$55/month (NAT $45 + WAF $10)

### 10.2 High Priority (Implement Within 1 Month)

| # | Recommendation | Effort | Cost | Benefit |
|---|---------------|--------|------|---------|
| 11 | GuardDuty threat detection | 1 hour | $30/mo | Automated threat detection |
| 12 | CloudTrail logging | 1 hour | $5/mo | Audit trail |
| 13 | Multi-AZ RDS | Config change | +30% DB cost | 99.9% availability |
| 14 | Immutable backups (Vault Lock) | 2 hours | FREE | Ransomware protection |
| 15 | Input validation (board size limits) | 1 day | FREE | Algorithm complexity attacks |
| 16 | Application-level rate limiting | 2 days | FREE | Fine-grained control |
| 17 | Database read replica | Config change | 2x DB cost | Scale reads, failover |
| 18 | CloudFront CDN | 4 hours | $1/GB | DDoS protection (L3/L4) |

### 10.3 Medium Priority (Implement Within 3 Months)

| # | Recommendation | Effort | Benefit |
|---|---------------|--------|---------|
| 19 | Secrets rotation (90 days) | 2 days | Credential hygiene |
| 20 | Penetration testing | 1 week | Validate security |
| 21 | Security audit | 1 week | Identify gaps |
| 22 | Incident response drills | 1 day | Preparedness |
| 23 | CAPTCHA on /new endpoint | 1 day | Bot protection |
| 24 | Database query timeouts | 1 hour | DoS prevention |
| 25 | Connection pooling optimization | 1 day | Resource efficiency |

### 10.4 Low Priority (Nice to Have)

| # | Recommendation | Effort | Benefit |
|---|---------------|--------|---------|
| 26 | AWS Config compliance rules | 1 week | Automated compliance |
| 27 | AWS Security Hub | 2 days | Centralized security view |
| 28 | VPC Flow Logs | 1 hour | Network forensics |
| 29 | KMS custom keys | 1 day | Fine-grained encryption control |
| 30 | SOC 2 audit | 3 months | Enterprise credibility |

---

## Appendix A: Attack Trees

### Cost Overrun Attack Tree

```
┌─────────────────────────────────────────┐
│      Cause Cost Overrun Attack          │
│         (Attacker Goal)                 │
└────────────────┬────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
┌───────▼──────┐   ┌──────▼────────┐
│Lambda Exhaust│   │DB Exhaust     │
└───────┬──────┘   └──────┬────────┘
        │                 │
   ┌────┴────┐       ┌────┴────┐
   │         │       │         │
┌──▼───┐ ┌──▼───┐ ┌─▼──┐ ┌────▼────┐
│Request│ │Algo  │ │Query│ │Storage  │
│Flood  │ │Cmplx │ │Bomb │ │Exhaust  │
└───────┘ └──────┘ └────┘ └─────────┘
   │         │        │        │
   ▼         ▼        ▼        ▼
 Easy      Med      Med      Hard
```

### Data Loss Attack Tree

```
┌─────────────────────────────────────────┐
│        Cause Data Loss Attack           │
│         (Attacker Goal)                 │
└────────────────┬────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
┌───────▼──────┐   ┌──────▼────────┐
│Destroy Data  │   │Corrupt Data   │
└───────┬──────┘   └──────┬────────┘
        │                 │
   ┌────┴────┐       ┌────┴────┐
   │         │       │         │
┌──▼───┐ ┌──▼───┐ ┌─▼──┐ ┌────▼────┐
│DELETE│ │Ransom│ │SQL │ │App Bug  │
│SQL   │ │ware  │ │Inj │ │Exploit  │
└───────┘ └──────┘ └────┘ └─────────┘
   │         │        │        │
   ▼         ▼        ▼        ▼
 Hard     Med      Hard      Low
(IAM)   (Creds)  (Paramz)  (Tests)
```

---

## Appendix B: Security Tools

### Automated Security Scanning

```yaml
# .github/workflows/security-scan.yml
name: Security Scan

on:
  push:
    branches: [main]
  pull_request:
  schedule:
    - cron: '0 0 * * 0'  # Weekly

jobs:
  dependency-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Snyk security scan
        uses: snyk/actions/scala@master
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          args: --severity-threshold=high

  sast:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Semgrep SAST
        run: |
          pip install semgrep
          semgrep --config=p/scala --config=p/security-audit --sarif -o results.sarif
      - name: Upload SARIF results
        uses: github/codeql-action/upload-sarif@v2
        with:
          sarif_file: results.sarif

  secrets-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Gitleaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Manual Penetration Testing Checklist

- [ ] SQL injection (all endpoints)
- [ ] XSS (game names, player names)
- [ ] CSRF (state-changing operations)
- [ ] Authentication bypass
- [ ] Authorization bypass
- [ ] Rate limiting effectiveness
- [ ] DoS resistance
- [ ] Credential theft vectors
- [ ] Session hijacking
- [ ] Information disclosure

---

**Document Status:** Draft - Pending Security Review
**Next Review Date:** Before production deployment
**Owner:** Security Team

**END OF SECURITY ASSESSMENT**
