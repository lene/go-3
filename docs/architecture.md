# Architecture

Domain model, server and client structure of go-3. Moved here from `CLAUDE.md`. For the
broader review (risks, state handling, security) see
[repository-functionality-architecture-report.md](repository-functionality-architecture-report.md).

## Architecture

### Core domain model (go3d package)

The game logic is split between two key classes:

- **`Goban`** (`src/main/scala/go3d/Goban.scala`): Represents the board state as a 3D array of
  stones, an `IArray` that cannot be changed: `setStone` returns a new board that shares the rows
  it does not change, and `Goban.tabulate` builds a whole board (for `Jsonify` and tests). Handles:
  - Stone placement and validation
  - Liberty calculations (empty adjacent positions)
  - Connected stone detection (areas)
  - Capture logic (removing stones without liberties)
  - Board uses sentinel values at boundaries (size+2 array for size board)
  - `Groups` (`Groups.scala`): the liberties of every group, found in one pass over a board.
    A board computes it once, as a `lazy val`, the first time a point without an empty neighbour
    is checked for suicide; all later checks on that board, from `possibleMoves` and
    `checkValid`, share it
  - Rules tests: `TestRulesAgainstReference` compares `Game` move by move with the plain
    `ReferenceRules`; `EngineBenchmark` times the engine (see `docs/development.md`)

- **`Game`** (`src/main/scala/go3d/Game.scala`): Manages game flow built on top of Goban. Handles:
  - Move history and turn management
  - Ko rule enforcement (cannot immediately recapture single stone)
  - Suicide rule: a move that leaves its own group without liberties is illegal unless it captures
  - Capture tracking (maps move index to captured stones)
  - Game-over detection (two consecutive passes or board full)
  - Scoring (stones + territory, Tromp-Taylor area scoring: captures are tracked but not scored)
  - Komi: points added to White's score, set when the game is created (default 0.5) and stored
    in the game JSON; `Game.winner` compares White's score plus komi with Black's once the game is
    over. Only half-integers up to size³ are valid (`Komi.scala`), so a game never ties
  - The status response carries the winner: `StatusResponse` derives `over` from its game, and has
    a `winner` if and only if the game is over, the server's `Game.winner`

Key domain types:
- `Position(x, y, z)`: Coordinates on the board (1-indexed)
- `Move(position, color)`: A stone placement
- `Pass(color)`: Passing a turn
- `Color`: Black, White, Empty, or Sentinel (board boundary)
- `Area`: A connected group of stones with the same color

The AlphaZero engine's network, trained in Python (`training/`), is exchanged with the JVM as
ONNX; what it sees and answers is in [model-contract.md](model-contract.md).
The sbt subproject `engine` (`engine/`, package `go3d.engine`) loads a generation with ONNX
Runtime and searches with it; `BotClient` finds it through the `MoveEngine` hook of the root
project (`client/MoveEngine.scala`, a `java.util.ServiceLoader` service), so root never depends
on it and the Lambda jar stays free of ONNX Runtime.
`SelfPlay` in the same subproject plays a generation against itself and writes the training
records of [self-play-records.md](self-play-records.md). `train-generation`
(`training/go3d_training/train.py`) trains a candidate for the next generation from those
records: `records.py` turns them into samples, `symmetry.py` augments them with the cube's 48
symmetries, and `export.py` writes the ONNX model with its checkpoint.
`store.py` holds the layout of the model store (promoted generations, `candidates/`, `rejected/`
and the match log) and of the records. `checkpoint.py` saves and loads `model.pt`, the network
config and its weights. `Match` (with `MatchGame`, one search per colour) plays a candidate
against the generation before it and promotes it or sets it aside; `promotion-loop`
(`training/go3d_training/loop.py`) chains self-play, training and the match, each a process of
its own, and works out its next step from the store. `Cli` and `Parallel` hold what `SelfPlay`
and `Match` share: the command-line handling, and the games played at the same time.

### Server architecture (go3d.server package)

The server uses **http4s with Cats Effect** for async HTTP handling:

- **`GoServer`**: Main entry point, initializes HTTP server and game loading
- **`GoHttpService`** (`http4s/GoHttpService.scala`): Defines HTTP routes:
  - `GET /new/{size}[?komi=k]` - Create new game; `k` is a half-integer such as `6.5` or `-0.5`,
    default `0.5`; anything else, or `komi` more than once, is a 400
  - `GET /register/{gameId}/{color}` - Register player
  - `GET /status/{gameId}` - Get current game state; `over` says whether the game is over, and
    `winner` (a colour, like `playerColor`) is there if and only if it is
  - `GET /status/{gameId}/{moveCount}` - Game state after the first `moveCount` moves (replay)
  - `GET /set/{gameId}/{x}/{y}/{z}` - Place stone
  - `GET /pass/{gameId}` - Pass turn
  - `GET /openGames` - List the games waiting for a white player: `ids`, and `games`, an
    `{id, komi}` per id in the same order, so a player sees the komi before joining
  - `GET /archived/{gameId}` - Archived game (local file, or a redirect to a pre-signed S3 URL)

  The AWS Lambda handler (`lambda/LambdaHandler.scala`) serves the same routes on top of
  `service/GameService`, with DynamoDB as the store and S3 as the archive. A finished game whose
  archive failed stays without an archive key; the manually invoked
  `lambda/ArchiveRecoveryHandler.scala` (`GameService.recoverArchives`) archives such games later.

- **Request handlers** (`http4s/` subpackage): Each route has a handler class (e.g.,
  `StartNewGame`, `DoSet`, `DoPass`, `RegisterPlayer`) that extends `BaseHandler` and implements
  authentication/authorization

- **State management**:
  - `Games`: Singleton managing active/archived games and file I/O
  - `Players`: Singleton tracking registered players and authentication tokens
  - Game state is persisted as JSON files in the save directory

Authentication: Bearer tokens in `Authentication` header for move requests.

### Client architecture (go3d.client package)

- **`BaseClient`**: Core HTTP client for server communication, handles token-based auth
- **`InteractiveClient`**: Base trait for clients that poll server and wait for turn
- **`AsciiClient`**: Terminal-based interactive client with command loop (set/pass/status/exit)
- **`GDXClient`**: 3D visualization using libGDX (OpenGL), watch-only mode
  - `GobanDisplay`: Main render loop
  - `GeometryBuilder`: Constructs 3D geometry for stones and board
  - `Go3DInputController`: Camera controls (rotation/zoom)
  - `ParticleMarker`: Visual effects for marking positions
- **`BotClient`**: Automated player using strategy pattern

### Bot strategy system (`SetStrategy.scala`)

Bots use a pipeline of strategies to narrow down move selection:
- Each strategy filters/ranks possible moves
- Strategies are applied sequentially (e.g., "prioritiseCapture,closestToCenter")
- Available strategies: random, closestToCenter, onStarPoints, closestToStarPoints,
  maximizeOwnLiberties, minimizeOpponentLiberties, maximizeDistance, prioritiseCapture
- `ThinkingTimeLimiter`: Optionally limits computation time by sampling moves

## Key implementation details

### Liberty calculation
Liberties are empty positions adjacent to a stone or connected group. The algorithm marks checked
stones with a `Sentinel` value to avoid infinite recursion on connected groups.

### Capture logic
After each move, the board checks all neighboring positions. If an opponent's group has zero
liberties, the entire connected group is removed (see `Goban.checkAndClear` and
`Game.doCaptures`).

### Board representation
The board is a 3D array of size `(size+2) × (size+2) × (size+2)` with:
- Actual play area: indices 1 to size
- Boundary: index 0 and size+1 (marked as `Sentinel`)
This simplifies neighbor checking by avoiding bounds checks.

### JSON serialization
Uses Circe for encoding game state, moves, and API responses. See `server/Jsonify.scala` for
encoders/decoders. A game's `komi` is a number field of the game JSON, so every store keeps it
(files, DynamoDB, the S3 archive); a stored game without it decodes with komi 0.5.

A status response has `over` always and `winner` (in the format of `playerColor`, from
`Game.winner`) if and only if the game is over, also for a replay at move `n`. The decoder rejects a
document whose `over` differs from its game's, or that has a `winner` while not over. It keeps the
server's `winner` without comparing it with the client's own scoring, so a client keeps working when
the server's scoring changes, and gives `over: true` without `winner`, which is how responses from
before the winner existed look, the winner of the game. The winner is not stored: the
game JSON stays the single source, and the winner follows from the game and its komi. No scores are
in the response.
