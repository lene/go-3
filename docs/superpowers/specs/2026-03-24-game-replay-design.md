# Game Replay Feature — Design Spec

**Issue:** #13
**Date:** 2026-03-24
**Status:** Approved

---

## Overview

Add replay support to the 3D Go viewer (GDXClient), allowing users to step through the move
history of any game — active or archived — one move at a time, with optional auto-play.

---

## Requirements

- Works on both active and archived games
- GDX 3D client only (not AsciiClient or BotClient)
- Launched via command-line arguments; no in-viewer menu needed
- Auto-play: board advances automatically at a configurable interval (seconds per move)
- Manual step-through: `Space` advances one move, `Backspace` goes back one move
- Board update is a simple state swap — no animation
- HUD overlay shows: move number / total, color to move, capture counts for each player
- Data is fetched per-move from the server (`GET /status/{gameId}/{moveIndex}`)
- Optional `--to N` argument stops replay at move N (default: replay to end of game)

---

## Architecture

### Components

```
Server
  GetStatusAtMove          new http4s handler
  GoHttpService            new route entry

Client
  ReplayState              new class — owns all replay logic
  GobanDisplay             extended — holds Option[ReplayState], renders HUD
  BaseClient               one new method: statusAtMove(n)
  ClientCLIConf            four new CLI options
  GDXClient.mainLoop       wires ReplayState when --replay flag present
```

---

## Section 1 — Server Endpoint

**Route:** `GET /status/{gameId}/{moveIndex}`
**Handler:** `GetStatusAtMove(gameId: String, moveIndex: Int, request: Request[IO])`
**Package:** `go3d.server.http4s`
**Extends:** `BaseHandler`

### Logic

1. Load the full game via `Games(gameId)` (falls back to archived games automatically)
2. Clamp `moveIndex` to `[0, game.moves.length]`
3. Replay moves 0..index:
   ```scala
   game.moves.take(index).foldLeft(Game.start(game.size)) { (acc, move) =>
     acc.flatMap(_.makeMove(move))
   }
   ```
4. Return `StatusResponse(replayedGame, List(), false, replayedGame.isOver, None, requestInfo.debugInfo)`

Possible moves and player color are omitted — this is a read-only historical snapshot.

Route is inserted in `GoHttpService` after `"status" / GameId(gameId) / "d"`:
```scala
case request@GET -> Root / "status" / GameId(gameId) / IntVar(moveIndex) =>
  GetStatusAtMove(gameId, moveIndex, request).response
```

---

## Section 2 — `ReplayState` Class

**Package:** `go3d.client.gdx`
**Purpose:** Owns all replay logic; independently unit-testable without a display context.

### Constructor

```scala
class ReplayState(
  client: BaseClient,
  from: Int,           // first move index to show
  to: Int,             // last move index to show (inclusive)
  autoPlayDelay: Float // seconds per move; drives auto-advance
)
```

### Internal state

| Field | Type | Description |
|-------|------|-------------|
| `currentIndex` | `Int` | Move currently displayed |
| `isPlaying` | `Boolean` | Whether auto-play is active |
| `elapsed` | `Float` | Time accumulator for auto-play |
| `currentStatus` | `Option[StatusResponse]` | Cached response for current index |

### Public methods

| Method | Behaviour |
|--------|-----------|
| `fetch()` | Calls `client.statusAtMove(currentIndex)`, updates `currentStatus` on success; logs and retains previous value on failure |
| `advance()` | If `currentIndex < to`: increment, fetch; if now at `to`: stop auto-play |
| `rewind()` | If `currentIndex > from`: decrement, fetch |
| `tick(delta: Float)` | Accumulates delta; calls `advance()` when `elapsed >= autoPlayDelay` |

### `BaseClient` addition

```scala
def statusAtMove(n: Int): Try[StatusResponse] =
  getSR(s"$serverURL/status/$id/$n", headers)
```

---

## Section 3 — `GobanDisplay` Changes + HUD

### Constructor change

```scala
class GobanDisplay(
  client: BaseClient,
  cursorFadeSeconds: Float,
  replayState: Option[ReplayState] = None
)
```

### Render loop (`render` method)

When replay mode is active:
```scala
replayState.foreach(_.tick(Gdx.graphics.getDeltaTime))
// use replayState.flatMap(_.currentStatus) as the board state
// instead of polling /status
```

### Input handling (`keyDown`)

| Key | Action |
|-----|--------|
| `Keys.SPACE` | `replayState.foreach(_.advance())` |
| `Keys.BACKSPACE` | `replayState.foreach(_.rewind())` |

Both are no-ops in live mode (`replayState` is `None`).

### HUD overlay

Drawn after the 3D scene using `SpriteBatch` + `BitmapFont` (libGDX standard).
Visible only when `replayState.isDefined`. Positioned top-left, three lines:

```
Move 7 / 42
Black to move
Black captures: 2  White captures: 0
```

`SpriteBatch` and `BitmapFont` are created in `create()` and disposed in `dispose()`.

---

## Section 4 — CLI Arguments

New options added to `ClientCLIConf` in `InteractiveClient.scala`:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--replay` | flag | false | Enable replay mode |
| `--from N` | Int | 0 | First move index to show |
| `--to N` | Int | `Int.MaxValue` | Last move index (clamped server-side) |
| `--replay-speed S` | Float | 1.0 | Seconds per move in auto-play |

**Scallop constraints:**
- `--from`, `--to`, `--replay-speed` depend on `--replay`
- `--replay` requires `--game-id` (can only replay an existing game)

**Wiring in `GDXClient.mainLoop`:**

```scala
if replayMode then
  val state = ReplayState(client, from, to, replaySpeed)
  state.fetch()
  new Lwjgl3Application(new GobanDisplay(client, cursorFadeSeconds, Some(state)), config)
else
  new Lwjgl3Application(new GobanDisplay(client, cursorFadeSeconds, None), config)
```

---

## Section 5 — Error Handling

| Situation | Behaviour |
|-----------|-----------|
| Unknown `gameId` in server endpoint | `BaseHandler` catches `NoSuchElementException` → 404 |
| `moveIndex < 0` | Clamped to 0 server-side; no error |
| `moveIndex > game.moves.length` | Clamped to `game.moves.length` server-side; no error |
| `ReplayState.fetch()` network failure | Logs error; `currentStatus` retains previous value; no crash |

---

## Section 6 — Testing

### Server (`TestGoHttpService`)

- `GET /status/{gameId}/0` → empty board (no moves)
- `GET /status/{gameId}/1` after one stone placed → board with that stone, `moves.length == 1`
- `GET /status/{gameId}/999` beyond end → returns final board state (clamped)
- `GET /status/NONEXISTENT/0` → 404

### Server integration (`TestServer`)

- Replay endpoint on a finished game returns correct intermediate board states

### Client (`TestReplayState`)

- `advance()` increments `currentIndex`
- `rewind()` clamps at `from`, does not go below
- `advance()` at `to` stops auto-play
- `tick(delta)` triggers `advance()` after `autoPlayDelay` seconds accumulate
- `fetch()` failure (stub throws) leaves `currentStatus` unchanged

`GobanDisplay` HUD and input changes are not unit-tested (libGDX requires a display context).
`ReplayState` isolation ensures full logic coverage without one.

---

## Out of Scope

- Smooth stone-placement animation
- In-viewer game-browser or menu
- AsciiClient replay support
- Lambda/DynamoDB replay path (Phase 7+ concern)
- AlphaZero training game integration (separate future issue)
