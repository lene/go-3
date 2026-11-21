# How to Play 3D Go

## Quick Start

### 1. Start the Server
```bash
sbt "runMain go3d.server.GoServer --port 6030 --save-dir saves"
```

### 2. Create a New Game and Register Players

Create a game (replace SIZE with desired board size, e.g., 5, 7, or 9):
```bash
# Create game (komi 0.5; for another, append "?komi=6.5": a half-integer, 400 otherwise)
GAME_ID=$(curl -s http://localhost:6030/new/SIZE | jq -r '.id')
echo "Game ID: $GAME_ID"

# Register Black player
BLACK_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/@" | jq -r '.authToken')
echo "Black token: $BLACK_TOKEN"

# Register White player
WHITE_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/O" | jq -r '.authToken')
echo "White token: $WHITE_TOKEN"
```

Instead of creating a game, you can join one that someone else created and that waits for a white
player. `/openGames` lists those games: `ids` has their IDs, and `games` has an `{id, komi}` for
each, so you see the komi before you register. To list them, and then join the first one with the
komi you want (here 6.5) as White:
```bash
curl -s http://localhost:6030/openGames | jq '.games'

GAME_ID=$(curl -s http://localhost:6030/openGames |
  jq -r --argjson komi 6.5 'first(.games[]? | select(.komi == $komi) | .id) // empty')
if [ -n "$GAME_ID" ]; then
  WHITE_TOKEN=$(curl -s "http://localhost:6030/register/$GAME_ID/O" | jq -r '.authToken')
  echo "Game ID: $GAME_ID, White token: $WHITE_TOKEN"
else
  echo "No open game with that komi: create one as above"
fi
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

### 5. See Who Won

Every move and `/status/$GAME_ID` answer with `"over"`. Once it is `true`, the response also has a
`"winner"` (`{"color":"@"}` for Black, `{"color":"O"}` for White): the player with the higher
score after the komi is added to White's. There is no `"winner"` while the game is running.
```bash
curl -s "http://localhost:6030/status/$GAME_ID" | jq '{over, winner}'
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
