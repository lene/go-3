# Multi-Cloud Deployment Strategy: Go-3D Server

**Cloud Portability Without Compromising on Features**

---

## Executive Summary

This document outlines a strategy to deploy Go-3D to AWS Lambda while maintaining maximum portability to GCP and Azure. The key principle: **"Container-first, cloud-agnostic services, abstraction layers everywhere."**

### Core Philosophy

> Deploy fast to one cloud, but architect for three.

**Target:** Deploy to Lambda in 3-4 weeks, maintain ability to switch clouds in 1-2 weeks.

---

## Table of Contents

1. [Multi-Cloud Architecture Principles](#1-multi-cloud-architecture-principles)
2. [Service Abstraction Layer Design](#2-service-abstraction-layer-design)
3. [Container-First Deployment Strategy](#3-container-first-deployment-strategy)
4. [Cloud Service Equivalents](#4-cloud-service-equivalents)
5. [Implementation Plan for Lambda](#5-implementation-plan-for-lambda)
6. [Migration Paths](#6-migration-paths)
7. [Testing Strategy](#7-testing-strategy)
8. [Infrastructure as Code](#8-infrastructure-as-code)

---

## 1. Multi-Cloud Architecture Principles

### 1.1 The Portability Pyramid

```
┌─────────────────────────────────────────┐
│     Business Logic (Go-3D Game)         │ ← 100% portable
├─────────────────────────────────────────┤
│   Service Interfaces (Traits)           │ ← 100% portable
├─────────────────────────────────────────┤
│   Cloud Adapters (Implementations)      │ ← Swap per cloud
├─────────────────────────────────────────┤
│   Standard Protocols (SQL, Redis, HTTP) │ ← 100% portable
├─────────────────────────────────────────┤
│   Container Runtime (Docker)            │ ← 100% portable
└─────────────────────────────────────────┘
```

### 1.2 Design Principles

| Principle | Description | Impact on Portability |
|-----------|-------------|---------------------|
| **Container-first** | All deployments use Docker containers | Same artifact works everywhere |
| **Interface-driven** | Cloud services behind Scala traits | Swap implementations per cloud |
| **Standard protocols** | PostgreSQL, Redis, HTTP - no proprietary APIs | Works on any cloud |
| **Config-driven** | Environment variables for cloud differences | No code changes to switch |
| **Avoid cloud SDKs** | Don't use AWS SDK, Azure SDK in business logic | Eliminates vendor coupling |
| **Open source stack** | Prefer OSS over managed services when possible | Ultimate portability |

### 1.3 Portability Spectrum

```
Most Portable                              Least Portable
│                                                        │
├────────┬──────────┬──────────┬──────────┬────────────┤
Docker   PostgreSQL  Redis      Lambda     DynamoDB
         on RDS      on managed  Container
                     service
```

**Strategy:** Stay left on this spectrum. Use managed services only when they have equivalents across clouds.

---

## 2. Service Abstraction Layer Design

### 2.1 Core Abstractions

Create Scala traits for all external dependencies:

```scala
// src/main/scala/go3d/infra/abstractions.scala

package go3d.infra

import cats.effect.IO
import go3d.{Game, Color}

// Database abstraction
trait GameRepository {
  def save(gameId: String, game: Game): IO[Unit]
  def load(gameId: String): IO[Option[Game]]
  def listActive(): IO[List[String]]
  def archive(gameId: String): IO[Unit]
}

// Cache/Session abstraction
trait SessionStore {
  def set(key: String, value: String, ttlSeconds: Int): IO[Unit]
  def get(key: String): IO[Option[String]]
  def delete(key: String): IO[Unit]
  def exists(key: String): IO[Boolean]
}

// Secrets abstraction
trait SecretManager {
  def getSecret(name: String): IO[String]
  def listSecrets(): IO[List[String]]
}

// Logging abstraction (use slf4j - already cloud-agnostic)
// Metrics abstraction
trait MetricsCollector {
  def incrementCounter(name: String, tags: Map[String, String]): IO[Unit]
  def recordLatency(name: String, millis: Long, tags: Map[String, String]): IO[Unit]
  def recordGauge(name: String, value: Double, tags: Map[String, String]): IO[Unit]
}

// Object storage abstraction (for game archives, replays, etc.)
trait ObjectStore {
  def put(bucket: String, key: String, content: Array[Byte]): IO[Unit]
  def get(bucket: String, key: String): IO[Option[Array[Byte]]]
  def delete(bucket: String, key: String): IO[Unit]
  def listKeys(bucket: String, prefix: String): IO[List[String]]
}
```

### 2.2 Implementation Hierarchy

```
go3d/
├── domain/              # Pure business logic (100% portable)
│   ├── Game.scala
│   ├── Goban.scala
│   └── Move.scala
│
├── infra/
│   ├── abstractions/    # Interfaces (100% portable)
│   │   ├── GameRepository.scala
│   │   ├── SessionStore.scala
│   │   ├── SecretManager.scala
│   │   └── MetricsCollector.scala
│   │
│   ├── postgres/        # PostgreSQL impl (95% portable)
│   │   └── PostgresGameRepository.scala
│   │
│   ├── redis/           # Redis impl (95% portable)
│   │   └── RedisSessionStore.scala
│   │
│   └── cloud/           # Cloud-specific (swap per cloud)
│       ├── aws/
│       │   ├── AWSSecretManager.scala
│       │   ├── CloudWatchMetrics.scala
│       │   └── S3ObjectStore.scala
│       │
│       ├── gcp/
│       │   ├── GCPSecretManager.scala
│       │   ├── CloudMonitoringMetrics.scala
│       │   └── GCSObjectStore.scala
│       │
│       └── azure/
│           ├── AzureKeyVaultSecrets.scala
│           ├── AzureMonitorMetrics.scala
│           └── BlobStorageObjectStore.scala
│
└── server/
    ├── GoServer.scala       # Wires up dependencies
    └── http4s/
        └── GoHttpService.scala
```

### 2.3 Dependency Injection Pattern

```scala
// src/main/scala/go3d/server/ServerConfig.scala

package go3d.server

import cats.effect.{IO, Resource}
import go3d.infra._

case class ServerDependencies(
  gameRepository: GameRepository,
  sessionStore: SessionStore,
  secretManager: SecretManager,
  metrics: MetricsCollector,
  objectStore: ObjectStore
)

object ServerDependencies {

  // Factory that selects implementations based on environment
  def create(cloudProvider: String): Resource[IO, ServerDependencies] = {
    cloudProvider.toLowerCase match {
      case "aws" => createAWS()
      case "gcp" => createGCP()
      case "azure" => createAzure()
      case "local" => createLocal() // For development
      case _ => throw new IllegalArgumentException(s"Unknown cloud: $cloudProvider")
    }
  }

  private def createAWS(): Resource[IO, ServerDependencies] = {
    for {
      secrets <- AWSSecretManager.resource()
      dbConfig <- Resource.eval(secrets.getSecret("db-config"))
      gameRepo <- PostgresGameRepository.resource(dbConfig)
      sessions <- RedisSessionStore.resource() // Redis works everywhere
      metrics <- CloudWatchMetrics.resource()
      storage <- S3ObjectStore.resource()
    } yield ServerDependencies(gameRepo, sessions, secrets, metrics, storage)
  }

  private def createGCP(): Resource[IO, ServerDependencies] = {
    for {
      secrets <- GCPSecretManager.resource()
      dbConfig <- Resource.eval(secrets.getSecret("db-config"))
      gameRepo <- PostgresGameRepository.resource(dbConfig)
      sessions <- RedisSessionStore.resource() // Same Redis
      metrics <- CloudMonitoringMetrics.resource()
      storage <- GCSObjectStore.resource()
    } yield ServerDependencies(gameRepo, sessions, secrets, metrics, storage)
  }

  private def createAzure(): Resource[IO, ServerDependencies] = {
    for {
      secrets <- AzureKeyVaultSecrets.resource()
      dbConfig <- Resource.eval(secrets.getSecret("db-config"))
      gameRepo <- PostgresGameRepository.resource(dbConfig)
      sessions <- RedisSessionStore.resource() // Same Redis
      metrics <- AzureMonitorMetrics.resource()
      storage <- BlobStorageObjectStore.resource()
    } yield ServerDependencies(gameRepo, sessions, secrets, metrics, storage)
  }

  // Local development with TestContainers
  private def createLocal(): Resource[IO, ServerDependencies] = {
    for {
      postgres <- TestContainers.postgres()
      redis <- TestContainers.redis()
      gameRepo <- PostgresGameRepository.resource(postgres.connectionString)
      sessions <- RedisSessionStore.resource(redis.endpoint)
      secrets <- InMemorySecretManager.resource() // Dev secrets
      metrics <- NoOpMetrics.resource() // Or Prometheus
      storage <- LocalFileStore.resource("/tmp/go3d-storage")
    } yield ServerDependencies(gameRepo, sessions, secrets, metrics, storage)
  }
}
```

### 2.4 Usage in Server

```scala
// src/main/scala/go3d/server/GoServer.scala

object GoServer extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val cloudProvider = sys.env.getOrElse("CLOUD_PROVIDER", "local")

    ServerDependencies.create(cloudProvider).use { deps =>
      for {
        _ <- IO.println(s"Starting Go-3D server on $cloudProvider")

        // Pass dependencies to HTTP service
        httpApp = GoHttpService.routes(deps).orNotFound

        // Start server
        _ <- EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"6030")
          .withHttpApp(httpApp)
          .build
          .use(_ => IO.never)

      } yield ExitCode.Success
    }
  }
}
```

---

## 3. Container-First Deployment Strategy

### 3.1 Why Containers Maximize Portability

| Deployment | AWS | GCP | Azure |
|------------|-----|-----|-------|
| **Container** | Lambda (custom runtime) | Cloud Run | Container Apps |
|  | ECS Fargate | GKE Autopilot | AKS |
| **Serverless** | Lambda (zip) ❌ | Cloud Functions ❌ | Functions ❌ |
| **VM** | EC2 | Compute Engine | Virtual Machines |

✅ **Same Docker image works on all container platforms**
❌ **Platform-specific deployment artifacts lock you in**

### 3.2 Multi-Stage Dockerfile (Portable)

```dockerfile
# Multi-stage build for maximum portability
# Can target: Lambda, Cloud Run, Container Apps, Kubernetes

FROM hseeberger/scala-sbt:17.0.2_1.6.2_3.1.1 AS builder

WORKDIR /app
COPY . .

# Build fat JAR (portable everywhere)
RUN sbt assembly

# Optional: Build native image (recompile per platform)
# RUN sbt nativeImage

# Runtime stage - uses standard base images
FROM eclipse-temurin:21-jre-alpine AS runtime-jvm

WORKDIR /app
COPY --from=builder /app/target/scala-3.7.3/go-3d-assembly-*.jar /app/server.jar

# Configuration via environment variables (cloud-agnostic)
ENV CLOUD_PROVIDER=aws
ENV PORT=8080
ENV DB_HOST=localhost
ENV REDIS_HOST=localhost

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]

# Alternative: Native image runtime (smaller, faster)
FROM debian:bullseye-slim AS runtime-native

WORKDIR /app
COPY --from=builder /app/target/native-image/go-3d-server /app/server

ENV CLOUD_PROVIDER=aws
ENV PORT=8080

EXPOSE 8080
ENTRYPOINT ["/app/server"]
```

### 3.3 Lambda Custom Runtime (Containers)

```dockerfile
# Lambda-specific runtime (still mostly portable)
FROM public.ecr.aws/lambda/provided:al2023 AS lambda-runtime

# Copy application binary
COPY --from=builder /app/target/native-image/go-3d-server ${LAMBDA_RUNTIME_DIR}/bootstrap

# Lambda Runtime Interface Emulator (for local testing)
ADD https://github.com/aws/aws-lambda-runtime-interface-emulator/releases/latest/download/aws-lambda-rie /usr/local/bin/aws-lambda-rie
RUN chmod 755 /usr/local/bin/aws-lambda-rie

# Environment configuration
ENV CLOUD_PROVIDER=aws

# Lambda entrypoint
ENTRYPOINT ["/lambda-entrypoint.sh"]
CMD ["bootstrap"]
```

**Key Point:** Even this Lambda-specific image is 90% the same. Only the base image and entrypoint differ.

### 3.4 Cloud-Agnostic HTTP Handler

Instead of Lambda-specific handler, use standard HTTP:

```scala
// Works on Lambda, Cloud Run, Container Apps, anywhere
object GoServerHTTP extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val port = sys.env.getOrElse("PORT", "8080").toInt
    val cloud = sys.env.getOrElse("CLOUD_PROVIDER", "local")

    ServerDependencies.create(cloud).use { deps =>
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(port).get)
        .withHttpApp(GoHttpService.routes(deps).orNotFound)
        .build
        .useForever
    }
  }
}
```

**Portability:** This exact code runs on:
- AWS Lambda (via API Gateway HTTP API → Lambda container)
- GCP Cloud Run (direct HTTP)
- Azure Container Apps (direct HTTP)
- Kubernetes (direct HTTP)
- Local development (direct HTTP)

---

## 4. Cloud Service Equivalents

### 4.1 Service Mapping Matrix

| Function | AWS | GCP | Azure | Open Source |
|----------|-----|-----|-------|-------------|
| **Serverless Containers** | Lambda Container | Cloud Run | Container Apps | Knative |
| **Managed Kubernetes** | EKS | GKE | AKS | - |
| **PostgreSQL** | RDS Aurora | Cloud SQL | Azure Database | Self-hosted |
| **Redis** | ElastiCache | Memorystore | Azure Cache | Self-hosted |
| **Object Storage** | S3 | GCS | Blob Storage | MinIO |
| **Secrets** | Secrets Manager | Secret Manager | Key Vault | Vault |
| **Logging** | CloudWatch | Cloud Logging | Monitor | Loki/ELK |
| **Metrics** | CloudWatch | Cloud Monitoring | Monitor | Prometheus |
| **Tracing** | X-Ray | Cloud Trace | App Insights | Jaeger |
| **Load Balancer** | ALB | Cloud Load Balancing | Load Balancer | nginx/Envoy |
| **DNS** | Route 53 | Cloud DNS | Azure DNS | - |
| **CDN** | CloudFront | Cloud CDN | Azure CDN | Cloudflare |

### 4.2 Recommended Stack (Maximum Portability)

```
┌────────────────────────────────────────────────────┐
│             Application Layer                      │
│  Go-3D Server (Scala + http4s + Cats Effect)      │
└────────────────────────────────────────────────────┘
                      ↓
┌────────────────────────────────────────────────────┐
│         Abstraction Layer (Traits)                 │
│  GameRepository | SessionStore | SecretManager     │
└────────────────────────────────────────────────────┘
                      ↓
┌────────────────────────────────────────────────────┐
│         Standard Protocols                         │
│  PostgreSQL | Redis | HTTP | gRPC                  │
└────────────────────────────────────────────────────┘
                      ↓
┌────────────────────────────────────────────────────┐
│         Managed Services (Cloud-Specific)          │
│  AWS: RDS, ElastiCache, Secrets Manager            │
│  GCP: Cloud SQL, Memorystore, Secret Manager       │
│  Azure: Database, Cache, Key Vault                 │
└────────────────────────────────────────────────────┘
```

### 4.3 Connection String Abstraction

```scala
// Cloud-agnostic database configuration
case class DatabaseConfig(
  host: String,
  port: Int,
  database: String,
  username: String,
  password: String,
  ssl: Boolean = true,
  maxConnections: Int = 10
) {
  def jdbcUrl: String = {
    val sslMode = if (ssl) "?sslmode=require" else ""
    s"jdbc:postgresql://$host:$port/$database$sslMode"
  }
}

object DatabaseConfig {
  // Load from environment (works everywhere)
  def fromEnv(): IO[DatabaseConfig] = IO {
    DatabaseConfig(
      host = sys.env("DB_HOST"),
      port = sys.env.getOrElse("DB_PORT", "5432").toInt,
      database = sys.env.getOrElse("DB_NAME", "go3d"),
      username = sys.env("DB_USER"),
      password = sys.env("DB_PASSWORD"),
      ssl = sys.env.getOrElse("DB_SSL", "true").toBoolean,
      maxConnections = sys.env.getOrElse("DB_MAX_CONN", "10").toInt
    )
  }

  // Or load from cloud secret manager (abstracted)
  def fromSecrets(secrets: SecretManager): IO[DatabaseConfig] = {
    for {
      configJson <- secrets.getSecret("database-config")
      config <- IO(decode[DatabaseConfig](configJson).getOrElse(
        throw new RuntimeException("Invalid database config")
      ))
    } yield config
  }
}
```

---

## 5. Implementation Plan for Lambda (with Portability)

### Phase 1: Setup Abstractions (Week 1)

**Goal:** Create portable foundation before cloud-specific code.

```
Day 1-2: Define Traits
├─ GameRepository trait
├─ SessionStore trait
├─ SecretManager trait
├─ MetricsCollector trait
└─ ObjectStore trait

Day 3-4: PostgreSQL Implementation
├─ PostgresGameRepository (using Doobie)
├─ Works on: RDS, Cloud SQL, Azure Database, any PostgreSQL
└─ Test with TestContainers

Day 5-7: Redis Implementation
├─ RedisSessionStore (using Redis4Cats)
├─ Works on: ElastiCache, Memorystore, Azure Cache, any Redis
└─ Test with TestContainers
```

**Portability Check:** ✅ All code so far works on any cloud.

### Phase 2: Local Development Setup (Week 2)

```
Day 1-2: Docker Compose for Local Dev
services:
  postgres:
    image: postgres:15
  redis:
    image: redis:7
  go3d-server:
    build: .
    environment:
      CLOUD_PROVIDER: local
      DB_HOST: postgres
      REDIS_HOST: redis

Day 3-4: Multi-Stage Dockerfile
├─ Build stage (sbt assembly)
├─ JVM runtime stage
├─ Native image stage (optional)
└─ Test all stages locally

Day 5-7: Refactor Server Code
├─ Remove singleton state (Games, Players)
├─ Wire up dependency injection
├─ Accept dependencies via constructor
└─ Integration tests with real Postgres/Redis
```

**Portability Check:** ✅ Still no cloud-specific code.

### Phase 3: AWS-Specific Adapters (Week 3)

Now we add AWS-specific implementations:

```
Day 1-2: AWS Secret Manager Adapter
class AWSSecretManager extends SecretManager {
  // Uses AWS SDK, but only in this file
  private val client = SecretsManagerClient.create()

  def getSecret(name: String): IO[String] = IO.blocking {
    client.getSecretValue(
      GetSecretValueRequest.builder().secretId(name).build()
    ).secretString()
  }
}

Day 3-4: CloudWatch Metrics Adapter
class CloudWatchMetrics extends MetricsCollector {
  private val client = CloudWatchClient.create()
  // Implementation...
}

Day 5-7: S3 Object Store Adapter
class S3ObjectStore extends ObjectStore {
  private val client = S3Client.create()
  // Implementation...
}
```

**Portability Check:** ✅ AWS code isolated to `infra/cloud/aws/` directory.

### Phase 4: Lambda Deployment (Week 4)

```
Day 1-2: Lambda Container Configuration
├─ Dockerfile with Lambda base image
├─ API Gateway HTTP API setup
├─ Terraform/CDK for infrastructure
└─ Environment variables for config

Day 3-4: Deploy to AWS
├─ Push container to ECR
├─ Create Lambda function from container
├─ Setup API Gateway routes
├─ Configure RDS, ElastiCache, Secrets Manager
└─ Test end-to-end

Day 5-7: Monitoring & Optimization
├─ CloudWatch dashboards
├─ Performance testing
├─ Cost optimization
└─ Documentation
```

**Portability Check:** ✅ Can migrate to GCP/Azure by:
1. Implementing 3 adapter classes (~200 lines each)
2. Changing Terraform provider
3. Updating Dockerfile base image

---

## 6. Migration Paths

### 6.1 AWS Lambda → GCP Cloud Run

**Effort: 1-2 weeks**

```diff
# Changes required:

# 1. Implement GCP adapters (3 files, ~600 lines)
+ infra/cloud/gcp/GCPSecretManager.scala
+ infra/cloud/gcp/CloudMonitoringMetrics.scala
+ infra/cloud/gcp/GCSObjectStore.scala

# 2. Update Dockerfile base image
- FROM public.ecr.aws/lambda/provided:al2023
+ FROM gcr.io/distroless/java17-debian11

# 3. Update Terraform provider
- provider "aws" { ... }
+ provider "google" { ... }

# 4. Update infrastructure resources
- resource "aws_lambda_function" "go3d" { ... }
+ resource "google_cloud_run_service" "go3d" { ... }

# 5. Change environment variable
- CLOUD_PROVIDER=aws
+ CLOUD_PROVIDER=gcp
```

**No changes to:**
- Business logic (Game, Goban, Move) ✅
- HTTP service (GoHttpService) ✅
- PostgreSQL repository ✅
- Redis session store ✅
- Test suite ✅

### 6.2 AWS Lambda → Azure Container Apps

**Effort: 1-2 weeks**

```diff
# Similar changes to GCP:

+ infra/cloud/azure/AzureKeyVaultSecrets.scala
+ infra/cloud/azure/AzureMonitorMetrics.scala
+ infra/cloud/azure/BlobStorageObjectStore.scala

- CLOUD_PROVIDER=aws
+ CLOUD_PROVIDER=azure

# Use Azure Bicep or Terraform azurerm provider
```

### 6.3 Cross-Cloud Deployment (Active-Active)

**Advanced: Run on multiple clouds simultaneously**

```scala
// Load balancer sends traffic to nearest cloud
// Use PostgreSQLcockroachDB for multi-region database
// Or PostgreSQL with replication

object MultiCloudServer extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val region = sys.env("CLOUD_REGION") // us-east-1, europe-west1, etc.
    val cloud = cloudFromRegion(region)

    ServerDependencies.create(cloud).use { deps =>
      // Same server code, different cloud backends
      startServer(deps)
    }
  }
}
```

---

## 7. Testing Strategy

### 7.1 Test Levels

```
┌────────────────────────────────────────┐
│  E2E Tests (Cloud-Agnostic)           │  Test complete workflows
│  └─ TestContainers (Postgres, Redis)  │  No cloud dependencies
├────────────────────────────────────────┤
│  Integration Tests (Per Cloud)        │  Test adapters
│  ├─ AWS: LocalStack                   │  Mock cloud services
│  ├─ GCP: Emulators                    │
│  └─ Azure: Azurite                    │
├────────────────────────────────────────┤
│  Unit Tests (Pure Functions)          │  Business logic only
│  └─ ScalaTest                         │  100% portable
└────────────────────────────────────────┘
```

### 7.2 TestContainers for Portability

```scala
// tests/IntegrationSpec.scala

class IntegrationSpec extends AnyFlatSpec {

  // Start real Postgres & Redis (works anywhere)
  val containers = TestContainersSetup.start()

  // Test against real services
  "GameRepository" should "save and load games" in {
    val repo = PostgresGameRepository(containers.postgres.jdbcUrl)
    val game = Game.start(5)

    repo.save("test-game", game).unsafeRunSync()
    val loaded = repo.load("test-game").unsafeRunSync()

    assert(loaded.contains(game))
  }

  // No cloud-specific mocking needed!
}
```

### 7.3 Contract Tests for Adapters

```scala
// Ensure all cloud adapters behave identically

trait SecretManagerContract {
  def secretManager: SecretManager

  "SecretManager" should "retrieve secrets" in {
    val secret = secretManager.getSecret("test-secret").unsafeRunSync()
    assert(secret.nonEmpty)
  }

  it should "handle missing secrets" in {
    val result = secretManager.getSecret("nonexistent").attempt.unsafeRunSync()
    assert(result.isLeft)
  }
}

// Run same tests against all implementations
class AWSSecretManagerSpec extends SecretManagerContract {
  lazy val secretManager = new AWSSecretManager()
}

class GCPSecretManagerSpec extends SecretManagerContract {
  lazy val secretManager = new GCPSecretManager()
}

class AzureSecretManagerSpec extends SecretManagerContract {
  lazy val secretManager = new AzureKeyVaultSecrets()
}
```

---

## 8. Infrastructure as Code

### 8.1 Terraform (Multi-Cloud)

**One language, three clouds:**

```hcl
# terraform/variables.tf
variable "cloud_provider" {
  type = string
  default = "aws"
  validation {
    condition = contains(["aws", "gcp", "azure"], var.cloud_provider)
  }
}

# terraform/main.tf
module "go3d_deployment" {
  source = "./modules/${var.cloud_provider}"

  # Common variables
  app_name = "go3d-server"
  environment = var.environment
  db_instance_type = var.db_instance_type
  cache_node_type = var.cache_node_type
}

# terraform/modules/aws/main.tf
resource "aws_lambda_function" "go3d" {
  function_name = var.app_name
  package_type  = "Image"
  image_uri     = var.container_image
  // ...
}

# terraform/modules/gcp/main.tf
resource "google_cloud_run_service" "go3d" {
  name     = var.app_name
  location = var.region

  template {
    spec {
      containers {
        image = var.container_image
      }
    }
  }
}

# terraform/modules/azure/main.tf
resource "azurerm_container_app" "go3d" {
  name                = var.app_name
  resource_group_name = azurerm_resource_group.go3d.name
  // ...
}
```

**Deploy to any cloud:**

```bash
# Deploy to AWS
terraform apply -var="cloud_provider=aws"

# Deploy to GCP
terraform apply -var="cloud_provider=gcp"

# Deploy to Azure
terraform apply -var="cloud_provider=azure"
```

### 8.2 Pulumi (Alternative - Type-Safe IaC)

```scala
// infrastructure/Main.scala
import com.pulumi._

object Main {
  def main(args: Array[String]): Unit = {
    Pulumi.run { ctx =>
      val cloudProvider = ctx.config.require("cloudProvider")

      cloudProvider match {
        case "aws" => deployToAWS(ctx)
        case "gcp" => deployToGCP(ctx)
        case "azure" => deployToAzure(ctx)
      }
    }
  }
}
```

---

## 9. Cost Comparison Across Clouds

### Low Traffic (1,000 games/month)

| Service | AWS | GCP | Azure |
|---------|-----|-----|-------|
| **Compute (Container)** | Lambda: $5 | Cloud Run: $3 | Container Apps: $4 |
| **Database (PostgreSQL)** | Aurora Serverless: $30 | Cloud SQL: $25 | Azure Database: $28 |
| **Cache (Redis)** | ElastiCache: $15 | Memorystore: $12 | Azure Cache: $14 |
| **Secrets** | Secrets Manager: $2 | Secret Manager: $1 | Key Vault: $2 |
| **Load Balancer** | ALB: $20 | Load Balancer: $18 | Load Balancer: $19 |
| **Other** | $5 | $4 | $5 |
| **TOTAL** | **$77** | **$63** | **$72** |

### High Traffic (50,000 games/month)

| Service | AWS | GCP | Azure |
|---------|-----|-----|-------|
| **Compute** | Lambda Native: $40 | Cloud Run: $35 | Container Apps: $38 |
| **Database** | Aurora: $120 | Cloud SQL: $110 | Azure Database: $115 |
| **Cache** | ElastiCache: $50 | Memorystore: $45 | Azure Cache: $48 |
| **Secrets** | $2 | $1 | $2 |
| **Load Balancer** | $25 | $22 | $24 |
| **Other** | $10 | $8 | $9 |
| **TOTAL** | **$247** | **$221** | **$236** |

**GCP tends to be 10-15% cheaper, especially for Cloud Run and managed databases.**

---

## 10. Decision Matrix

### When to Stay Single Cloud

✅ **Deploy to AWS only if:**
- Project is < 6 months old
- < 1000 users
- Budget < $500/month
- Team < 3 people

**Reason:** Premature multi-cloud is over-engineering.

### When to Go Multi-Cloud

✅ **Architect for portability if:**
- Regulated industry (avoid vendor lock-in)
- Enterprise customers require specific cloud
- Budget > $5000/month (negotiating leverage)
- Geographic requirements (GCP strong in Asia, Azure in Europe)
- Disaster recovery requires different cloud

---

## 11. Recommended Approach

### Step 1: Build with Portability (Weeks 1-2)

```
✅ Use abstractions (traits)
✅ Container-first deployment
✅ Standard protocols (PostgreSQL, Redis, HTTP)
✅ Environment-based configuration
✅ TestContainers for testing
```

### Step 2: Deploy to AWS Lambda (Weeks 3-4)

```
✅ Implement AWS adapters (3 files)
✅ Terraform for AWS resources
✅ Docker container to Lambda
✅ Monitor and optimize
```

### Step 3: Keep Option Open (Ongoing)

```
✅ Document adapter interface contracts
✅ Quarterly review of cloud pricing
✅ Maintain migration runbook
✅ Test locally with TestContainers
✅ Never use cloud-specific APIs in business logic
```

### Step 4: Migrate if Needed (1-2 weeks when required)

```
1. Implement 3 adapter classes for target cloud
2. Update Terraform provider
3. Update Dockerfile base image
4. Deploy to new cloud
5. Test end-to-end
6. Switch DNS / traffic
```

---

## 12. Anti-Patterns to Avoid

### ❌ Don't Do This

```scala
// AWS SDK in business logic - VENDOR LOCK-IN!
import com.amazonaws.services.s3.AmazonS3

class Game(val id: String) {
  def save(): Unit = {
    val s3 = AmazonS3ClientBuilder.defaultClient()
    s3.putObject("games", s3, serialize()) // ❌ Bad!
  }
}
```

### ✅ Do This Instead

```scala
// Abstract storage, implement per cloud
trait ObjectStore {
  def save(key: String, data: Array[Byte]): IO[Unit]
}

class Game(val id: String) {
  def save(storage: ObjectStore): IO[Unit] = {
    storage.save(id, serialize()) // ✅ Good!
  }
}
```

### ❌ Don't Use Cloud-Specific Features Without Abstraction

- AWS DynamoDB Streams
- GCP Pub/Sub
- Azure Service Bus

**Unless you abstract them behind interfaces!**

### ✅ Do Use Standard Technologies

- PostgreSQL (works everywhere)
- Redis (works everywhere)
- HTTP/gRPC (works everywhere)
- Docker containers (works everywhere)
- Prometheus metrics (works everywhere)

---

## 13. Migration Checklist

When switching clouds, verify:

- [ ] All cloud adapters implemented and tested
- [ ] Infrastructure as Code updated
- [ ] Environment variables documented
- [ ] Secrets migrated to new cloud
- [ ] Database migrated or replicated
- [ ] DNS updated
- [ ] Monitoring dashboards created
- [ ] Alerts configured
- [ ] Cost budgets set
- [ ] Runbook documented
- [ ] Team trained on new cloud
- [ ] Rollback plan tested

---

## Conclusion

**Core Strategy:**
1. ✅ **Abstractions everywhere** - Traits for all external services
2. ✅ **Containers first** - Same Docker image across clouds
3. ✅ **Standard protocols** - PostgreSQL, Redis, HTTP only
4. ✅ **Cloud adapters** - Swap implementations, not code

**Result:**
- Deploy to Lambda in 3-4 weeks
- Migrate to GCP/Azure in 1-2 weeks
- **90% of codebase is cloud-agnostic**

**Trade-off:**
- Slightly more upfront design (abstractions)
- Slightly less use of cloud-specific features
- **Massive flexibility and negotiating power**

*Multi-cloud strategy document for Go-3D deployment*
