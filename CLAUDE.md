# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a 3D Go (Weiqi/Baduk) game implementation where the traditional 2D board is extended into three dimensions on a cubic lattice. The project consists of a server that manages game state and multiple client types (ASCII, 3D graphical, bot) that connect to play games.

## Build System and Commands

### Building
```bash
# Build the project (compiles and packages)
sbt universal:packageBin

# Compile only
sbt compile

# Clean build artifacts
sbt clean
```

The `universal:packageBin` command creates `target/universal/go-3d-<VERSION>.zip` containing executables in `bin/` and libraries in `lib/`.

### Testing
```bash
# Run all tests
sbt test

# Run a specific test class
sbt "testOnly go3d.TestGoban"

# Run with test coverage
sbt clean coverage test coverageReport
```

### Running Components

#### Server
```bash
# From sbt
sbt "runMain go3d.server.GoServer --port 6030 --save-dir saves"

# Benchmark mode (runs random games for performance testing)
sbt "runMain go3d.server.GoServer --benchmark 5 --print-step-size 10"
```

#### Clients
```bash
# ASCII client (for playing)
sbt "runMain go3d.client.AsciiClient --server localhost --port 6030 --size 7 --color b"
sbt "runMain go3d.client.AsciiClient --server localhost --port 6030 --game-id XXXXX --color w"

# GDX client (3D visualization, watch-only)
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id XXXXX"

# Bot client
sbt "runMain go3d.client.BotClient --server localhost --port 6030 --size 7 --color b --strategy prioritiseCapture,closestToCenter"
```

### Docker
```bash
# Build Docker image
docker build -t registry.gitlab.com/lilacashes/go-3/server .

# Run server in Docker
docker run --net=host --env PORT=6030 --env SAVE_DIR=saves -t registry.gitlab.com/lilacashes/go-3/server:latest
```

## Architecture

### Core Domain Model (go3d package)

The game logic is split between two key classes:

- **`Goban`** (`src/main/scala/go3d/Goban.scala`): Represents the board state as a 3D array of stones. Handles:
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

### Server Architecture (go3d.server package)

The server uses **http4s with Cats Effect** for async HTTP handling:

- **`GoServer`**: Main entry point, initializes HTTP server and game loading
- **`GoHttpService`** (`http4s/GoHttpService.scala`): Defines HTTP routes:
  - `GET /new/{size}` - Create new game
  - `GET /register/{gameId}/{color}` - Register player
  - `GET /status/{gameId}` - Get current game state
  - `GET /set/{gameId}/{x}/{y}/{z}` - Place stone
  - `GET /pass/{gameId}` - Pass turn
  - `GET /openGames` - List available games

- **Request Handlers** (`http4s/` subpackage): Each route has a handler class (e.g., `StartNewGame`, `DoSet`, `DoPass`, `RegisterPlayer`) that extends `BaseHandler` and implements authentication/authorization

- **State Management**:
  - `Games`: Singleton managing active/archived games and file I/O
  - `Players`: Singleton tracking registered players and authentication tokens
  - Game state is persisted as JSON files in the save directory

Authentication: Bearer tokens in `Authentication` header for move requests.

### Client Architecture (go3d.client package)

- **`BaseClient`**: Core HTTP client for server communication, handles token-based auth
- **`InteractiveClient`**: Base trait for clients that poll server and wait for turn
- **`AsciiClient`**: Terminal-based interactive client with command loop (set/pass/status/exit)
- **`GDXClient`**: 3D visualization using libGDX (OpenGL), watch-only mode
  - `GobanDisplay`: Main render loop
  - `GeometryBuilder`: Constructs 3D geometry for stones and board
  - `Go3DInputController`: Camera controls (rotation/zoom)
  - `ParticleMarker`: Visual effects for marking positions
- **`BotClient`**: Automated player using strategy pattern

### Bot Strategy System (`SetStrategy.scala`)

Bots use a pipeline of strategies to narrow down move selection:
- Each strategy filters/ranks possible moves
- Strategies are applied sequentially (e.g., "prioritiseCapture,closestToCenter")
- Available strategies: random, closestToCenter, onStarPoints, closestToStarPoints, maximizeOwnLiberties, minimizeOpponentLiberties, maximizeDistance, prioritiseCapture
- `ThinkingTimeLimiter`: Optionally limits computation time by sampling moves

## Code Conventions

- **Scala 3.3.1** with strict compiler flags (`-deprecation`, `-explain`, `-feature`)
- Functional style preferred but pragmatic: mutable collections used in performance-critical paths (see `Goban.connectedStones`)
- Tail recursion (`@tailrec`) for iterative algorithms
- Case classes for immutability in domain model (except `Goban` which copies arrays)
- Comprehensive test coverage using ScalaTest
- Lines have a maximum length of 100 characters

## Key Implementation Details

### Liberty Calculation
Liberties are empty positions adjacent to a stone or connected group. The algorithm marks checked stones with a `Sentinel` value to avoid infinite recursion on connected groups.

### Capture Logic
After each move, the board checks all neighboring positions. If an opponent's group has zero liberties, the entire connected group is removed (see `Goban.checkAndClear` and `Game.doCaptures`).

### Board Representation
The board is a 3D array of size `(size+2) × (size+2) × (size+2)` with:
- Actual play area: indices 1 to size
- Boundary: index 0 and size+1 (marked as `Sentinel`)
This simplifies neighbor checking by avoiding bounds checks.

### JSON Serialization
Uses Circe for encoding game state, moves, and API responses. See `server/Jsonify.scala` for encoders/decoders.

# Testing and Development Workflow

## Starting a Game Session

### 1. Start the server (in background)
```bash
sbt "runMain go3d.server.GoServer --port 6030 --save-dir saves" &
```

### 2. Create a new game
```bash
curl -s http://localhost:6030/new/5  # Creates 5x5x5 game
# Returns: {"id":"XXXXXX","size":5}
```

### 3. Register players (save the tokens!)
```bash
# Black player
curl -s "http://localhost:6030/register/GAME_ID/@"
# Returns: {...,"color":{"color":"@"},"authToken":"TOKEN1",...}

# White player
curl -s "http://localhost:6030/register/GAME_ID/O"
# Returns: {...,"color":{"color":"O"},"authToken":"TOKEN2",...}
```

### 4. Start GDX client (3D visualization)
```bash
# As authenticated Black player
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id GAME_ID --token TOKEN1" &

# Or as spectator (watch-only, no cursors)
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id GAME_ID" &
```

### 5. Make moves via API
```bash
# Black plays at (3,3,3)
curl -s -H "Authentication: Bearer TOKEN1" "http://localhost:6030/set/GAME_ID/3/3/3"

# White plays at (4,3,3)
curl -s -H "Authentication: Bearer TOKEN2" "http://localhost:6030/set/GAME_ID/4/3/3"
```

## Taking Screenshots for Analysis

Use `scrot` to capture the screen:

```bash
# Take full screen screenshot
scrot /tmp/screenshot.png

# Take screenshot with 3 second delay (useful for capturing menus)
scrot -d 3 /tmp/screenshot.png

# Take screenshot of focused window only
scrot -u /tmp/screenshot.png
```

### Analyzing screenshots with Claude Code

1. Take screenshot: `scrot /tmp/screenshot.png`
2. Use the Read tool to load the screenshot: `Read("/tmp/screenshot.png")`
3. Claude Code can view and analyze the visual output, including:
    - 3D board state
    - Cursor positions and colors
    - Stone placement
    - UI elements

## GDX Client Cursor System

The GDX 3D client shows two cursors when authenticated:

- **Green cursor**: Marks the player's own last move
- **Red cursor**: Marks the opponent's last move
- Both cursors are wireframe spheres (1.1x stone size) positioned at grid intersections
- Cursors only appear when client is authenticated with `--token`
- Watch-only clients (no token) see no cursors

### Cursor Implementation Details

- Green cursor color: `ColorAttribute(0.2, 0.5, 0.1)` - greenish
- Red cursor color: `ColorAttribute(0.5, 0.1, 0.1)` - reddish
- Both use `GL20.GL_LINES` rendering mode for wireframe appearance
- Position calculated with board-centering offset: `-(boardSize+1)/2`
- Uses `setToTranslationAndScaling()` for absolute positioning (not additive `translate()`)

## Common Test Scenarios

### Testing cursor positioning
```bash
# Create game and register both players
GAME_ID=$(curl -s http://localhost:6030/new/5 | jq -r '.id')
BLACK_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/@" | jq -r '.authToken')
WHITE_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/O" | jq -r '.authToken')

# Start GDX client as Black
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id $GAME_ID --token $BLACK_TOKEN" &

# Make moves and take screenshots
curl -s -H "Authentication: Bearer $BLACK_TOKEN" "http://localhost:6030/set/$GAME_ID/3/3/3"
sleep 2
scrot /tmp/after_black_move.png

curl -s -H "Authentication: Bearer $WHITE_TOKEN" "http://localhost:6030/set/$GAME_ID/4/3/3"
sleep 2
scrot /tmp/after_white_move.png
```

### Testing bot vs bot game
```bash
# Start two bots playing each other
sbt "runMain go3d.client.BotClient --server localhost --port 6030 --size 5 --color @ --strategy closestToCenter" &
sbt "runMain go3d.client.BotClient --server localhost --port 6030 --game-id GAME_ID --color O --strategy random" &
```

## Debugging Tips

- Check client output for cursor rendering: `grep "rendering.*cursor"` in sbt output
- Verify player color is set: Check `client.playerColor` is `Some(Black)` or `Some(White)`
- Check move history: `curl -s http://localhost:6030/status/GAME_ID | jq '.game.moves'`
- Monitor server logs for authentication issues

## Common Patterns

When adding new client features:
1. Extend `InteractiveClient` trait
2. Implement `mainLoop(args: Array[String])`
3. Use `BaseClient` for server communication
4. Handle authentication tokens for move requests

When adding new bot strategies:
1. Add strategy case in `SetStrategy.narrowDown`
2. Implement method returning `Seq[Position]`
3. Use `bestBy` helper for ranking positions by metric

When adding new server endpoints:
1. Add route pattern in `GoHttpService.goService`
2. Create handler class extending `BaseHandler` or `MakeMoveTrait`
3. Implement `response` method returning `IO[Response[IO]]`
4. Add authentication check if needed
