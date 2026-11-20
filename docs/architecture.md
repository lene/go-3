# Architecture

Domain model, server and client structure of go-3. Moved here from `CLAUDE.md`. For the
broader review (risks, state handling, security) see
[repository-functionality-architecture-report.md](repository-functionality-architecture-report.md).

## Architecture

### Core domain model (go3d package)

The game logic is split between two key classes:

- **`Goban`** (`src/main/scala/go3d/Goban.scala`): Represents the board state as a 3D array of
  stones. Handles:
  - Stone placement and validation
  - Liberty calculations (empty adjacent positions)
  - Connected stone detection (areas)
  - Capture logic (removing stones without liberties)
  - Board uses sentinel values at boundaries (size+2 array for size board)

- **`Game`** (`src/main/scala/go3d/Game.scala`): Manages game flow built on top of Goban. Handles:
  - Move history and turn management
  - Ko rule enforcement (cannot immediately recapture single stone)
  - Suicide rule (cannot place stone with no liberties unless it captures)
  - Capture tracking (maps move index to captured stones)
  - Game-over detection (two consecutive passes or board full)
  - Scoring (territory + captures)

Key domain types:
- `Position(x, y, z)`: Coordinates on the board (1-indexed)
- `Move(position, color)`: A stone placement
- `Pass(color)`: Passing a turn
- `Color`: Black, White, Empty, or Sentinel (board boundary)
- `Area`: A connected group of stones with the same color

### Server architecture (go3d.server package)

The server uses **http4s with Cats Effect** for async HTTP handling:

- **`GoServer`**: Main entry point, initializes HTTP server and game loading
- **`GoHttpService`** (`http4s/GoHttpService.scala`): Defines HTTP routes:
  - `GET /new/{size}` - Create new game
  - `GET /register/{gameId}/{color}` - Register player
  - `GET /status/{gameId}` - Get current game state
  - `GET /status/{gameId}/{moveCount}` - Game state after the first `moveCount` moves (replay)
  - `GET /set/{gameId}/{x}/{y}/{z}` - Place stone
  - `GET /pass/{gameId}` - Pass turn
  - `GET /openGames` - List available games
  - `GET /archived/{gameId}` - Archived game (local file, or a redirect to a pre-signed S3 URL)

  The AWS Lambda handler (`lambda/LambdaHandler.scala`) serves the same routes on top of
  `service/GameService`, with DynamoDB as the store and S3 as the archive.

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
encoders/decoders.
