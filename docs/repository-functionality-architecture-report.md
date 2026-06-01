# Repository Functionality and Architecture Report

Review date: 2026-06-01

## Scope

This report reviews the repository as it exists in the current working tree. It covers the user-facing functionality, the main runtime components, their interactions, persistence and API behavior, tests, operational tooling, and notable risks.

The repository is a Scala 3/SBT project named `go-3d`. It implements three-dimensional Go on a cubic lattice, with a standalone HTTP server, several clients, JSON save files, bot strategies, and a small Python analysis helper for bot-vs-bot result files.

Current working tree note: `Dockerfile` and `src/test/scala/go3d/server/TestServer.scala` were already modified before this report was written. Production sources compile, but the current test sources do not compile because `TestServer.scala` contains `GoHttpService(Teself. stPort)`.

## Executive Summary

The application lets two players play a 3D extension of Go. Stones are placed at `(x, y, z)` coordinates on an odd-sized cubic board. The rule engine supports alternating turns, passes, captures, suicide prevention, a simple ko check, game-over detection, legal move generation, and scoring by stones, captures, and surrounded territory.

The main architecture is split into:

- Domain model and rules in `src/main/scala/go3d`.
- Server state, JSON serialization, persistence, identity, and auth in `src/main/scala/go3d/server`.
- HTTP route handlers in `src/main/scala/go3d/server/http4s`.
- CLI and graphical clients in `src/main/scala/go3d/client`.
- LibGDX rendering support in `src/main/scala/go3d/client/gdx`.
- Tests across domain, server, HTTP, client, strategy, and serialization behavior in `src/test/scala`.
- Operational helpers in `Dockerfile`, `run-test-game.sh`, and `analyze_games.py`.

The codebase is small, direct, and mostly organized by responsibility. The most important architectural caveat is that server state is global mutable state, while the game engine presents new `Game`/`Goban` snapshots but still exposes mutable board arrays internally. That combination is workable for a small trusted server, but it is not concurrency-safe and can create stale cached board-derived values if board arrays are mutated after lazy values are evaluated.

## User-Facing Functionality

### 3D Go Game

The core product is a playable 3D Go variant:

- The board is a cube with 1-based coordinates.
- Board sizes are constrained to odd sizes from 3 to 25 inclusive.
- Stones are black (`@`) and white (`O`); empty points are spaces; sentinel border cells use `.` with the middle dot character in source.
- A point has up to six face-adjacent neighbors: +/- x, +/- y, +/- z. Diagonals are not neighbors.
- Black moves first.
- Players can set a stone or pass.
- The game ends after two consecutive passes or after the move list reaches `size * size * size`.

Implemented rule behavior:

- Reject bad colors.
- Reject positions outside the board.
- Reject occupied positions.
- Reject moves out of turn.
- Reject suicide unless the move captures.
- Capture connected opponent groups that have no liberties.
- Track captured stones by move index.
- Prevent immediate ko when the previous capture was a single stone and the next move tries to play that captured point.
- Generate possible legal moves for the active player.
- Score by stones on board plus captures plus enclosed empty territory.

Not currently represented as first-class game features:

- Komi.
- Handicap setup, despite `MaxHandicaps` existing as a constant.
- Resignation.
- Clocks.
- Ruleset selection.
- Superko beyond the current simple immediate ko check.
- Multi-player games beyond black/white, despite `MaxPlayers` and `DefaultPlayers` constants.

### Server

`go3d.server.GoServer` is the main server entry point. It supports:

- `--port`, defaulting to `6030`.
- `--save-dir`, defaulting to `saves`.
- `--benchmark`, which runs a random in-process benchmark game instead of starting the server.
- `--print-step-size`, used only for benchmark logging.

On startup, the server initializes file persistence and attempts to load active save files from the save directory. It then starts an http4s Ember server bound to `0.0.0.0`.

### HTTP API

The HTTP API is JSON-based, except for plain error strings in many failure cases. State-changing operations use GET requests.

| Endpoint | Auth | Behavior |
| --- | --- | --- |
| `GET /new/{size}` | No | Creates a new active game and returns `GameCreatedResponse`. |
| `GET /register/{gameId}/{color}` | No | Registers a player color and returns a bearer token in `PlayerRegisteredResponse`. |
| `GET /status/{gameId}` | Optional | Returns board state, legal moves if authenticated as active player, readiness, and game-over status. |
| `GET /status/{gameId}/d` | Optional | Same as status, with debug request info only when authorized. |
| `GET /set/{gameId}/{x}/{y}/{z}` | Required | Applies the authenticated player's move. |
| `GET /set/{gameId}/{x}/{y}/{z}/d` | Required | Applies a move and includes debug request info. |
| `GET /pass/{gameId}` | Required | Applies a pass for the authenticated player. |
| `GET /pass/{gameId}/d` | Required | Applies a pass and includes debug request info. |
| `GET /openGames` | No | Returns games with black registered and white not yet registered. |
| `GET /health` | No | Returns `1`. |

Auth is a custom `Authentication: Bearer {token}` header. The token is generated when a player registers.

Error mapping is centralized in `BaseHandler`:

- Bad board size, bad color, duplicate color, illegal move: `400`.
- Not ready to move: `400`.
- Unknown route/nonexistent game/no such element: `404`.
- Authorization errors: `401`.
- Game over on attempted move: `410`.
- Generic server exceptions and unknown failures: `500`.

### Clients

The repository provides three main client experiences:

1. `AsciiClient`
   - Interactive terminal client.
   - Can create a game, join a game, reconnect with a token, show status, set stones, pass, and exit.
   - Displays the 3D board as 2D slices laid out in text.

2. `BotClient`
   - CLI bot that polls until ready, chooses a move, sends it, and repeats.
   - Supports comma-separated strategies:
     - `random`
     - `closestToCenter`
     - `onStarPoints`
     - `closestToStarPoints`
     - `maximizeOwnLiberties`
     - `minimizeOpponentLiberties`
     - `maximizeDistance`
     - `prioritiseCapture`
   - Supports `--max-thinking-time-ms` by sampling a subset of moves based on previous thinking time.
   - Parses `--parallel`, but the current implementation constructs `SetStrategy(...)` directly rather than using `SetStrategy.create(...)`, so the option appears ineffective.

3. `GDXClient`
   - LibGDX/LWJGL desktop viewer.
   - Polls game status and renders a 3D board with stones, grid planes, star points, camera controls, rotation, zoom, and reset.
   - Currently watches games; it does not make moves.

### Bot Analysis Tooling

`run-test-game.sh` repeatedly runs two bot clients against a local server and records final score pairs into CSV files named:

```text
{blackStrategy}:{whiteStrategy}:{boardSize}.csv
```

`analyze_games.py` reads those CSV files and prints tables for:

- Number of games.
- Percentage of black wins.
- Average black score advantage.

The Python package metadata is named `analyze-games`. The Poetry script entry is currently `mixcloud_upload = "analyze-games:main"`, which appears unrelated and likely invalid because Python module names cannot contain hyphens.

## Architecture Overview

### High-Level Runtime Diagram

```text
Human CLI             Bot CLI              GDX viewer
AsciiClient           BotClient            GDXClient
     |                   |                     |
     +-------- BaseClient / HTTP JSON --------+
                         |
                    GoHttpService
                         |
      +------------------+------------------+
      |                  |                  |
  Route handlers      RequestInfo       BaseHandler
      |                  |                  |
      +------------------+------------------+
                         |
                    Games / Players
                         |
          +--------------+--------------+
          |                             |
       Game / Goban / Area          FileIO
       rules and scoring            JSON saves
```

### Package Map

| Package/path | Responsibility |
| --- | --- |
| `go3d` | Core game model, rules, board logic, exceptions, scoring. |
| `go3d.server` | Server-side state, players, IDs, auth request parsing, JSON codecs, save/load IO, responses. |
| `go3d.server.http4s` | HTTP routes and request handlers. |
| `go3d.client` | Shared client, CLI clients, bot strategy logic, utility board builders. |
| `go3d.client.gdx` | LibGDX rendering, geometry, input, camera, polling display. |
| `src/test/scala` | Unit and integration-style tests. |
| root scripts | Docker build/run, test-game automation, Python result analysis. |

## Core Domain Architecture

### Coordinates and Colors

`Position` is a 1-based coordinate triple. It rejects zero or negative coordinates at construction time. Upper-bound validation happens in `Goban.checkValid` and `Goban.setStone`.

`Delta` represents coordinate differences. Its `abs` is Manhattan distance, and neighbor detection depends on distance exactly `1`.

`Color` wraps one allowed character:

- Empty: space.
- Black: `@`.
- White: `O`.
- Sentinel border: the source uses a middle-dot sentinel.

The unary `!` operator flips black to white and white to black.

### Board Representation

`Goban` is the board representation:

- `size` is the playable edge length.
- `stones` is a 3D array of `Color` with dimensions `(size + 2)^3`.
- Index `1..size` in each dimension is playable.
- The outer border at index `0` and `size + 1` is initialized to sentinel values.

Important derived values:

- `allPositions`: every playable coordinate.
- `allNeighbors`: a lazy map from each position to neighboring moves/colors.
- `areas`: lazy connected same-color stone areas.

Most board transitions return new `Goban` instances using deep copies. However, the array is public and is directly mutated by serialization helpers and tests. Because `areas` and `allNeighbors` are lazy, direct mutation after those values have been evaluated can leave stale derived values.

### Game Snapshot

`Game` combines:

- Board size.
- Current `Goban`.
- Ordered `moves`, containing `Move` or `Pass`.
- `captures`, keyed by move index, storing captured stones.

`Game.makeMove` returns a new `Game`:

- Passing appends a `Pass`.
- Setting validates turn, board legality, suicide, and ko.
- It places the stone, captures neighbors, appends the move, and records captures.

`Game` is the main rule facade used by the server and clients.

### Move Validation and Captures

Move validation is split:

- `Game.checkValid` handles turn order and ko.
- `Goban.checkValid` handles board bounds, occupation, and suicide.

Capture flow:

1. `Game.setStone(move)` delegates to `Goban.setStone(move)`.
2. `Game.doCaptures` calls `captureNeighbors` for each adjacent position.
3. `Goban.checkAndClear` ignores empty/sentinel/friendly positions.
4. For opponent positions, it checks liberties.
5. If no liberties remain, it clears the connected opponent group.
6. `Game.doCaptures` compares the old and new board to determine captured stones.

Connected groups are found by recursively walking neighboring stones of the same color while temporarily marking a visited stone with the sentinel color to avoid revisiting.

### Legal Move Generation

`Game.possibleMoves(color)` returns legal positions for the next player only:

- Wrong color or non-active turn: empty list.
- Board already at max move count: empty list.
- Empty board: black gets all positions; white gets none.
- Otherwise, empty positions are filtered by `isPossibleMove`.

`isPossibleMove` avoids full validation for positions with an empty neighbor, and only invokes `checkValid` for positions without empty neighbors. This is an optimization around suicide/ko checks.

### Areas, Territory, and Scoring

`Area` represents a connected set of same-colored stones, tracks liberties, and computes:

- The axis-aligned outer hull.
- Positions inside that hull that are not the area's color.
- A simple liveness heuristic based on internal empty spaces.

`Game.score` computes:

1. Stone count for black and white.
2. Captures credited to the capturing color.
3. Empty connected areas.
4. If an empty area's boundary has exactly one color, that empty area is counted as territory for that color.

This is a practical scoring implementation for the project, but it is not a full formal ruleset engine.

## Server Architecture

### Global State

`Games` is a singleton object containing:

- `activeGames: mutable.Map[String, Game]`
- `archivedGames: mutable.Map[String, Game]`
- `fileIO: Option[FileIO]`

`Players` is another singleton:

- `activePlayers: mutable.Map[String, Map[Color, Player]]`

This design is simple and convenient, but it means:

- There is one in-memory game registry per JVM.
- Tests need careful setup/teardown.
- Concurrent requests can race because the mutable maps are not synchronized.
- Game IDs are random but collisions are not checked; a collision would overwrite an active game.

### Player Registration

Game creation and player registration are separate:

1. `/new/{size}` creates the game and returns a game ID.
2. `/register/{gameId}/{color}` creates a token and registers a color.
3. The client uses that token as a bearer token for status and moves.

`Players.openGames()` returns games with black registered and white absent. That matches the README flow where a black player starts a game and a white player joins from the open-games list.

The `ready` flag in `PlayerRegisteredResponse` has asymmetric behavior: it is true only when black registers after white is already present. If white registers second, the game is ready for black, but that registration response returns false. Subsequent `/status` calls are the reliable source of readiness.

### Persistence

`FileIO` writes JSON save files into the configured save directory. The saved object is:

```text
SaveGame(game: Game, players: Map[Color, Player])
```

Save behavior:

- New games are only in memory until the first move or pass.
- `Games.add(gameId, game)` saves the current game state.
- If the game is over, `Games.archive(gameId)` moves it from active to archived state, unregisters players, and moves the save file into `{saveDir}/archive`.

Startup behavior:

- `Games.loadGames(saveDir)` initializes `FileIO`.
- It reads `*.json` files in the save directory root.
- Each valid save restores player registrations and the game.
- If a restored game is already over, adding it archives it.

Archived games already in `{saveDir}/archive` are listed by ID, but they are not read back into the `archivedGames` in-memory map on startup.

### JSON Serialization

`Jsonify.scala` defines explicit Circe encoders/decoders for:

- `Color`
- `Position`
- `Move`
- `Move | Pass`
- `Goban`
- `Game`
- `Player`
- `SaveGame`
- response types

`Goban` JSON stores levels as strings, not as a nested numeric/color array. This makes save files more compact and readable, and tests verify conversion both ways.

The generic `GoResponse` encoder covers `StatusResponse`, `PlayerRegisteredResponse`, `ErrorResponse`, `GameCreatedResponse`, and `OpenGamesResponse`. `GameOverResponse` exists but is not covered by the encoder and appears unused.

### HTTP Handler Structure

The HTTP layer is intentionally thin:

- `GoHttpService` defines route patterns.
- `BaseHandler` wraps `handle` in exception-to-status mapping.
- `StartNewGame`, `RegisterPlayer`, `GetStatus`, `DoSet`, `DoPass`, and `ListOpenGames` each implement one operation.
- `MakeMove` contains the common logic for `set` and `pass`.

Move handling flow:

```text
HTTP request
  -> GoHttpService route
  -> DoSet or DoPass
  -> MakeMove.handle
  -> RequestInfo.mustGetPlayer
  -> Games(gameId)
  -> game.isOver / game.isTurn checks
  -> Game.makeMove
  -> Games.add saves and archives if needed
  -> StatusResponse
```

Status flow:

```text
HTTP request
  -> GetStatus
  -> RequestInfo
  -> Games(gameId)
  -> Optional token lookup
  -> If player found: possible moves and ready flag
  -> If unauthenticated: board state only, no legal moves
```

## Client Architecture

### Shared HTTP Client

`BaseClient` is a small wrapper around the HTTP API:

- `status`
- `set`
- `pass`
- `create`
- `register`

It uses `scala.io.Source.fromURL` for create/register and `requests.get` for status/set/pass. The requests-based calls have explicit 30-second timeouts; the `Source.fromURL` calls do not.

### CLI Parsing and Main Loop Base

`Client` provides a common `main` method and central exception handling.

`InteractiveClient` handles common CLI options:

- `--server`
- `--port`
- `--size`
- `--game-id`
- `--color`
- `--token`

It supports three modes:

- Create a new game with `--size` and `--color`.
- Join an existing game with `--game-id` and `--color`.
- Reconnect/watch with `--game-id` and `--token` or without auth.

### ASCII Client

`AsciiClient` loops as follows:

1. Print server/game/token info.
2. Poll until `ready`.
3. Print the current goban.
4. Read a command:
   - `set` or `s`
   - `pass` or `p`
   - `status` or `st`
   - `exit`
5. Send the request and repeat until game over or exit.

### Bot Client and Strategy Engine

`BotClient` extends the same client base but automates moves.

High-level bot loop:

1. Poll until ready.
2. Read legal moves from `StatusResponse`.
3. Narrow them through configured `SetStrategy` rules.
4. Randomly choose among equally best remaining positions.
5. Set the stone, or pass if no moves remain.
6. Repeat until game over.

`SetStrategy` applies strategies in sequence. Each strategy returns the subset of candidate positions tied for best according to its metric. Later strategies refine the tie set. This gives composable behavior such as:

```text
prioritiseCapture,closestToCenter,maximizeOwnLiberties
```

Supporting client extensions in `client/Game.scala` provide strategy metrics like total liberties, stones by color, free neighbors, connected areas, and liberties of an area.

### GDX Viewer

The GDX viewer is a polling renderer:

1. `GDXClient` creates a LWJGL window.
2. `GobanDisplay` polls `client.status` on a LibGDX timer.
3. When move count changes, it rebuilds stone renderables.
4. `GDXResources` renders grid, star points, and stones using a perspective camera.
5. `Go3DInputMultiplexer` combines the standard LibGDX camera controller with custom key controls.

`GeometryBuilder` builds reusable models for:

- Black stones.
- White stones.
- Horizontal and vertical grid planes.
- Star points.

## Build, Packaging, and Operations

### Scala/SBT

The main build uses:

- Scala `3.6.2`.
- SBT `1.10.7`.
- sbt-native-packager.
- JUnit 5 and ScalaTest.
- http4s Ember server/client.
- Circe JSON.
- Scallop CLI parser.
- requests HTTP client.
- LibGDX/LWJGL.
- scala-parallel-collections.
- logback and scala-logging.

`sbt universal:packageBin` produces the distributable ZIP described in the README.

### Docker

The Dockerfile is a multi-stage build:

1. Builder image copies the repo, runs `sbt "Universal / packageBin"`, unzips the distribution, and keeps generated `bin`/`lib`.
2. Runtime image sets `SAVE_DIR` and `PORT`, creates a `go-3d` user, copies the app, exposes the port, starts the server, and defines `/health` as healthcheck.

This report did not verify Docker build success. The current Dockerfile is also one of the pre-existing modified files.

### Bot Match Automation

`run-test-game.sh` assumes a packaged ZIP and a running local server. It:

- Unzips the distribution.
- Starts a black bot in the background.
- Finds an open game with `/openGames`.
- Starts a white bot.
- Appends parsed score output to a CSV in `results/`.

## Test Coverage

The test suite is broad for a small project. It covers:

- Constants, colors, positions, deltas, and moves.
- Board initialization, sentinel borders, bounds, deep copies, equality, and string builders.
- Neighbor detection in corners, edges, faces, and interior positions.
- Liberty counting for individual stones, connected groups, and total liberties.
- Capture logic including connected captures and multiple disjoint captures.
- Suicide and capture-into-eye behavior.
- Simple ko detection.
- Game over by passes and full move count.
- Legal move generation.
- Territory scoring.
- Area hulls, inside-area detection, and liveness heuristics.
- JSON codecs for domain objects, saves, and responses.
- File IO saves, path traversal checks, archive conflict checks.
- ID generation.
- HTTP routes, status codes, auth requirements, debug paths, open games, and health.
- Client arg parsing.
- Base auth headers.
- Bot strategy behavior, including parallel strategy variants.
- Star point generation.
- LibGDX version availability, with full app instantiation disabled.

Disabled tests:

- `TestArea.testInside10ConnectedStones` is disabled as "Needs investigation".
- `TestSetStrategy.testParallelization` is disabled as a manual hack.
- `TestLibGDX.testInstantiateClient` is disabled because repeated SBT runs can hit native library loading issues and the app does not have a clean stop path.

Verification performed during this review:

- `sbt Compile/compile`: passed.
- `sbt Test/compile`: failed because `src/test/scala/go3d/server/TestServer.scala:31` references `Teself`, which is not defined.

## Main Risks and Maintenance Notes

### Current Test Tree Does Not Compile

`src/test/scala/go3d/server/TestServer.scala` currently has:

```scala
GoHttpService(Teself. stPort)
```

This prevents `sbt Test/compile` from succeeding. Because this file was already modified in the working tree, this report does not alter it.

### Global Mutable Server State

`Games` and `Players` are process-global mutable maps. There is no locking or compare-and-swap behavior around updates. Concurrent requests against the same game can race, especially if two move requests arrive close together.

For a low-traffic local game server this may be acceptable. For a hosted service, game updates should be serialized per game ID or moved behind an effectful/ref-based state model.

### Mutable Board Internals and Lazy Caches

`Goban` mostly behaves like an immutable value because `setStone` deep-copies arrays. However, its `stones` array is public and several helpers mutate it directly. Since `Goban` also has lazy caches such as `areas` and `allNeighbors`, direct mutation after lazy evaluation can make board-derived results stale.

This is one of the most important internal correctness hazards.

### GET Requests Mutate State

`/set`, `/pass`, `/new`, and `/register` are all GET endpoints. This is convenient from simple clients and shell commands, but it makes state changes cacheable/bookmarkable/replayable in ways that violate normal HTTP expectations.

### Authentication Is Minimal

Bearer tokens are generated and checked, but:

- Tokens are logged at registration.
- There is no TLS handling in the app itself.
- Debug endpoints can echo request headers when authorized.
- There is no token revocation except game archive/unregister.

This is suitable for trusted/local play, not for an exposed internet service without a reverse proxy and tightened logging.

### Archive Reload Is Limited

The server lists archived game IDs from disk, but startup only reads active root-level `*.json` files. Previously archived games under `{saveDir}/archive` are not restored into the in-memory `archivedGames` map on startup.

### Game ID Collision Handling

IDs are six base62 characters. The collision probability is low for small deployments, but `Games.register` does not retry if a generated ID already exists.

### Bot Parallel Option Appears Ineffective

`BotClient` parses `--parallel`, but `mainLoop` constructs `SetStrategy(game.size, strategies, maxThinkingTimeMs)` directly. It does not pass `parallel` to `SetStrategy.create(...)`. As written, CLI users likely cannot enable `ParallelSetStrategy`.

### Rule Completeness

The rule engine is sufficient for this game variant, but it is not a full configurable Go rules engine. In particular, ko is a simple immediate recapture check, scoring is custom territory logic, and features such as komi, handicap setup, and resignation are absent.

### Performance Scaling

The implementation favors clarity over scalability:

- Boards can be as large as 25^3 points.
- Move generation can scan all empty positions.
- Board updates deep-copy the 3D array.
- Bot strategies can simulate many candidate moves.

This is fine for smaller boards and local play, but larger boards plus bots can become expensive.

## Suggested Architecture Improvements

1. Fix the current test compile break in `TestServer.scala`.
2. Encapsulate `Goban.stones` and replace direct mutation helpers with constructors/builders that do not invalidate lazy caches.
3. Serialize per-game server updates or store games in a concurrency-safe state abstraction.
4. Change mutating HTTP operations to POST while keeping compatibility wrappers if needed.
5. Make registration readiness semantics consistent, or document that `/status` is authoritative.
6. Make `BotClient --parallel` call `SetStrategy.create(...)`.
7. Decide whether archived games should be loadable after restart and implement archive loading if yes.
8. Add ID collision retry logic in `Games.register`.
9. Replace `Source.fromURL` client calls with the same requests/http client used elsewhere so all network calls have timeouts.
10. Clarify or fix the Poetry script entry in `pyproject.toml`.

## Bottom Line

This repository implements a complete playable 3D Go system with a compact domain model, a simple HTTP server, terminal/bot/3D-viewer clients, JSON persistence, and meaningful tests. The core production code compiles in the current working tree. The biggest architectural constraints are mutable global server state, mutable board internals with lazy derived data, basic authentication/HTTP semantics, and the currently broken test compile caused by an uncommitted typo in `TestServer.scala`.
