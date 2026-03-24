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
- HUD overlay shows: move count / total, color to move (or "Game over"), capture counts
- Data is fetched per-move from the server (`GET /status/{gameId}/{moveCount}`)
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

**Route:** `GET /status/{gameId}/{moveCount}`
**Handler:** `GetStatusAtMove(gameId: String, moveCount: Int, request: Request[IO])`
**Package:** `go3d.server.http4s`
**Extends:** `BaseHandler`

### Semantics

`moveCount` is the **number of moves to replay** (not a zero-based index):
- `moveCount = 0` → empty board (no moves applied)
- `moveCount = 1` → board after one move
- `moveCount = N` → board after N moves

### Logic

1. Load the full game via `Games(gameId)` (falls back to archived games automatically)
2. Clamp `moveCount` to `[0, game.moves.length]`: `val count = moveCount.max(0).min(game.moves.length)`
3. Replay exactly `count` moves:
   ```scala
   game.moves.take(count).foldLeft(Game.start(game.size)) { (acc, move) =>
     acc.flatMap(_.makeMove(move))
   }
   ```
   If any `makeMove` call returns `Failure`, the fold propagates a `Failure[Game]`. The handler
   treats this as an `InternalServerError` — it falls through to the `BaseHandler` catch-all
   `case Failure(e) => InternalServerError(...)`. This should only occur if stored game state is
   corrupted.
4. Return (using named parameters to avoid field-order ambiguity):
   ```scala
   StatusResponse(
     game        = replayedGame,
     moves       = List(),
     ready       = false,
     over        = replayedGame.isOver,
     playerColor = None,
     debug       = requestInfo.debugInfo
   )
   ```

Possible moves and player color are omitted — this is a read-only historical snapshot.

Route is inserted in `GoHttpService` after `"status" / GameId(gameId) / "d"`:
```scala
case request@GET -> Root / "status" / GameId(gameId) / IntVar(moveCount) =>
  GetStatusAtMove(gameId, moveCount, request).response
```

---

## Section 2 — `ReplayState` Class

**Package:** `go3d.client.gdx`
**Purpose:** Owns all replay logic; independently unit-testable without a display context.

### Constructor

```scala
@SuppressWarnings(Array("org.wartremover.warts.Var"))
class ReplayState(
  client: BaseClient,
  from: Int,           // first move count to show (inclusive)
  to: Int,             // last move count to show (inclusive); resolved from game during init
  autoPlayDelay: Float // seconds per move; drives auto-advance
)
```

`@SuppressWarnings(Array("org.wartremover.warts.Var"))` is required because `ReplayState` holds
mutable display state, consistent with WartRemover usage in other libGDX-adjacent classes.

### Internal state

| Field | Type | Initial value | Description |
|-------|------|---------------|-------------|
| `currentIndex` | `Int` | `from` | Move count currently displayed |
| `isPlaying` | `Boolean` | `true` | Auto-play is on by default |
| `elapsed` | `Float` | `0f` | Time accumulator for auto-play |
| `currentStatus` | `Option[StatusResponse]` | `None` | Cached response for current index |
| `totalMoves` | `Int` | `to` | Resolved total; set during `init()` |

### Initialisation

`init()` is called once from the **main thread before `Lwjgl3Application` starts**. It performs
only HTTP I/O — no GL calls — so it is safe to call before the GL context exists.

Steps:
1. Fetch `client.statusAtMove(Int.MaxValue)` → read `response.game.moves.length` → this is the game's actual total move count (`actualTotal`)
2. Set `totalMoves = actualTotal`; clamp `to = to.min(actualTotal)`
3. Set `currentIndex = from`
4. Call `fetch()` to load the initial board state

If the probe fetch (step 1) fails: log the error, set `isPlaying = false` (disable auto-play to
avoid infinite fetching), and let the user step manually. `totalMoves` and `to` retain their
pre-init values.

This costs one extra server call at startup but ensures correct end-of-game detection regardless of whether `--to` was specified.

### Public methods

| Method | Behaviour |
|--------|-----------|
| `fetch()` | Calls `client.statusAtMove(currentIndex)`; on success updates `currentStatus`; on failure logs error and retains previous `currentStatus` |
| `advance()` | If `currentIndex < to`: increment `currentIndex`, call `fetch()`; if `currentIndex` is now equal to `to`, set `isPlaying = false`. If `currentIndex >= to`, do nothing. |
| `rewind()` | If `currentIndex > from`: decrement `currentIndex`, call `fetch()`; if already at `from`, do nothing (no fetch) |
| `tick(delta: Float)` | If `isPlaying`: accumulate `elapsed += delta`; when `elapsed >= autoPlayDelay`, reset `elapsed -= autoPlayDelay` (subtract, not zero, to avoid drift) and call `advance()` |

`tick` is a no-op when `isPlaying == false`.

There is no user-facing toggle for `isPlaying`; auto-play is always on at startup and stops
automatically when the end of the replay range is reached.

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

`@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))` is required on the
constructor for the `= None` default.

### Render loop (`render` method)

The render method has two mutually exclusive paths:

```
if replayState.isDefined:
  replayState.foreach(_.tick(Gdx.graphics.getDeltaTime))
  boardState = replayState.flatMap(_.currentStatus)
    // if None (before init() completes), render empty board
else:
  // existing live-polling path (unchanged)
```

When `replayState` is defined, the existing live-polling call to `/status` is **skipped entirely**.
If `currentStatus` is `None` (before `init()` has completed its first fetch), the display renders
an empty board.

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
Black to move          ← or "Game over" when replayedGame.isOver
Black captures: 2  White captures: 0
```

The denominator (`42`) is `totalMoves` — the full game length — not `to` (the replay range end).
This gives context of where in the full game the replay is, even when `--to` is used.

When `replayedGame.isOver` is true, line 2 shows `"Game over"` instead of a color.

`SpriteBatch` and `BitmapFont` are created in `create()` and disposed in `dispose()`.

---

## Section 4 — CLI Arguments

New options added to `ClientCLIConf` in `InteractiveClient.scala`:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--replay` | flag | false | Enable replay mode |
| `--from N` | Int | 0 | First move count to show |
| `--to N` | Int | `Int.MaxValue` | Last move count (resolved against actual game length during `init()`) |
| `--replay-speed S` | Float | 1.0 | Seconds per move in auto-play |

**Scallop constraints:**
- `--from`, `--to`, `--replay-speed` depend on `--replay`
- `--replay` requires `--game-id` via `dependsOnAll(replay, List(gameId))`
- The existing `requireOne(size, gameId)` constraint is satisfied because `--replay` requires
  `--game-id`, and `--game-id` alone satisfies the `requireOne` rule

**Wiring in `GDXClient.mainLoop`:**

```scala
if replayMode then
  val state = ReplayState(client, from, to, replaySpeed)
  state.init()   // fetches total moves, sets to, loads initial board
  new Lwjgl3Application(new GobanDisplay(client, cursorFadeSeconds, Some(state)), config)
else
  new Lwjgl3Application(new GobanDisplay(client, cursorFadeSeconds, None), config)
```

---

## Section 5 — Error Handling

| Situation | Behaviour |
|-----------|-----------|
| Unknown `gameId` in server endpoint | `BaseHandler` catches `NoSuchElementException` → 404 |
| `moveCount < 0` | Clamped to 0 server-side; no error |
| `moveCount > game.moves.length` | Clamped to `game.moves.length` server-side; no error |
| `ReplayState.fetch()` network failure | Logs error; `currentStatus` retains previous value; no crash |
| `ReplayState.init()` probe fetch fails | Logs error; sets `isPlaying = false`; `to` and `totalMoves` retain pre-init values; user can still step manually |
| Corrupted stored game (makeMove fails mid-fold) | `GetStatusAtMove.handle` returns `Failure`; `BaseHandler` catch-all returns 500 Internal Server Error |

---

## Section 6 — Testing

### Server (`TestGoHttpService`)

- `GET /status/{gameId}/0` → board with 0 moves (empty)
- `GET /status/{gameId}/1` after one stone placed → board with that stone, `game.moves.length == 1`
- `GET /status/{gameId}/999` beyond end → returns final board state (clamped to actual move count)
- `GET /status/NONEXISTENT/0` → 404

### Server integration (`TestServer`)

- Replay endpoint on a finished game returns correct intermediate board states at each move count

### Client (`TestReplayState`)

- `advance()` increments `currentIndex` and calls `fetch()`
- `advance()` when `currentIndex == to - 1`: increments to `to`, calls `fetch()`, sets `isPlaying = false`
- `advance()` when `currentIndex == to` (already at end): no-op (no increment, no fetch)
- `rewind()` clamps at `from`: when already at `from`, does nothing and does not call `fetch()`
- `tick(delta)` triggers `advance()` after `autoPlayDelay` seconds accumulate
- `tick(delta)` is a no-op when `isPlaying == false`
- `fetch()` failure (stub client throws) leaves `currentStatus` unchanged

`GobanDisplay` HUD and input changes are not unit-tested (libGDX requires a display context).
`ReplayState` isolation ensures full logic coverage without one.

---

## Out of Scope

- Smooth stone-placement animation
- In-viewer game-browser or menu
- Manual toggle for auto-play (stops automatically at end; space/backspace for manual control)
- AsciiClient replay support
- Lambda/DynamoDB replay path (Phase 7+ concern)
- AlphaZero training game integration (separate future issue)
