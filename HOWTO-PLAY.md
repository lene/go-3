# How to Play 3D Go

## Quick Start

### 1. Start the Server
```bash
sbt "runMain go3d.server.GoServer --port 6030 --save-dir saves"
```

### 2. Create a New Game and Register Players

Create a game (replace SIZE with desired board size, e.g., 5, 7, or 9):
```bash
# Create game
GAME_ID=$(curl -s http://localhost:6030/new/SIZE | jq -r '.id')
echo "Game ID: $GAME_ID"

# Register Black player
BLACK_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/@" | jq -r '.authToken')
echo "Black token: $BLACK_TOKEN"

# Register White player
WHITE_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/O" | jq -r '.authToken')
echo "White token: $WHITE_TOKEN"
```

### 3. Start Clients

**ASCII Client (for making moves):**
```bash
# Black player
sbt "runMain go3d.client.AsciiClient --server localhost --port 6030 --game-id $GAME_ID --token $BLACK_TOKEN"

# White player (in another terminal)
sbt "runMain go3d.client.AsciiClient --server localhost --port 6030 --game-id $GAME_ID --token $WHITE_TOKEN"
```

**3D Viewer (watch-only):**
```bash
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id $GAME_ID"
```

### 4. Make Moves via API

Alternatively, make moves directly via curl:
```bash
# Black plays at (3,3,3)
curl -s -H "Authentication: Bearer $BLACK_TOKEN" "http://localhost:6030/set/$GAME_ID/3/3/3"

# White plays at (4,3,3)
curl -s -H "Authentication: Bearer $WHITE_TOKEN" "http://localhost:6030/set/$GAME_ID/4/3/3"
```

## Color Symbols
- Black: `@` (or `b` in some contexts)
- White: `O` (capital O, or `w` in some contexts)

## Board Coordinates
- Coordinates are 1-indexed: (1,1,1) to (SIZE,SIZE,SIZE)
- For a 5x5x5 board, center is at (3,3,3)
- For a 7x7x7 board, center is at (4,4,4)

## 3D Client Controls
- Mouse drag: Rotate view
- Mouse wheel: Zoom in/out
- The cursor (wireframe sphere) marks the opponent's last move
