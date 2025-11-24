# AWS Deployment Plan: Go-3D Server

**Date:** November 24, 2025
**Version:** 0.7.16

---

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Cloud-Readiness Requirements](#2-cloud-readiness-requirements)
3. [Deployment Options](#3-deployment-options)
   - [3.6 Lambda Native Compilation Deep Dive](#36-lambda-native-compilation-deep-dive)
4. [Comparison Matrix](#4-comparison-matrix)
5. [Recommended Architecture](#5-recommended-architecture)
6. [Implementation Roadmap](#6-implementation-roadmap)

📘 **See Also:** [Multi-Cloud Strategy](MULTI_CLOUD_STRATEGY.md) - Keep your deployment portable across AWS, GCP, and Azure

---

## 1. Current State Analysis

### 1.1 Cloud Blockers

| Issue | Current Implementation | Cloud Impact |
|-------|----------------------|--------------|
| **In-Memory State** | `Games` and `Players` use mutable `Map` singletons | Data loss on restart, no horizontal scaling |
| **Local File Storage** | `FileIO` writes JSON to local filesystem | Not shared across instances |
| **No External Database** | All persistence is file-based | Can't scale, no backup strategy |
| **Stateful Design** | Game state tied to single process | Single point of failure |
| **No Distributed Sessions** | Auth tokens in memory only | Lost on restart |

### 1.2 Cloud-Ready Features (Already Present)

| Feature | Status |
|---------|--------|
| Docker support | Dockerfile with multi-stage build |
| Health check endpoint | `GET /health` returns 200 |
| Environment variables | `PORT`, `SAVE_DIR` configurable |
| Stateless HTTP | http4s with Cats Effect IO |
| JSON serialization | Circe encoders/decoders ready |

---

## 2. Cloud-Readiness Requirements

### 2.1 Code Changes Required

#### Phase 1: Externalize State (Required for all options)

```
Estimated Effort: 2-3 weeks
```

| Change | Description | Priority |
|--------|-------------|----------|
| Database integration | Replace `Games`/`Players` singletons with database | Critical |
| Redis/ElastiCache | Session state and auth tokens | Critical |
| Remove file I/O | Replace `FileIO` with database persistence | Critical |
| Thread-safe state | Use `Ref[IO, Map[...]]` if keeping any in-memory state | High |
| Configuration | Externalize DB credentials via env vars or Secrets Manager | High |

#### Phase 2: Scalability Enhancements (Recommended)

```
Estimated Effort: 1-2 weeks additional
```

| Change | Description | Priority |
|--------|-------------|----------|
| WebSocket support | Real-time updates via API Gateway WebSocket | Medium |
| Connection pooling | HikariCP for database connections | Medium |
| Graceful shutdown | Proper signal handling for container orchestration | Medium |
| Structured logging | JSON logging for CloudWatch integration | Low |
| Metrics | Micrometer/Prometheus for observability | Low |

### 2.2 Database Schema

```sql
-- Games table
CREATE TABLE games (
    id VARCHAR(32) PRIMARY KEY,
    size INT NOT NULL,
    goban JSONB NOT NULL,
    moves JSONB NOT NULL,
    captures JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Players table
CREATE TABLE players (
    game_id VARCHAR(32) NOT NULL,
    color CHAR(1) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (game_id, color),
    FOREIGN KEY (game_id) REFERENCES games(id)
);

-- Index for token lookups
CREATE INDEX idx_players_token ON players(token);
```

---

## 3. Deployment Options

### Option A: Single EC2 Instance

**Architecture:**
```
┌─────────────────────────────────────────────────────┐
│                      AWS Cloud                       │
│  ┌─────────────┐    ┌─────────────┐                 │
│  │   Route 53  │───▶│     ALB     │                 │
│  └─────────────┘    └──────┬──────┘                 │
│                            │                         │
│                     ┌──────▼──────┐                 │
│                     │  EC2 (t3)   │                 │
│                     │  Go-3D App  │                 │
│                     └──────┬──────┘                 │
│                            │                         │
│                     ┌──────▼──────┐                 │
│                     │    EBS     │                 │
│                     │  (storage)  │                 │
│                     └─────────────┘                 │
└─────────────────────────────────────────────────────┘
```

**Characteristics:**
- Minimal code changes (can use existing file-based storage)
- Single point of failure
- No horizontal scaling
- Manual deployments

**AWS Services:**
- EC2 t3.small/medium
- EBS gp3 volume
- Application Load Balancer
- Route 53 (DNS)
- ACM (SSL certificate)

**Estimated Monthly Cost:** $30-60/month

---

### Option B: ECS Fargate with RDS

**Architecture:**
```
┌──────────────────────────────────────────────────────────────┐
│                         AWS Cloud                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐       │
│  │   Route 53  │───▶│     ALB     │───▶│ ECS Fargate │       │
│  └─────────────┘    └─────────────┘    │  (1-4 tasks)│       │
│                                         └──────┬──────┘       │
│                                                │              │
│         ┌──────────────────────────────────────┤              │
│         │                                      │              │
│  ┌──────▼──────┐                      ┌───────▼───────┐      │
│  │ ElastiCache │                      │  RDS Aurora   │      │
│  │   (Redis)   │                      │  PostgreSQL   │      │
│  │  (sessions) │                      │   (db.t3)     │      │
│  └─────────────┘                      └───────────────┘      │
└──────────────────────────────────────────────────────────────┘
```

**Characteristics:**
- Fully managed containers
- Auto-scaling capability
- No server management
- Requires code changes for database

**AWS Services:**
- ECS Fargate (0.25-1 vCPU, 0.5-2 GB)
- RDS Aurora PostgreSQL Serverless v2
- ElastiCache Redis (cache.t3.micro)
- Application Load Balancer
- ECR (container registry)
- CloudWatch (logging/monitoring)
- Secrets Manager

**Estimated Monthly Cost:** $80-200/month

---

### Option C: EKS (Kubernetes)

**Architecture:**
```
┌─────────────────────────────────────────────────────────────────┐
│                          AWS Cloud                               │
│  ┌─────────────┐    ┌─────────────┐    ┌───────────────────┐    │
│  │   Route 53  │───▶│ AWS LB Ctrl │───▶│   EKS Cluster     │    │
│  └─────────────┘    └─────────────┘    │  ┌─────────────┐  │    │
│                                         │  │  Go-3D Pods │  │    │
│                                         │  │  (2-10)     │  │    │
│                                         │  └──────┬──────┘  │    │
│                                         └─────────┼─────────┘    │
│                                                   │              │
│         ┌─────────────────────────────────────────┤              │
│         │                                         │              │
│  ┌──────▼──────┐                         ┌───────▼───────┐      │
│  │ ElastiCache │                         │  RDS Aurora   │      │
│  │   Cluster   │                         │   Cluster     │      │
│  └─────────────┘                         └───────────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

**Characteristics:**
- Full Kubernetes ecosystem
- Maximum flexibility and control
- Complex setup and management
- Best for multi-service architectures
- Overkill for single service

**AWS Services:**
- EKS (managed Kubernetes)
- EC2 node groups or Fargate
- RDS Aurora PostgreSQL
- ElastiCache Redis cluster
- AWS Load Balancer Controller
- ECR
- CloudWatch Container Insights

**Estimated Monthly Cost:** $200-500/month

---

### Option D: App Runner

**Architecture:**
```
┌─────────────────────────────────────────────────────────────┐
│                        AWS Cloud                             │
│  ┌─────────────┐    ┌─────────────────┐                     │
│  │   Route 53  │───▶│   App Runner    │                     │
│  └─────────────┘    │   (auto-managed)│                     │
│                      └────────┬────────┘                     │
│                               │                              │
│         ┌─────────────────────┴─────────────────┐           │
│         │                                       │           │
│  ┌──────▼──────┐                       ┌───────▼───────┐   │
│  │ ElastiCache │                       │  RDS Aurora   │   │
│  │   (Redis)   │                       │  Serverless   │   │
│  └─────────────┘                       └───────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**Characteristics:**
- Simplest deployment model
- Automatic scaling and load balancing
- Limited customization
- Good for straightforward HTTP services

**AWS Services:**
- App Runner
- RDS Aurora Serverless v2
- ElastiCache Redis
- ECR
- Secrets Manager

**Estimated Monthly Cost:** $50-150/month

---

### Option E: Lambda + API Gateway (Serverless)

**Architecture:**
```
┌─────────────────────────────────────────────────────────────────┐
│                          AWS Cloud                               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐      │
│  │   Route 53  │───▶│ API Gateway │───▶│  Lambda Funcs   │      │
│  └─────────────┘    │  (HTTP API) │    │  ┌───────────┐  │      │
│                      └─────────────┘    │  │ newGame   │  │      │
│                                         │  │ register  │  │      │
│                                         │  │ status    │  │      │
│                                         │  │ setMove   │  │      │
│                                         │  │ pass      │  │      │
│                                         │  └─────┬─────┘  │      │
│                                         └───────┼────────┘      │
│                                                 │               │
│         ┌───────────────────────────────────────┤               │
│         │                                       │               │
│  ┌──────▼──────┐                       ┌───────▼───────┐       │
│  │  DynamoDB   │                       │   DynamoDB    │       │
│  │  (sessions) │                       │    (games)    │       │
│  └─────────────┘                       └───────────────┘       │
│                                                                 │
│  ┌─────────────────────────────────────────────────────┐       │
│  │              API Gateway WebSocket                   │       │
│  │              (real-time updates)                     │       │
│  └─────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

**Characteristics:**
- Pay per request
- Auto-scales to zero
- Cold start latency (JVM problematic)
- Requires significant refactoring
- Best for sporadic traffic

**AWS Services:**
- API Gateway HTTP API
- API Gateway WebSocket API
- Lambda (GraalVM native or increased memory for JVM)
- DynamoDB (games and sessions)
- CloudWatch

**Estimated Monthly Cost:** $5-100/month (highly variable with traffic)

---

### 3.6 Lambda Native Compilation Deep Dive

⚠️ **Critical for Lambda Success: Addressing JVM Cold Start Issues**

The primary concern with Lambda (Option E) is JVM cold start latency of **2-5 seconds**, which is unacceptable for user-facing APIs. Native compilation solves this problem.

#### Cold Start Comparison

| Approach | Cold Start | Memory | Cost/Invocation | Difficulty |
|----------|-----------|--------|-----------------|------------|
| **Standard JVM** | 2,000-5,000ms | 512-1024MB | $0.0000002083/ms | Easy |
| **SnapStart** | 600-800ms | 512-1024MB | $0.0000002083/ms | Trivial |
| **GraalVM Native** | 50-200ms | 128-256MB | $0.0000000833/ms | Medium |
| **Scala Native** | 10-50ms | 64-128MB | $0.0000000417/ms | High (not viable) |

#### Recommendation: GraalVM Native Image ✅

**Why GraalVM:**
- **10-25x faster cold starts** (2-5s → 50-200ms)
- **60% cost reduction** due to lower memory and faster execution
- **Proven compatibility** with http4s, Cats Effect, Circe
- **2-3 week implementation** vs 8-12 weeks for Scala Native
- **Minimal code changes** - mostly build configuration

**Implementation Highlights:**

```scala
// build.sbt - Enable native image
enablePlugins(NativeImagePlugin)

nativeImageOptions ++= Seq(
  "--no-fallback",
  "--initialize-at-build-time",
  "-O3",
  "--gc=serial",
  "-H:+ReportExceptionStackTraces"
)
```

**Expected Performance:**
- Cold start: **150ms** (vs 3,000ms JVM)
- Memory: **256MB** (vs 512MB JVM)
- Warm latency: **15ms** (vs 30ms JVM)
- **Monthly cost reduction: 60-70%**

**Cost Impact at 50,000 games/month:**

| Component | JVM | GraalVM Native | Savings |
|-----------|-----|----------------|---------|
| Compute | $120 | $25 | $95 |
| Memory | $25 | $12 | $13 |
| Provisioned concurrency | $30 (needed) | $0 (not needed) | $30 |
| **Total** | **$175** | **$37** | **$138/mo (79% savings)** |

**Phased Approach:**

1. **Week 1:** Enable **SnapStart** (AWS feature) - Get 60% improvement instantly with zero code changes
2. **Week 2-3:** Build **GraalVM Native Image** - Get 90% improvement
3. **Week 4:** Profile-guided optimization - Fine-tune last 10%

**See [LAMBDA_NATIVE_COMPILATION.md](LAMBDA_NATIVE_COMPILATION.md) for complete implementation guide including:**
- Dependency compatibility analysis
- Build configuration examples
- Reflection configuration for Circe
- Lambda handler implementation
- Performance benchmarks
- Testing strategy
- Migration risks and mitigations

---

## 4. Comparison Matrix

### 4.1 Effort to Implement

| Option | Code Changes | Infrastructure | DevOps Setup | Total Effort |
|--------|--------------|----------------|--------------|--------------|
| **A: EC2** | Minimal | Low | Low | **1-2 weeks** |
| **B: ECS Fargate** | Medium | Medium | Medium | **3-4 weeks** |
| **C: EKS** | Medium | High | High | **6-8 weeks** |
| **D: App Runner** | Medium | Low | Low | **2-3 weeks** |
| **E: Lambda (Native)** | Medium | Medium | Medium | **3-4 weeks** |

### 4.2 Monthly Cost Estimate (Low Traffic: ~1000 games/month)

| Option | Compute | Database | Other | Total |
|--------|---------|----------|-------|-------|
| **A: EC2** | $15-30 | $0 (EBS) | $15 (ALB) | **$30-60** |
| **B: ECS Fargate** | $30-50 | $30-80 | $30 | **$90-160** |
| **C: EKS** | $75-150 | $50-100 | $75 | **$200-400** |
| **D: App Runner** | $25-50 | $30-80 | $15 | **$70-145** |
| **E: Lambda (Native)** | $3-10 | $25 (DDB) | $5 | **$33-40** |

### 4.3 Monthly Cost Estimate (High Traffic: ~50,000 games/month)

| Option | Compute | Database | Other | Total |
|--------|---------|----------|-------|-------|
| **A: EC2** | N/A | N/A | N/A | **Can't scale** |
| **B: ECS Fargate** | $100-200 | $100-200 | $50 | **$250-450** |
| **C: EKS** | $150-300 | $150-250 | $100 | **$400-650** |
| **D: App Runner** | $100-200 | $100-200 | $30 | **$230-430** |
| **E: Lambda (Native)** | $25-60 | $50-100 | $20 | **$95-180** |

### 4.4 Performance & Scalability

| Option | Latency | Max Concurrent Games | Auto-Scaling | Availability |
|--------|---------|---------------------|--------------|--------------|
| **A: EC2** | Low (5-10ms) | ~100-500 | None | Single AZ |
| **B: ECS Fargate** | Low (5-15ms) | ~10,000+ | Automatic | Multi-AZ |
| **C: EKS** | Low (5-15ms) | ~100,000+ | Advanced | Multi-AZ/Region |
| **D: App Runner** | Low (10-20ms) | ~5,000+ | Automatic | Multi-AZ |
| **E: Lambda (Native)** | Low (15-150ms*) | Unlimited | Instant | Multi-AZ |

*Native image cold start: 50-200ms. JVM cold start: 2-5s (use SnapStart or provisioned concurrency if not using native).

### 4.5 Overall Rating (1-5 stars)

| Option | Effort | Cost (Low) | Cost (High) | Scalability | Recommended For |
|--------|--------|------------|-------------|-------------|-----------------|
| **A: EC2** | ★★★★★ | ★★★★★ | N/A | ★☆☆☆☆ | Hobby/Dev |
| **B: ECS Fargate** | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★★☆ | **Production** |
| **C: EKS** | ★★☆☆☆ | ★★☆☆☆ | ★★★☆☆ | ★★★★★ | Enterprise/Multi-service |
| **D: App Runner** | ★★★★☆ | ★★★★☆ | ★★★★☆ | ★★★☆☆ | **Quick Start** |
| **E: Lambda (Native)** | ★★★☆☆ | ★★★★★ | ★★★★★ | ★★★★★ | **Low Cost/Variable Traffic** |

---

## 5. Recommended Architecture

### 5.1 For Most Use Cases: Option B (ECS Fargate)

**Rationale:**
- Best balance of effort, cost, and scalability
- Production-ready with minimal operational overhead
- Familiar Docker-based workflow
- Easy to start small and scale up

### 5.2 For Quick MVP: Option D (App Runner)

**Rationale:**
- Fastest time to production
- Minimal infrastructure management
- Good enough for initial launch
- Easy migration to ECS later

### 5.3 For Low Cost / Variable Traffic: Option E (Lambda Native)

**Rationale:**
- **Lowest cost option** ($33-40/month low traffic, $95-180/month high traffic)
- Excellent for early-stage or hobby projects
- Scales to zero when not in use
- Native image eliminates cold start concerns
- Perfect for sporadic/unpredictable traffic patterns
- **Recommended if cost is the primary concern**

**Trade-offs:**
- More complex deployment (native compilation)
- Platform lock-in to Lambda
- Limited to 15-minute execution time (not an issue for API)

### 5.4 Detailed Architecture (Option B)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AWS Cloud (Multi-AZ)                            │
│                                                                              │
│  ┌──────────────┐                                                           │
│  │   Route 53   │  DNS with health checks                                   │
│  └───────┬──────┘                                                           │
│          │                                                                   │
│  ┌───────▼──────┐                                                           │
│  │     ACM      │  SSL/TLS certificates                                     │
│  └───────┬──────┘                                                           │
│          │                                                                   │
│  ┌───────▼──────────────────────────────────────────────────────────────┐  │
│  │                    Application Load Balancer                          │  │
│  │                    (with WAF for security)                            │  │
│  └───────────────────────────┬──────────────────────────────────────────┘  │
│                              │                                              │
│  ┌───────────────────────────▼──────────────────────────────────────────┐  │
│  │                         ECS Cluster                                   │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐       │  │
│  │  │  Go-3D Task     │  │  Go-3D Task     │  │  Go-3D Task     │       │  │
│  │  │  (Fargate)      │  │  (Fargate)      │  │  (Fargate)      │       │  │
│  │  │  AZ-a           │  │  AZ-b           │  │  AZ-c           │       │  │
│  │  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘       │  │
│  └───────────┼────────────────────┼────────────────────┼────────────────┘  │
│              │                    │                    │                    │
│              └────────────────────┼────────────────────┘                    │
│                                   │                                         │
│         ┌─────────────────────────┴─────────────────────────┐              │
│         │                                                   │              │
│  ┌──────▼──────┐                                   ┌───────▼───────┐      │
│  │ ElastiCache │                                   │  RDS Aurora   │      │
│  │   Redis     │                                   │  PostgreSQL   │      │
│  │             │                                   │  Serverless   │      │
│  │  Sessions   │                                   │               │      │
│  │  Auth tokens│                                   │  Games data   │      │
│  │  Rate limits│                                   │  Players      │      │
│  └─────────────┘                                   └───────────────┘      │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │                         Supporting Services                           │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │ │
│  │  │     ECR     │  │  Secrets    │  │ CloudWatch  │  │   X-Ray     │  │ │
│  │  │  Container  │  │  Manager    │  │   Logs &    │  │  Tracing    │  │ │
│  │  │  Registry   │  │  (DB creds) │  │   Metrics   │  │             │  │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Implementation Roadmap

### Phase 1: Code Preparation (Weeks 1-3)

#### Week 1: Database Integration
- [ ] Add Doobie or Skunk (Scala PostgreSQL library) dependency
- [ ] Create database schema and migrations
- [ ] Implement `GameRepository` trait with PostgreSQL backend
- [ ] Implement `PlayerRepository` trait with PostgreSQL backend
- [ ] Add connection pooling (HikariCP)

#### Week 2: Session Management
- [ ] Add Redis4Cats dependency
- [ ] Implement `SessionStore` with Redis backend
- [ ] Move auth token storage to Redis
- [ ] Add token expiration handling

#### Week 3: Testing & Refinement
- [ ] Update tests with TestContainers
- [ ] Integration tests with real DB
- [ ] Load testing with Gatling
- [ ] Fix any issues found

### Phase 2: Infrastructure Setup (Week 4)

#### Terraform/CDK Resources
- [ ] VPC with public/private subnets
- [ ] RDS Aurora Serverless cluster
- [ ] ElastiCache Redis cluster
- [ ] ECS cluster and task definitions
- [ ] ALB and target groups
- [ ] Security groups
- [ ] IAM roles and policies
- [ ] Secrets Manager secrets
- [ ] ECR repository

### Phase 3: CI/CD Pipeline (Week 5)

#### GitHub Actions / GitLab CI
```yaml
# Example GitHub Actions workflow
name: Deploy to AWS
on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build and test
        run: sbt test
      - name: Build Docker image
        run: docker build -t go-3d .
      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_URL
          docker tag go-3d:latest $ECR_URL/go-3d:$GITHUB_SHA
          docker push $ECR_URL/go-3d:$GITHUB_SHA
      - name: Deploy to ECS
        run: |
          aws ecs update-service --cluster go-3d --service go-3d --force-new-deployment
```

### Phase 4: Production Deployment (Week 6)

- [ ] Deploy to staging environment
- [ ] Run smoke tests
- [ ] Configure monitoring alerts
- [ ] Deploy to production
- [ ] Monitor for issues
- [ ] Document runbooks

---

## 7. Cost Optimization Tips

### Development Environment
- Use single-AZ deployments
- Schedule non-production to stop overnight
- Use spot instances for dev/test ECS tasks

### Production Environment
- Right-size Fargate tasks (start small, scale up)
- Use Aurora Serverless v2 (scales to zero ACU)
- Enable S3 lifecycle policies for logs
- Use Reserved Capacity for predictable workloads (1-year commit = 30% savings)

### Monitoring Costs
- Set up AWS Budgets alerts
- Review Cost Explorer weekly
- Tag all resources for cost allocation

---

## 8. Security Checklist

- [ ] VPC with private subnets for compute and database
- [ ] Security groups with minimal required access
- [ ] Secrets Manager for database credentials
- [ ] IAM roles with least privilege
- [ ] WAF rules on ALB
- [ ] Enable RDS encryption at rest
- [ ] Enable Redis encryption in transit
- [ ] CloudTrail for audit logging
- [ ] GuardDuty for threat detection

---

## Appendix A: Sample Terraform Configuration

```hcl
# main.tf - ECS Fargate deployment

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.0.0"

  name = "go-3d-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["us-east-1a", "us-east-1b"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24"]

  enable_nat_gateway = true
}

module "ecs" {
  source  = "terraform-aws-modules/ecs/aws"
  version = "5.0.0"

  cluster_name = "go-3d"

  fargate_capacity_providers = {
    FARGATE = {
      default_capacity_provider_strategy = {
        weight = 100
      }
    }
  }
}

resource "aws_ecs_task_definition" "go3d" {
  family                   = "go-3d"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512

  container_definitions = jsonencode([{
    name  = "go-3d"
    image = "${aws_ecr_repository.go3d.repository_url}:latest"

    portMappings = [{
      containerPort = 6030
      hostPort      = 6030
    }]

    environment = [
      { name = "PORT", value = "6030" },
      { name = "DB_HOST", value = module.aurora.cluster_endpoint }
    ]

    secrets = [{
      name      = "DB_PASSWORD"
      valueFrom = aws_secretsmanager_secret.db_password.arn
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = "/ecs/go-3d"
        awslogs-region        = "us-east-1"
        awslogs-stream-prefix = "ecs"
      }
    }
  }])
}
```

---

## Appendix B: Code Changes Summary

### New Dependencies (build.sbt)

```scala
// Database
libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core"     % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-hikari"   % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC4",
)

// Redis
libraryDependencies += "dev.profunktor" %% "redis4cats-effects" % "1.5.0"

// Configuration
libraryDependencies += "com.github.pureconfig" %% "pureconfig-core" % "0.17.4"
```

### New Configuration (application.conf)

```hocon
database {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://"${?DB_HOST}":5432/go3d"
  user = ${?DB_USER}
  password = ${?DB_PASSWORD}
  pool-size = 10
}

redis {
  host = ${?REDIS_HOST}
  port = 6379
}

server {
  port = ${?PORT}
  host = "0.0.0.0"
}
```

---

*Document prepared for Go-3D AWS deployment planning*
