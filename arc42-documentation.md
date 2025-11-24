# arc42 Architecture Documentation: Go-3D Server

**Version:** 0.7.16
**Date:** November 24, 2025
**Status:** Migration to Multi-Cloud Architecture

---

## Document Status Legend

Throughout this document, you'll see status indicators:

- ✅ **AS-IS**: Current implementation (v0.7.16)
- 🎯 **TO-BE**: Target multi-cloud architecture
- 🔄 **MIGRATION**: Transition path from AS-IS to TO-BE

---

## Table of Contents

1. [Introduction and Goals](#1-introduction-and-goals)
2. [Constraints](#2-constraints)
3. [Context and Scope](#3-context-and-scope)
4. [Solution Strategy](#4-solution-strategy)
5. [Building Block View](#5-building-block-view)
6. [Runtime View](#6-runtime-view)
7. [Deployment View](#7-deployment-view)
8. [Crosscutting Concepts](#8-crosscutting-concepts)
9. [Architecture Decisions](#9-architecture-decisions)
10. [Quality Requirements](#10-quality-requirements)
11. [Risks and Technical Debt](#11-risks-and-technical-debt)
12. [Glossary](#12-glossary)

---

## 1. Introduction and Goals

### 1.1 Requirements Overview

**Go-3D** is a three-dimensional Go (Weiqi/Baduk) game server that extends the traditional 2D board game into a cubic lattice, allowing players to place stones in 3D space.

#### Core Functional Requirements

| Requirement | Description | Status |
|-------------|-------------|--------|
| **3D Game Logic** | Implement Go rules in 3D space | ✅ Implemented |
| **Multi-player Support** | Two players per game with turn management | ✅ Implemented |
| **Game State Persistence** | Save and restore games | ✅ File-based (TO-BE: Database) |
| **Multiple Clients** | ASCII terminal, 3D visualization, AI bots | ✅ Implemented |
| **REST API** | HTTP endpoints for game operations | ✅ Implemented |
| **Authentication** | Token-based player authentication | ✅ Implemented |

#### Quality Goals

| Priority | Quality Goal | AS-IS Status | TO-BE Target |
|----------|--------------|--------------|--------------|
| 1 | **Portability** | ❌ Single deployment | 🎯 Multi-cloud capable |
| 2 | **Scalability** | ❌ Single server | 🎯 Horizontal scaling |
| 3 | **Cost Efficiency** | ⚠️ Always-on server | 🎯 Serverless, scales to zero |
| 4 | **Maintainability** | ✅ Clean domain model | 🎯 Service abstractions |
| 5 | **Performance** | ✅ Fast game logic | 🎯 Sub-200ms cold starts |

### 1.2 Stakeholders

| Role | Name/Group | Expectations | Status |
|------|------------|--------------|--------|
| **Developer** | Project Owner | Clean architecture, easy to extend | ✅ Satisfied |
| **DevOps** | Operations Team | Easy deployment, low maintenance | 🔄 Improving |
| **Players** | End Users | Fast response times, reliable service | ✅ Local, 🎯 Cloud |
| **Cloud Provider** | AWS/GCP/Azure | Standard APIs, best practices | 🎯 Multi-cloud |

### 1.3 Project Goals

**Current State (AS-IS):**
- ✅ Functional 3D Go game with complete rule implementation
- ✅ HTTP server with REST API
- ✅ Multiple client implementations
- ✅ Comprehensive test suite
- ❌ Monolithic architecture with singleton state
- ❌ Local file-based persistence
- ❌ Not cloud-ready

**Target State (TO-BE):**
- 🎯 Cloud-native, multi-cloud capable architecture
- 🎯 Horizontal scalability across cloud providers
- 🎯 Service abstraction layer for cloud portability
- 🎯 Container-first deployment strategy
- 🎯 Database-backed persistence
- 🎯 Serverless deployment option (Lambda/Cloud Run)
- 🎯 Infrastructure as Code for all clouds

---

## 2. Constraints

### 2.1 Technical Constraints

| Constraint | Description | Impact |
|------------|-------------|--------|
| **Scala 3.7.3** | ✅ Programming language | Modern language features, smaller ecosystem |
| **JVM Platform** | ✅ Runtime environment | Cold start issues in serverless (mitigated by native image) |
| **http4s + Cats Effect** | ✅ Web framework | Functional programming style, excellent for async |
| **No Breaking Changes** | 🎯 Maintain API compatibility | Migration must be transparent to clients |

### 2.2 Organizational Constraints

| Constraint | Description | Impact |
|------------|-------------|--------|
| **Solo Developer** | ✅ Single maintainer | Prefer simplicity over complex patterns |
| **Limited Budget** | 🎯 Cost-sensitive deployment | Favor serverless, managed services |
| **No DevOps Team** | 🎯 Self-managed infrastructure | Need simple, automated deployments |

### 2.3 Conventions

| Convention | AS-IS | TO-BE |
|------------|-------|-------|
| **Code Style** | ✅ Scala 3 idioms, functional style | ✅ Maintain |
| **Testing** | ✅ ScalaTest, JUnit 5 | 🎯 Add TestContainers |
| **Deployment** | ✅ Docker | 🎯 Multi-cloud containers |
| **Documentation** | ✅ Markdown | 🎯 arc42 + ADRs |

---

## 3. Context and Scope

### 3.1 Business Context

```
┌─────────────────────────────────────────────────────────────┐
│                    External Actors                           │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │    Human     │  │      Bot     │  │  3D Viewer   │      │
│  │   Players    │  │   Players    │  │   (Watch)    │      │
│  │ (ASCII CLI)  │  │ (Automated)  │  │   (GDX)      │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
│         └──────────────────┼──────────────────┘              │
│                            │                                 │
│                   ┌────────▼─────────┐                       │
│                   │                  │                       │
│                   │   Go-3D Server   │                       │
│                   │   (HTTP API)     │                       │
│                   │                  │                       │
│                   └────────┬─────────┘                       │
│                            │                                 │
│         ┌──────────────────┼──────────────────┐              │
│         │                  │                  │              │
│  ┌──────▼───────┐  ┌───────▼──────┐  ┌───────▼──────┐      │
│  │   Storage    │  │    Metrics   │  │     Logs     │      │
│  │  (Games)     │  │  (Optional)  │  │  (Optional)  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

#### Communication Channels

| Partner | Input | Output | Protocol |
|---------|-------|--------|----------|
| **Players (Human)** | Game moves, passes | Game state, board | HTTP/JSON |
| **Players (Bot)** | Automated moves | Game state | HTTP/JSON |
| **Viewers** | Status requests | Board state | HTTP/JSON (polling) |
| **Storage** | Game state | Persisted games | ✅ File I/O → 🎯 Database |
| **Monitoring** | Events | Metrics, logs | 🎯 Cloud-native APIs |

### 3.2 Technical Context

#### AS-IS (Current Architecture)

```
┌────────────────────────────────────────────────────────┐
│                    Go-3D Server                         │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │         HTTP Layer (http4s)                      │  │
│  │  ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈  │  │
│  │  GET /new/{size}        - Create game           │  │
│  │  GET /register/{id}/{c} - Register player       │  │
│  │  GET /status/{id}       - Get state             │  │
│  │  GET /set/{id}/{x}/{y}/{z} - Make move          │  │
│  │  GET /pass/{id}         - Pass turn             │  │
│  └────────────────┬─────────────────────────────────┘  │
│                   │                                     │
│  ┌────────────────▼─────────────────────────────────┐  │
│  │         Business Logic (Pure Scala)             │  │
│  │  ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈  │  │
│  │  Game, Goban, Move, Position                    │  │
│  │  Liberty calculation, capture logic             │  │
│  │  Ko rule, suicide rule, scoring                 │  │
│  └────────────────┬─────────────────────────────────┘  │
│                   │                                     │
│  ┌────────────────▼─────────────────────────────────┐  │
│  │      Singleton State (PROBLEM!)                 │  │
│  │  ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈  │  │
│  │  Games: mutable.Map[String, Game]               │  │
│  │  Players: mutable.Map[String, Player]           │  │
│  └────────────────┬─────────────────────────────────┘  │
│                   │                                     │
│  ┌────────────────▼─────────────────────────────────┐  │
│  │         File I/O (JSON Files)                   │  │
│  │  ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈  │  │
│  │  saves/{gameId}.json                            │  │
│  │  saves/archive/{gameId}.json                    │  │
│  └─────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

❌ Issues:
- Singleton state prevents horizontal scaling
- File I/O not shared across instances
- No cloud provider abstraction
- Not suitable for serverless deployment
```

#### TO-BE (Multi-Cloud Architecture)

```
┌────────────────────────────────────────────────────────────┐
│                    Go-3D Server                             │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         HTTP Layer (http4s) - UNCHANGED              │  │
│  └────────────────┬─────────────────────────────────────┘  │
│                   │                                         │
│  ┌────────────────▼─────────────────────────────────────┐  │
│  │      Business Logic (Pure Scala) - UNCHANGED         │  │
│  └────────────────┬─────────────────────────────────────┘  │
│                   │                                         │
│  ┌────────────────▼─────────────────────────────────────┐  │
│  │      Service Abstractions (NEW!)                     │  │
│  │  ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈  │  │
│  │  trait GameRepository                                │  │
│  │  trait SessionStore                                  │  │
│  │  trait SecretManager                                 │  │
│  │  trait MetricsCollector                              │  │
│  │  trait ObjectStore                                   │  │
│  └────────────────┬─────────────────────────────────────┘  │
│                   │                                         │
│         ┌─────────┴─────────┐                              │
│         │                   │                              │
│  ┌──────▼──────┐    ┌───────▼────────┐                    │
│  │  Standard   │    │  Cloud-Specific│                    │
│  │  Protocols  │    │    Adapters    │                    │
│  │ ┈┈┈┈┈┈┈┈┈┈┈ │    │ ┈┈┈┈┈┈┈┈┈┈┈┈┈┈ │                    │
│  │ PostgreSQL  │    │ AWS / GCP /    │                    │
│  │ Redis       │    │ Azure impls    │                    │
│  │ HTTP        │    │ (3 files each) │                    │
│  └─────────────┘    └────────────────┘                    │
└────────────────────────────────────────────────────────────┘

✅ Benefits:
- Horizontal scaling via stateless design
- Database-backed persistence (shared state)
- Cloud provider abstraction (swap implementations)
- Serverless-ready (stateless containers)
- 90% of code is cloud-agnostic
```

---

## 4. Solution Strategy

### 4.1 Technology Decisions

| Decision | AS-IS | TO-BE | Rationale |
|----------|-------|-------|-----------|
| **Language** | ✅ Scala 3.7.3 | ✅ Keep | Type safety, FP support, mature ecosystem |
| **Web Framework** | ✅ http4s + Cats Effect | ✅ Keep | Pure FP, excellent async, cloud-native |
| **JSON** | ✅ Circe | ✅ Keep | Compile-time serialization, type-safe |
| **State Management** | ❌ Singleton objects | 🎯 Dependency Injection | Enable horizontal scaling |
| **Persistence** | ❌ Local files | 🎯 PostgreSQL | Shared across instances, ACID guarantees |
| **Sessions** | ❌ In-memory | 🎯 Redis | Distributed, fast, battle-tested |
| **Deployment** | ✅ Docker | 🎯 Docker + Native Image | Container portability, faster cold starts |

### 4.2 Top-Level Decomposition

```
┌─────────────────────────────────────────────────────────┐
│                    Portability Layers                    │
├─────────────────────────────────────────────────────────┤
│  Domain Layer (100% portable)                           │
│  ├─ Game, Goban, Move, Position                         │
│  └─ Pure business logic, no external dependencies       │
├─────────────────────────────────────────────────────────┤
│  Service Abstraction Layer (100% portable)              │
│  ├─ Scala traits for all external services              │
│  └─ No implementation, just contracts                   │
├─────────────────────────────────────────────────────────┤
│  Standard Protocol Layer (95% portable)                 │
│  ├─ PostgreSQL implementation (Doobie)                  │
│  ├─ Redis implementation (Redis4Cats)                   │
│  └─ Works on any cloud's managed services               │
├─────────────────────────────────────────────────────────┤
│  Cloud Adapter Layer (swap per cloud)                   │
│  ├─ AWS: Secrets Manager, CloudWatch, S3                │
│  ├─ GCP: Secret Manager, Cloud Monitoring, GCS          │
│  └─ Azure: Key Vault, Azure Monitor, Blob Storage       │
├─────────────────────────────────────────────────────────┤
│  Infrastructure Layer (per cloud)                       │
│  ├─ Terraform modules for AWS/GCP/Azure                 │
│  └─ Same interface, different providers                 │
└─────────────────────────────────────────────────────────┘
```

### 4.3 Achieving Key Quality Goals

| Quality Goal | Strategy | Implementation |
|--------------|----------|----------------|
| **Portability** | Service abstractions + containers | Scala traits + Docker |
| **Scalability** | Stateless design + external state | Remove singletons, use DB/Redis |
| **Cost Efficiency** | Serverless + auto-scaling | Lambda/Cloud Run with native image |
| **Maintainability** | Clean architecture + DI | Separate concerns, interface-driven |
| **Performance** | Native compilation + caching | GraalVM Native Image + Redis |

---

## 5. Building Block View

### 5.1 Level 0: System Context

#### AS-IS Context

```
                    ┌──────────────────┐
                    │   HTTP Clients   │
                    │  (Players, Bots) │
                    └────────┬─────────┘
                             │
                             │ HTTP/JSON
                             │
                    ┌────────▼─────────┐
                    │                  │
                    │   Go-3D Server   │
                    │   (Monolithic)   │
                    │                  │
                    └────────┬─────────┘
                             │
                             │ File I/O
                             │
                    ┌────────▼─────────┐
                    │  Local Filesystem│
                    │  (saves/*.json)  │
                    └──────────────────┘
```

#### TO-BE Context

```
                    ┌──────────────────┐
                    │   HTTP Clients   │
                    │  (Players, Bots) │
                    └────────┬─────────┘
                             │
                             │ HTTP/JSON
                             │
                    ┌────────▼─────────┐
                    │                  │
                    │   Go-3D Server   │
                    │  (Stateless)     │
                    │                  │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼────┐  ┌──────▼──────┐  ┌───▼────────┐
     │ PostgreSQL  │  │    Redis    │  │ Cloud APIs │
     │  (Games)    │  │ (Sessions)  │  │ (Secrets,  │
     │             │  │             │  │  Metrics)  │
     └─────────────┘  └─────────────┘  └────────────┘
```

### 5.2 Level 1: Go-3D Server Whitebox

#### AS-IS Structure

```
go3d-server (AS-IS)
│
├── domain/                  ✅ GOOD - Will keep as-is
│   ├── Game.scala          (Game flow, ko rule, scoring)
│   ├── Goban.scala         (Board state, liberties, captures)
│   ├── Position.scala      (3D coordinates)
│   ├── Move.scala          (Stone placement)
│   ├── Color.scala         (Black, White, Empty, Sentinel)
│   └── IllegalMove.scala   (Domain exceptions)
│
├── server/                  ⚠️ NEEDS REFACTORING
│   ├── GoServer.scala      (Main entry point)
│   ├── http4s/
│   │   ├── GoHttpService.scala     (Route definitions)
│   │   ├── StartNewGame.scala      (Handler)
│   │   ├── RegisterPlayer.scala    (Handler)
│   │   ├── GetStatus.scala         (Handler)
│   │   ├── DoSet.scala             (Handler)
│   │   └── DoPass.scala            (Handler)
│   │
│   ├── Games.scala         ❌ SINGLETON - Remove
│   ├── Players.scala       ❌ SINGLETON - Remove
│   ├── FileIO.scala        ❌ LOCAL FILE - Replace
│   └── Jsonify.scala       ✅ KEEP
│
└── client/                  ✅ GOOD - Keep as-is
    ├── BaseClient.scala    (HTTP client)
    ├── AsciiClient.scala   (Terminal UI)
    ├── BotClient.scala     (AI player)
    └── gdx/                (3D visualization)
```

#### TO-BE Structure

```
go3d-server (TO-BE)
│
├── domain/                  ✅ UNCHANGED
│   └── (Same as AS-IS)
│
├── infra/                   🆕 NEW LAYER
│   │
│   ├── abstractions/        🎯 Service interfaces
│   │   ├── GameRepository.scala
│   │   ├── SessionStore.scala
│   │   ├── SecretManager.scala
│   │   ├── MetricsCollector.scala
│   │   └── ObjectStore.scala
│   │
│   ├── postgres/            🎯 Standard protocol impl
│   │   └── PostgresGameRepository.scala
│   │
│   ├── redis/               🎯 Standard protocol impl
│   │   └── RedisSessionStore.scala
│   │
│   └── cloud/               🎯 Cloud-specific adapters
│       ├── aws/
│       │   ├── AWSSecretManager.scala
│       │   ├── CloudWatchMetrics.scala
│       │   └── S3ObjectStore.scala
│       ├── gcp/
│       │   ├── GCPSecretManager.scala
│       │   ├── CloudMonitoringMetrics.scala
│       │   └── GCSObjectStore.scala
│       └── azure/
│           ├── AzureKeyVaultSecrets.scala
│           ├── AzureMonitorMetrics.scala
│           └── BlobStorageObjectStore.scala
│
├── server/                  🔄 REFACTORED
│   ├── GoServer.scala      (DI setup, wiring)
│   ├── ServerDependencies.scala  🆕 (Factory)
│   ├── http4s/             (Handlers accept deps)
│   └── Jsonify.scala       ✅ UNCHANGED
│
└── client/                  ✅ UNCHANGED
    └── (Same as AS-IS)
```

### 5.3 Level 2: Component Details

#### Component: GameRepository (TO-BE)

```scala
// infra/abstractions/GameRepository.scala
trait GameRepository {
  def save(gameId: String, game: Game): IO[Unit]
  def load(gameId: String): IO[Option[Game]]
  def listActive(): IO[List[String]]
  def archive(gameId: String): IO[Unit]
  def delete(gameId: String): IO[Unit]
}
```

**Implementations:**

| Implementation | File | Cloud Portability |
|----------------|------|-------------------|
| PostgreSQL | `postgres/PostgresGameRepository.scala` | ✅ Works on AWS RDS, GCP Cloud SQL, Azure Database |
| In-Memory (Test) | `test/InMemoryGameRepository.scala` | ✅ Unit testing |

**AS-IS Equivalent:**
- `server/Games.scala` (mutable Map + FileIO)
- ❌ Not scalable, not cloud-ready

#### Component: SessionStore (TO-BE)

```scala
// infra/abstractions/SessionStore.scala
trait SessionStore {
  def set(key: String, value: String, ttlSeconds: Int): IO[Unit]
  def get(key: String): IO[Option[String]]
  def delete(key: String): IO[Unit]
  def exists(key: String): IO[Boolean]
}
```

**Implementations:**

| Implementation | File | Cloud Portability |
|----------------|------|-------------------|
| Redis | `redis/RedisSessionStore.scala` | ✅ Works on AWS ElastiCache, GCP Memorystore, Azure Cache |
| In-Memory (Test) | `test/InMemorySessionStore.scala` | ✅ Unit testing |

**AS-IS Equivalent:**
- `server/Players.scala` (mutable Map)
- ❌ Lost on server restart

---

## 6. Runtime View

### 6.1 Scenario: Create New Game

#### AS-IS Flow

```
Client                Server              Games (Singleton)    FileIO
  │                      │                         │              │
  ├─GET /new/5──────────▶│                         │              │
  │                      │                         │              │
  │                      ├─register(size)─────────▶│              │
  │                      │                         │              │
  │                      │                         ├─generateId() │
  │                      │                         ├─Game.start() │
  │                      │                         ├─map += game  │ ❌ In-memory
  │                      │                         │              │
  │                      │◀────gameId──────────────┤              │
  │                      │                         │              │
  │◀─{id, size}─────────┤                         │              │
  │                      │                         │              │
```

❌ **Issues:**
- State in singleton Map (not scalable)
- No persistence until player registers
- Single point of failure

#### TO-BE Flow

```
Client                Server         GameRepository      Database
  │                      │                  │               │
  ├─GET /new/5──────────▶│                  │               │
  │                      │                  │               │
  │                      ├─save(id, game)──▶│               │
  │                      │                  │               │
  │                      │                  ├─INSERT────────▶│
  │                      │                  │               │
  │                      │                  │◀──success─────┤
  │                      │                  │               │
  │                      │◀─────────────────┤               │
  │                      │                  │               │
  │◀─{id, size}─────────┤                  │               │
  │                      │                  │               │
```

✅ **Benefits:**
- Stateless server (scalable)
- Immediate persistence
- Multiple instances can share state

### 6.2 Scenario: Make Move

#### AS-IS Flow

```
Client                Server              Games         Players
  │                      │                  │             │
  ├─GET /set/ID/3/3/3───▶│                  │             │
  │   + Bearer TOKEN     │                  │             │
  │                      │                  │             │
  │                      ├─validate(token)──┤             │
  │                      │                  │             │
  │                      │                  ├────────────▶│ ❌ In-memory
  │                      │                  │             │
  │                      ├─Games(id)────────▶│             │
  │                      │                  │             │
  │                      │◀─game────────────┤             │
  │                      │                  │             │
  │                      ├─game.makeMove()  │             │ ✅ Pure logic
  │                      │                  │             │
  │                      ├─Games += newGame─▶│             │ ❌ In-memory
  │                      │                  │             │
  │◀─{game, status}─────┤                  │             │
  │                      │                  │             │
```

#### TO-BE Flow

```
Client        Server    SessionStore  GameRepository  Database
  │              │            │              │            │
  ├─GET /set────▶│            │              │            │
  │  + TOKEN     │            │              │            │
  │              │            │              │            │
  │              ├─get(token)─▶│              │            │
  │              │            │              │            │
  │              │            ├─REDIS GET───▶│            │ ✅ Distributed
  │              │            │              │            │
  │              ├─load(id)───┤              │            │
  │              │            │              │            │
  │              │            ├──────────────▶├─SELECT───▶│
  │              │            │              │            │
  │              ├─game.makeMove()            │            │ ✅ Pure logic
  │              │            │              │            │
  │              ├─save(game)─┤              │            │
  │              │            │              │            │
  │              │            ├──────────────▶├─UPDATE───▶│
  │              │            │              │            │
  │◀─response────┤            │              │            │
  │              │            │              │            │
```

✅ **Benefits:**
- Stateless server (any instance can handle request)
- Distributed session store (Redis cluster)
- Database ensures consistency
- Horizontal scaling ready

---

## 7. Deployment View

### 7.1 AS-IS Deployment

```
┌─────────────────────────────────────────┐
│         Local / Single Server            │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │       Go-3D Server Process         │ │
│  │                                    │ │
│  │  ┌──────────────────────────────┐ │ │
│  │  │  In-Memory State             │ │ │
│  │  │  ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈  │ │ │
│  │  │  Games: Map[String, Game]    │ │ │
│  │  │  Players: Map[String, Player]│ │ │
│  │  └──────────────────────────────┘ │ │
│  │                                    │ │
│  │  Port: 6030                        │ │
│  └────────────────┬───────────────────┘ │
│                   │                      │
│                   │ File I/O             │
│                   │                      │
│  ┌────────────────▼───────────────────┐ │
│  │    Local Filesystem                │ │
│  │    saves/{gameId}.json             │ │
│  │    saves/archive/{gameId}.json     │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘

Deployment Method: ✅ Docker container or sbt run
Infrastructure: ✅ Manual (EC2, VPS, localhost)
Scalability: ❌ Vertical only (bigger machine)
Availability: ❌ Single point of failure
Cost: ⚠️ Always running, fixed cost
```

### 7.2 TO-BE Deployment: AWS Lambda (Primary Target)

```
┌──────────────────────────────────────────────────────────────┐
│                       AWS Cloud (Multi-AZ)                    │
│                                                               │
│  ┌──────────────┐                                            │
│  │  Route 53    │  go3d.example.com                          │
│  └──────┬───────┘                                            │
│         │                                                     │
│  ┌──────▼────────────────────────────────────────────────┐  │
│  │          API Gateway (HTTP API)                       │  │
│  │  /new, /register, /status, /set, /pass               │  │
│  └──────┬────────────────────────────────────────────────┘  │
│         │                                                     │
│  ┌──────▼────────────────────────────────────────────────┐  │
│  │             Lambda Functions                          │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐            │  │
│  │  │ Instance │  │ Instance │  │ Instance │            │  │
│  │  │    1     │  │    2     │  │    n     │            │  │
│  │  │(stateless)│  │(stateless)│  │(stateless)           │  │
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘            │  │
│  │       │             │             │                    │  │
│  │       └─────────────┼─────────────┘                    │  │
│  │                     │                                   │  │
│  │           Native Image Binary                          │  │
│  │           Memory: 256MB                                │  │
│  │           Cold Start: <200ms                           │  │
│  └──────┬──────────────────────────────────────────────────┘  │
│         │                                                     │
│    ┌────┼────────────────┐                                   │
│    │    │                │                                   │
│  ┌─▼────▼───┐  ┌─────────▼──────┐  ┌──────────────────┐    │
│  │   RDS    │  │  ElastiCache   │  │ Secrets Manager  │    │
│  │ Aurora   │  │     Redis      │  │                  │    │
│  │PostgreSQL│  │   (Sessions)   │  │  (DB password)   │    │
│  └──────────┘  └────────────────┘  └──────────────────┘    │
│                                                              │
└──────────────────────────────────────────────────────────────┘

Deployment: 🎯 Docker container → Lambda
Infrastructure: 🎯 Terraform/CDK (IaC)
Scalability: 🎯 Automatic (0 → thousands)
Availability: 🎯 Multi-AZ, automatic failover
Cost: 🎯 Pay-per-request ($33-40/mo low, $95-180/mo high)
```

### 7.3 TO-BE Deployment: GCP Cloud Run (Alternative)

```
┌──────────────────────────────────────────────────────────────┐
│                      GCP Cloud (Multi-Region)                 │
│                                                               │
│  ┌──────────────┐                                            │
│  │  Cloud DNS   │  go3d.example.com                          │
│  └──────┬───────┘                                            │
│         │                                                     │
│  ┌──────▼────────────────────────────────────────────────┐  │
│  │      Cloud Load Balancing (Global)                    │  │
│  └──────┬────────────────────────────────────────────────┘  │
│         │                                                     │
│  ┌──────▼────────────────────────────────────────────────┐  │
│  │            Cloud Run Service                          │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐            │  │
│  │  │Container │  │Container │  │Container │            │  │
│  │  │    1     │  │    2     │  │    n     │            │  │
│  │  │(stateless)│  │(stateless)│  │(stateless)           │  │
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘            │  │
│  │       │             │             │                    │  │
│  │       └─────────────┼─────────────┘                    │  │
│  │                     │                                   │  │
│  │           Same Docker Image as AWS!                    │  │
│  │           Port: 8080 (configurable)                    │  │
│  └──────┬──────────────────────────────────────────────────┘  │
│         │                                                     │
│    ┌────┼────────────────┐                                   │
│    │    │                │                                   │
│  ┌─▼────▼───┐  ┌─────────▼──────┐  ┌──────────────────┐    │
│  │  Cloud   │  │  Memorystore   │  │ Secret Manager   │    │
│  │   SQL    │  │     Redis      │  │                  │    │
│  │PostgreSQL│  │   (Sessions)   │  │  (DB password)   │    │
│  └──────────┘  └────────────────┘  └──────────────────┘    │
│                                                              │
└──────────────────────────────────────────────────────────────┘

Deployment: 🎯 Same Docker image, different platform
Infrastructure: 🎯 Terraform with different provider
Scalability: 🎯 Automatic (0 → thousands)
Cost: 🎯 ~10% cheaper than AWS ($30-38/mo low, $85-160/mo high)
```

### 7.4 TO-BE Deployment: Azure Container Apps (Alternative)

```
┌──────────────────────────────────────────────────────────────┐
│                     Azure Cloud (Multi-Region)                │
│                                                               │
│  ┌──────────────┐                                            │
│  │  Azure DNS   │  go3d.example.com                          │
│  └──────┬───────┘                                            │
│         │                                                     │
│  ┌──────▼────────────────────────────────────────────────┐  │
│  │         Application Gateway                           │  │
│  └──────┬────────────────────────────────────────────────┘  │
│         │                                                     │
│  ┌──────▼────────────────────────────────────────────────┐  │
│  │          Container Apps                               │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐            │  │
│  │  │Container │  │Container │  │Container │            │  │
│  │  │    1     │  │    2     │  │    n     │            │  │
│  │  │(stateless)│  │(stateless)│  │(stateless)           │  │
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘            │  │
│  │       │             │             │                    │  │
│  │       └─────────────┼─────────────┘                    │  │
│  │                     │                                   │  │
│  │           Same Docker Image!                           │  │
│  │           Port: 8080 (configurable)                    │  │
│  └──────┬──────────────────────────────────────────────────┘  │
│         │                                                     │
│    ┌────┼────────────────┐                                   │
│    │    │                │                                   │
│  ┌─▼────▼───┐  ┌─────────▼──────┐  ┌──────────────────┐    │
│  │  Azure   │  │  Azure Cache   │  │   Key Vault      │    │
│  │ Database │  │   for Redis    │  │                  │    │
│  │PostgreSQL│  │   (Sessions)   │  │  (DB password)   │    │
│  └──────────┘  └────────────────┘  └──────────────────┘    │
│                                                              │
└──────────────────────────────────────────────────────────────┘

Deployment: 🎯 Same Docker image, different platform
Infrastructure: 🎯 Terraform/Bicep with Azure provider
Scalability: 🎯 Automatic (0 → thousands)
Cost: 🎯 Similar to AWS ($34-42/mo low, $90-170/mo high)
```

### 7.5 Infrastructure Mapping

| Component | AWS | GCP | Azure | Abstraction |
|-----------|-----|-----|-------|-------------|
| **Compute** | Lambda / ECS | Cloud Run / GKE | Container Apps / AKS | Docker container |
| **Database** | RDS Aurora | Cloud SQL | Azure Database | PostgreSQL protocol |
| **Cache** | ElastiCache | Memorystore | Azure Cache | Redis protocol |
| **Secrets** | Secrets Manager | Secret Manager | Key Vault | `SecretManager` trait |
| **Storage** | S3 | GCS | Blob Storage | `ObjectStore` trait |
| **Metrics** | CloudWatch | Cloud Monitoring | Azure Monitor | `MetricsCollector` trait |
| **DNS** | Route 53 | Cloud DNS | Azure DNS | Standard DNS |
| **Load Balancer** | ALB/API Gateway | Cloud Load Balancing | App Gateway | HTTP |

---

## 8. Crosscutting Concepts

### 8.1 Domain Model (Pure Business Logic)

✅ **AS-IS: Already excellent, no changes needed**

```scala
// Pure, immutable domain model
case class Position(x: Int, y: Int, z: Int)
case class Move(position: Position, color: Color)
case class Game(size: Int, goban: Goban, moves: Array[Move | Pass], captures: Map[Int, Array[Move]])

// Pure functions, no side effects
def makeMove(move: Move): Game = {
  checkValid(move)
  val newGoban = setStone(move)
  Game(size, newGoban.goban, moves.appended(move), newGoban.captures)
}
```

**Portability: 100%** - Pure Scala, no dependencies, works everywhere.

### 8.2 Dependency Injection

#### AS-IS: No DI (Singletons)

```scala
// Current anti-pattern
object Games {
  private val activeGames: mutable.Map[String, Game] = mutable.Map()

  def apply(gameId: String): Game = activeGames(gameId) // ❌
}

// Usage in HTTP handler
class GetStatus {
  def handle(gameId: String): IO[Response] = {
    val game = Games(gameId) // ❌ Hard dependency on singleton
    Ok(game.asJson)
  }
}
```

**Issues:**
- ❌ Cannot test without singleton
- ❌ Cannot run multiple instances
- ❌ Cannot swap implementations

#### TO-BE: Constructor Injection

```scala
// Service abstraction
trait GameRepository {
  def load(gameId: String): IO[Option[Game]]
}

// HTTP handler accepts dependency
class GetStatus(repo: GameRepository) {
  def handle(gameId: String): IO[Response] = {
    repo.load(gameId).flatMap {
      case Some(game) => Ok(game.asJson)
      case None => NotFound("Game not found")
    }
  }
}

// Wiring at application startup
object GoServer extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val cloudProvider = sys.env.getOrElse("CLOUD_PROVIDER", "local")

    ServerDependencies.create(cloudProvider).use { deps =>
      val handler = new GetStatus(deps.gameRepository)
      startServer(handler)
    }
  }
}
```

**Benefits:**
- ✅ Testable (pass mock repository)
- ✅ Scalable (stateless)
- ✅ Portable (swap implementations)

### 8.3 Configuration Management

#### AS-IS: Hardcoded + Scallop

```scala
// Current approach
class Conf(args: Seq[String]) extends ScallopConf(args) {
  val port = opt[Int](default = Some(6030))
  val saveDir = opt[String](default = Some("saves"))
  verify()
}

// Hardcoded file paths
class FileIO(val baseFolder: String) {
  private val basePath = Paths.get(baseFolder) // ❌ Local filesystem
}
```

#### TO-BE: Environment Variables

```scala
// Cloud-native configuration
case class ServerConfig(
  port: Int,
  cloudProvider: String,
  dbHost: String,
  dbPort: Int,
  dbName: String,
  dbUser: String,
  dbPassword: String, // From secret manager
  redisHost: String,
  redisPort: Int
)

object ServerConfig {
  def fromEnv(): IO[ServerConfig] = IO {
    ServerConfig(
      port = sys.env.getOrElse("PORT", "8080").toInt,
      cloudProvider = sys.env.getOrElse("CLOUD_PROVIDER", "local"),
      dbHost = sys.env("DB_HOST"),
      dbPort = sys.env.getOrElse("DB_PORT", "5432").toInt,
      dbName = sys.env.getOrElse("DB_NAME", "go3d"),
      dbUser = sys.env("DB_USER"),
      dbPassword = sys.env("DB_PASSWORD"), // Or from SecretManager
      redisHost = sys.env("REDIS_HOST"),
      redisPort = sys.env.getOrElse("REDIS_PORT", "6379").toInt
    )
  }
}
```

**Benefits:**
- ✅ 12-factor app compliant
- ✅ Same code, different config per environment
- ✅ Secrets never in code

### 8.4 Error Handling

#### AS-IS: Exceptions

```scala
// Domain exceptions (keep these)
class IllegalMove(message: String) extends IllegalArgumentException(message)
class Ko(move: Move) extends IllegalMove("ko at " + move.toString)
class Suicide(move: Move) extends IllegalMove("suicide at " + move.toString)

// HTTP layer catches domain exceptions
try {
  game.makeMove(move)
  Ok(game.asJson)
} catch {
  case e: IllegalMove => BadRequest(e.getMessage)
}
```

✅ **Keep for domain logic** - Exceptions are appropriate for invalid moves.

#### TO-BE: IO Error Handling for Infrastructure

```scala
// Infrastructure errors use IO
trait GameRepository {
  def load(gameId: String): IO[Option[Game]]
}

// Usage with error recovery
def getGame(gameId: String, repo: GameRepository): IO[Response] = {
  repo.load(gameId).flatMap {
    case Some(game) => Ok(game.asJson)
    case None => NotFound("Game not found")
  }.handleErrorWith {
    case e: SQLException => ServiceUnavailable("Database error")
    case e: TimeoutException => RequestTimeout("Database timeout")
    case e => InternalServerError(e.getMessage)
  }
}
```

### 8.5 Logging

#### AS-IS: scala-logging

```scala
// Current approach
import com.typesafe.scalalogging.LazyLogging

object GoServer extends LazyLogging {
  logger.info(s"Starting server on port $port")
}
```

✅ **Keep** - slf4j is cloud-agnostic, works everywhere.

#### TO-BE: Structured Logging

```scala
// Add structured logging for cloud observability
import org.slf4j.MDC

// Add context to logs
MDC.put("gameId", gameId)
MDC.put("player", playerId)
MDC.put("cloudProvider", cloudProvider)
logger.info("Move made")
MDC.clear()

// Logs appear in:
// - AWS CloudWatch Logs
// - GCP Cloud Logging
// - Azure Monitor Logs
// All with searchable structured data
```

### 8.6 Testing Strategy

#### AS-IS

```scala
// Unit tests with ScalaTest
class TestGame extends AnyFlatSpec {
  "Game" should "detect ko" in {
    val game = Game.start(5)
    // Test pure business logic
  }
}
```

✅ **Keep** - Excellent domain testing.

#### TO-BE: Add Integration Tests

```scala
// Integration tests with TestContainers
class GameRepositorySpec extends AnyFlatSpec {
  val containers = TestContainers.start()

  "PostgresGameRepository" should "save and load games" in {
    val repo = PostgresGameRepository(containers.postgres.jdbcUrl)
    val game = Game.start(5)

    repo.save("test-1", game).unsafeRunSync()
    val loaded = repo.load("test-1").unsafeRunSync()

    assert(loaded.contains(game))
  }

  // Works without any cloud account!
  // Tests against real Postgres in Docker
}
```

**Benefits:**
- ✅ Test against real services (Postgres, Redis)
- ✅ No cloud account needed
- ✅ Fast feedback (containers start in seconds)
- ✅ Same tests run in CI/CD

---

## 9. Architecture Decisions

### ADR-001: Adopt Service Abstraction Layer

**Status:** 🎯 TO-BE

**Context:**
Current implementation uses singleton objects (Games, Players) with local file storage, preventing horizontal scaling and cloud deployment.

**Decision:**
Create Scala traits for all external services (GameRepository, SessionStore, SecretManager, etc.) with cloud-specific implementations.

**Consequences:**
- ✅ Cloud portability (swap implementations)
- ✅ Horizontal scaling (stateless design)
- ✅ Testability (mock implementations)
- ⚠️ More code (abstractions + implementations)
- ⚠️ Initial migration effort (2-3 weeks)

**Alternatives Considered:**
1. ❌ Keep singletons, use sticky sessions → Doesn't scale well
2. ❌ Use cloud-specific SDKs directly → Vendor lock-in

### ADR-002: PostgreSQL for Game Persistence

**Status:** 🎯 TO-BE

**Context:**
Need shared persistence across multiple server instances, replacing local file storage.

**Decision:**
Use PostgreSQL with JSONB columns for game state, accessed via Doobie (functional SQL library).

**Consequences:**
- ✅ ACID transactions for consistency
- ✅ Works on AWS RDS, GCP Cloud SQL, Azure Database
- ✅ Mature, battle-tested technology
- ✅ Query game history, analytics
- ⚠️ Managed service cost (~$30-80/month)
- ⚠️ Slightly slower than in-memory (acceptable)

**Alternatives Considered:**
1. ❌ DynamoDB/Firestore/CosmosDB → Vendor lock-in, higher cost
2. ❌ Keep file storage with NFS → Complex, not serverless-compatible
3. ❌ CockroachDB → Overkill for single-region deployment

### ADR-003: Redis for Session State

**Status:** 🎯 TO-BE

**Context:**
Auth tokens and player sessions need fast, distributed storage.

**Decision:**
Use Redis for session state, accessed via Redis4Cats.

**Consequences:**
- ✅ Fast (<1ms latency)
- ✅ Works on AWS ElastiCache, GCP Memorystore, Azure Cache
- ✅ Built-in TTL for session expiration
- ✅ Battle-tested for session storage
- ⚠️ Managed service cost (~$15-50/month)

**Alternatives Considered:**
1. ❌ Database for sessions → Too slow
2. ❌ In-memory with sticky sessions → Doesn't scale

### ADR-004: GraalVM Native Image for Lambda

**Status:** 🎯 TO-BE (Optional optimization)

**Context:**
JVM cold starts on Lambda are 2-5 seconds, unacceptable for user-facing API.

**Decision:**
Use GraalVM Native Image compilation for Lambda deployment to achieve <200ms cold starts.

**Consequences:**
- ✅ 10-25x faster cold starts (2-5s → 50-200ms)
- ✅ 60% cost reduction (lower memory, faster execution)
- ✅ http4s and Cats Effect are compatible
- ⚠️ Longer build times (5-10 minutes)
- ⚠️ Larger artifacts (50-80MB vs 15MB)
- ⚠️ Some reflection configuration needed

**Alternatives Considered:**
1. ⚠️ AWS SnapStart → 60% improvement, easier, but not as fast
2. ❌ Scala Native → Ecosystem too limited (no http4s)
3. ❌ Provisioned concurrency → $30/month extra cost

### ADR-005: Container-First Deployment

**Status:** 🎯 TO-BE

**Context:**
Need to support Lambda, Cloud Run, Container Apps with same codebase.

**Decision:**
Package as Docker container, use platform-specific base images.

**Consequences:**
- ✅ Same artifact works on Lambda, Cloud Run, Container Apps, Kubernetes
- ✅ Local development identical to production
- ✅ Easy migration between clouds (change base image only)
- ⚠️ Slightly more complex than ZIP deployment

**Alternatives Considered:**
1. ❌ Platform-specific packages (ZIP, JAR) → Different build per cloud

### ADR-006: Infrastructure as Code with Terraform

**Status:** 🎯 TO-BE

**Context:**
Need reproducible, version-controlled infrastructure across multiple clouds.

**Decision:**
Use Terraform with separate modules for AWS, GCP, Azure.

**Consequences:**
- ✅ Infrastructure versioned with code
- ✅ Multi-cloud support (same tool)
- ✅ Preview changes before applying
- ✅ Destroy and recreate easily
- ⚠️ Learning curve for Terraform

**Alternatives Considered:**
1. ⚠️ Pulumi (Scala) → Nice, but smaller community
2. ❌ AWS CDK → AWS-only, not multi-cloud
3. ❌ Manual setup → Not reproducible

---

## 10. Quality Requirements

### 10.1 Quality Tree

```
Quality
│
├── Portability (Priority 1)
│   ├── Cloud Independence
│   │   └── Requirement: Deploy to AWS, GCP, or Azure without code changes
│   │       Status: 🎯 TO-BE (1-2 weeks per new cloud)
│   ├── Data Portability
│   │   └── Requirement: Database migration between clouds
│   │       Status: 🎯 TO-BE (PostgreSQL dump/restore)
│   └── Development Portability
│       └── Requirement: Run identical stack locally
│           Status: 🎯 TO-BE (Docker Compose + TestContainers)
│
├── Scalability (Priority 2)
│   ├── Horizontal Scaling
│   │   └── Requirement: Support 10,000+ concurrent games
│   │       Status: ❌ AS-IS (single instance), 🎯 TO-BE (stateless, DB-backed)
│   ├── Auto-Scaling
│   │   └── Requirement: Scale from 0 to peak automatically
│   │       Status: 🎯 TO-BE (Lambda/Cloud Run)
│   └── Database Scalability
│       └── Requirement: Read replicas for scaling reads
│           Status: 🎯 Future (Aurora/Cloud SQL support)
│
├── Performance (Priority 3)
│   ├── Cold Start
│   │   └── Requirement: <200ms cold start (Lambda)
│   │       Status: 🎯 TO-BE (Native Image)
│   ├── Warm Latency
│   │   └── Requirement: <50ms response time
│   │       Status: ✅ AS-IS (10-30ms), ✅ TO-BE (similar)
│   └── Database Performance
│       └── Requirement: <10ms query latency (p95)
│           Status: 🎯 TO-BE (connection pooling, indexes)
│
├── Cost Efficiency (Priority 4)
│   ├── Compute Cost
│   │   └── Requirement: <$100/month for 10,000 games
│   │       Status: 🎯 TO-BE ($40-55/month with Lambda Native)
│   ├── Storage Cost
│   │   └── Requirement: <$30/month for database
│   │       Status: 🎯 TO-BE (Aurora Serverless v2)
│   └── Idle Cost
│       └── Requirement: Near-zero cost when unused
│           Status: 🎯 TO-BE (serverless scales to zero)
│
└── Maintainability (Priority 5)
    ├── Code Quality
    │   └── Requirement: Pass static analysis
    │       Status: ✅ AS-IS (WartRemover, Scalafix)
    ├── Test Coverage
    │   └── Requirement: >80% coverage for business logic
    │       Status: ✅ AS-IS (comprehensive test suite)
    └── Documentation
        └── Requirement: Up-to-date arc42 documentation
            Status: 🎯 TO-BE (this document)
```

### 10.2 Quality Scenarios

#### Scenario 1: Migrate from AWS to GCP

**Context:** Company policy changes, need to move from AWS to GCP.

| Aspect | Target | AS-IS | TO-BE |
|--------|--------|-------|-------|
| **Effort** | <2 weeks | ❌ Impossible (monolithic) | ✅ 1-2 weeks |
| **Code Changes** | <500 lines | ❌ Rewrite everything | ✅ 600 lines (3 adapters) |
| **Testing** | Full test suite passes | ❌ N/A | ✅ Same tests work |
| **Downtime** | <1 hour | ❌ N/A | ✅ Blue-green deployment |
| **Data Migration** | Database export/import | ❌ Custom file format | ✅ pg_dump / pg_restore |

**Steps:**
1. Implement 3 GCP adapters (~200 lines each)
2. Update Terraform provider
3. Change Dockerfile base image
4. Set `CLOUD_PROVIDER=gcp`
5. Export PostgreSQL from AWS RDS
6. Import PostgreSQL to GCP Cloud SQL
7. Deploy container to Cloud Run
8. Update DNS

#### Scenario 2: Handle Traffic Spike (100x normal)

**Context:** Game goes viral on social media, traffic spikes from 100 to 10,000 concurrent users.

| Aspect | Target | AS-IS | TO-BE |
|--------|--------|-------|-------|
| **Response Time** | <1s for 95% of requests | ❌ Server crashes | ✅ Auto-scales, maintains latency |
| **Scaling Time** | <2 minutes to handle spike | ❌ Manual intervention | ✅ Automatic (serverless) |
| **Cost Increase** | Linear with usage | ❌ Fixed cost, can't scale | ✅ Pay per request |
| **Failure Mode** | Graceful degradation | ❌ Complete outage | ✅ Queuing, retries |

**AS-IS Behavior:**
1. Single server gets overwhelmed
2. Memory fills up with game state
3. Requests start timing out
4. Server crashes
5. All games lost

**TO-BE Behavior:**
1. API Gateway queues excess requests
2. Lambda/Cloud Run auto-scales to 100s of instances
3. Database connection pool manages connections
4. Redis handles session lookups (fast)
5. Response time stays under 1s
6. Cost increases proportionally ($40 → $400/month during spike)

#### Scenario 3: Database Failure

**Context:** Primary database instance fails.

| Aspect | Target | AS-IS | TO-BE |
|--------|--------|-------|-------|
| **Detection** | <30s | ❌ Manual | ✅ Health checks fail |
| **Failover** | <2 minutes | ❌ Manual restart | ✅ Automatic (Multi-AZ RDS) |
| **Data Loss** | Zero | ❌ Last save interval | ✅ Zero (synchronous replication) |
| **Availability** | 99.9% | ❌ ~95% | ✅ 99.9% (managed service SLA) |

**TO-BE Behavior:**
1. Primary database fails
2. RDS/Cloud SQL detects failure (30s)
3. Automatic failover to standby (1-2 min)
4. Connection string points to new primary
5. Application retries failed requests
6. Service restored with zero data loss

---

## 11. Risks and Technical Debt

### 11.1 Current Technical Debt (AS-IS)

| Debt Item | Impact | Effort to Fix | Status |
|-----------|--------|---------------|--------|
| **Singleton State** | ❌ Cannot scale horizontally | Medium (2-3 weeks) | 🔄 Planned |
| **Local File Storage** | ❌ Not shared across instances | Medium (2 weeks) | 🔄 Planned |
| **No Database** | ❌ No backup, no queries | Medium (1 week) | 🔄 Planned |
| **In-Memory Sessions** | ❌ Lost on restart | Low (1 week) | 🔄 Planned |
| **No Graceful Shutdown** | ⚠️ Games lost on restart | Low (1 day) | 📋 Backlog |
| **GET for State Changes** | ⚠️ Not RESTful, caching issues | Medium (1 week) | 📋 Backlog |
| **Unsafe Collection Ops** | ⚠️ `.head`, `.last` can throw | Low (2-3 days) | 📋 Backlog |

### 11.2 Risks (TO-BE Migration)

#### Risk 1: Database Performance

**Description:** PostgreSQL might be slower than in-memory state for game lookups.

| Aspect | Details |
|--------|---------|
| **Probability** | Medium (30%) |
| **Impact** | Low (acceptable latency increase) |
| **Mitigation** | Connection pooling, indexes, caching |
| **Acceptance Criteria** | <50ms p95 latency for game load |

**Mitigation Steps:**
1. Add database indexes on `game_id`, `status`
2. Use HikariCP connection pool (10-20 connections)
3. Add Redis caching for frequently accessed games
4. Monitor slow queries with CloudWatch/pgBadger

#### Risk 2: Native Image Compilation Issues

**Description:** Circe or http4s might have reflection issues with GraalVM Native Image.

| Aspect | Details |
|--------|---------|
| **Probability** | Low (20%) - http4s is tested with native image |
| **Impact** | Medium (fall back to JVM + SnapStart) |
| **Mitigation** | Start with JVM deployment, add native later |
| **Fallback** | Use AWS SnapStart (still 60% improvement) |

**Mitigation Steps:**
1. Phase 1: Deploy JVM Lambda with SnapStart
2. Phase 2: Build native image locally
3. Phase 3: Test native image thoroughly
4. Phase 4: Deploy native only if successful

#### Risk 3: Cost Overrun

**Description:** Cloud costs exceed budget due to misconfiguration.

| Aspect | Details |
|--------|---------|
| **Probability** | Low (20%) |
| **Impact** | High (unexpected bills) |
| **Mitigation** | AWS Budgets, cost alerts, auto-shutdown |
| **Prevention** | Start with low limits, scale gradually |

**Mitigation Steps:**
1. Set AWS Budget alerts at $50, $100, $200
2. Use Aurora Serverless v2 (scales to 0.5 ACU)
3. Set Lambda reserved concurrency limit (100)
4. Monitor costs daily during first month
5. Tag all resources for cost allocation

#### Risk 4: Vendor Lock-In (Accidental)

**Description:** Developer uses cloud-specific SDK in business logic.

| Aspect | Details |
|--------|---------|
| **Probability** | Medium (40%) - easy mistake |
| **Impact** | High (defeats multi-cloud goal) |
| **Mitigation** | Code reviews, linting rules, architecture tests |
| **Prevention** | Clear guidelines, trait-only dependencies |

**Prevention Measures:**
1. ArchUnit tests: "Business logic cannot depend on AWS SDK"
2. Code review checklist: "Are cloud SDKs behind traits?"
3. Developer documentation: "Always use abstractions"
4. Regular architecture audits

### 11.3 Migration Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Data Loss During Migration** | Low | Critical | Test migration process, backup everything |
| **Breaking API Changes** | Medium | High | Comprehensive integration tests |
| **Performance Regression** | Low | Medium | Load testing before production |
| **Increased Complexity** | High | Low | Good documentation (this doc) |

---

## 12. Glossary

| Term | Definition |
|------|------------|
| **3D Go** | Traditional Go game extended to a cubic lattice (3D board) |
| **Arc42** | Template for software architecture documentation |
| **AS-IS** | Current implementation (v0.7.16) |
| **Cats Effect** | Functional effect system for Scala (IO monad) |
| **Cold Start** | Time to start a serverless function from scratch |
| **DI** | Dependency Injection - providing dependencies from outside |
| **Doobie** | Functional database access library for Scala |
| **GraalVM** | Java VM with ahead-of-time compilation to native code |
| **http4s** | Functional HTTP server/client library for Scala |
| **Ko Rule** | Go rule preventing immediate recapture of a single stone |
| **Liberty** | Empty point adjacent to a stone or group |
| **Native Image** | Ahead-of-time compiled binary (vs JIT-compiled JVM bytecode) |
| **Redis4Cats** | Functional Redis client library for Cats Effect |
| **Singleton** | Global object with single instance (anti-pattern for scaling) |
| **SnapStart** | AWS Lambda feature that caches initialized JVM state |
| **Stateless** | Server doesn't store session data (enables horizontal scaling) |
| **Suicide Rule** | Go rule preventing moves that capture your own stones |
| **TO-BE** | Target architecture (multi-cloud capable) |
| **Trait** | Scala interface/mixin (used for service abstractions) |
| **Warm Start** | Serverless function execution reusing existing container |

---

## 13. Appendices

### Appendix A: Migration Roadmap

#### Phase 1: Abstractions (Week 1-2)

**Goal:** Create portable foundation with no cloud dependencies.

```
Status: 🔄 Ready to start

Tasks:
□ Define service trait interfaces
  □ GameRepository
  □ SessionStore
  □ SecretManager
  □ MetricsCollector
  □ ObjectStore

□ Implement PostgreSQL repository
  □ Add Doobie dependency
  □ Create schema
  □ Implement CRUD operations
  □ Unit tests with TestContainers

□ Implement Redis session store
  □ Add Redis4Cats dependency
  □ Implement set/get/delete
  □ Unit tests with TestContainers

□ Refactor server code
  □ Remove singleton objects
  □ Add constructor injection
  □ Wire dependencies at startup

Deliverables:
✓ All traits defined
✓ PostgreSQL + Redis implementations
✓ All tests passing
✓ Zero cloud-specific code
```

#### Phase 2: AWS Deployment (Week 3-4)

**Goal:** Deploy to AWS Lambda with database backend.

```
Status: 🎯 After Phase 1

Tasks:
□ Implement AWS adapters
  □ AWSSecretManager
  □ CloudWatchMetrics
  □ S3ObjectStore

□ Infrastructure setup
  □ Write Terraform modules
  □ Create RDS Aurora cluster
  □ Create ElastiCache Redis
  □ Create Lambda function

□ Deployment pipeline
  □ Docker build → ECR
  □ Terraform apply
  □ Integration testing

□ Monitoring setup
  □ CloudWatch dashboards
  □ Alerts for errors
  □ Cost monitoring

Deliverables:
✓ Working Lambda deployment
✓ Database-backed state
✓ Redis session store
✓ Monitoring in place
```

#### Phase 3: Native Image (Week 5-6, Optional)

**Goal:** Optimize cold starts with GraalVM Native Image.

```
Status: 🎯 Optional enhancement

Tasks:
□ Native image setup
  □ Add sbt-native-image plugin
  □ Configure reflection
  □ Build locally

□ Testing
  □ Test all endpoints
  □ Load testing
  □ Cost comparison

□ Deployment
  □ Update Dockerfile
  □ Deploy to Lambda
  □ Monitor cold starts

Deliverables:
✓ <200ms cold starts
✓ 60% cost reduction
✓ All functionality working
```

### Appendix B: Testing Matrix

| Test Type | AS-IS | TO-BE | Coverage |
|-----------|-------|-------|----------|
| **Unit Tests** | ✅ 4,091 LOC | ✅ Keep + expand | Domain: 100% |
| **Integration Tests** | ⚠️ Limited | 🎯 Add TestContainers | Repositories: 90% |
| **Contract Tests** | ❌ None | 🎯 For all adapters | Adapters: 100% |
| **E2E Tests** | ⚠️ Manual | 🎯 Automated | Critical paths: 80% |
| **Load Tests** | ❌ None | 🎯 Gatling | Performance: Key scenarios |
| **Cloud-Specific Tests** | ❌ N/A | 🎯 LocalStack/Emulators | AWS/GCP/Azure adapters |

### Appendix C: Cost Projections

#### Low Traffic (1,000 games/month)

| Cloud | AS-IS | TO-BE (Serverless) | Savings |
|-------|-------|-------------------|---------|
| **None (Local)** | $0 (dev only) | N/A | N/A |
| **AWS Lambda** | N/A | $33-40/mo | Baseline |
| **GCP Cloud Run** | N/A | $30-38/mo | 10% cheaper |
| **Azure Container Apps** | N/A | $34-42/mo | Similar |

#### Medium Traffic (10,000 games/month)

| Cloud | TO-BE Cost | Components |
|-------|-----------|------------|
| **AWS** | $60-80/mo | Lambda: $15, RDS: $30, Redis: $15, Other: $20 |
| **GCP** | $50-70/mo | Cloud Run: $12, Cloud SQL: $25, Memorystore: $12, Other: $16 |
| **Azure** | $55-75/mo | Container Apps: $13, Azure Database: $28, Azure Cache: $14, Other: $18 |

#### High Traffic (50,000 games/month)

| Cloud | TO-BE Cost | Break-even vs Always-On |
|-------|-----------|------------------------|
| **AWS** | $95-180/mo | Better than $250/mo ECS |
| **GCP** | $85-160/mo | Better than $220/mo GKE |
| **Azure** | $90-170/mo | Better than $230/mo AKS |

### Appendix D: Decision Log

| Date | Decision | Rationale | Status |
|------|----------|-----------|--------|
| 2025-11-24 | Multi-cloud strategy | Vendor independence, flexibility | 🎯 Planned |
| 2025-11-24 | Service abstractions | Enable cloud portability | 🎯 Planned |
| 2025-11-24 | PostgreSQL + Redis | Standard protocols, works everywhere | 🎯 Planned |
| 2025-11-24 | GraalVM Native Image | Eliminate cold starts | 🎯 Optional |
| 2025-11-24 | Container-first | Same artifact, multiple clouds | 🎯 Planned |
| 2025-11-24 | Terraform IaC | Multi-cloud support | 🎯 Planned |

---

## Document Information

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0 |
| **Date** | November 24, 2025 |
| **Author** | Go-3D Development Team |
| **Status** | Draft - Under Review |
| **Template** | arc42 Template v8.2 |
| **Next Review** | After Phase 1 completion |

---

**End of arc42 Documentation**

*This document provides a complete architectural view of the Go-3D server, clearly delineating the current monolithic architecture (AS-IS) from the target multi-cloud capable architecture (TO-BE), with a detailed migration path.*
