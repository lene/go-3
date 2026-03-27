# Game Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add step-through replay support to the GDX 3D viewer, allowing users to watch any game move-by-move with auto-play and manual controls.

**Architecture:** `Game.atMove` reconstructs board state at any move count; a new HTTP endpoint (`GET /status/{gameId}/{moveCount}`) delegates to it; `ReplayState` encapsulates all client-side replay logic; `GobanDisplay` renders a HUD overlay and handles Space/Backspace input when `replayState` is set.

**Tech Stack:** Scala 3, http4s/Cats Effect, libGDX (`Gdx.graphics`, `SpriteBatch`, `BitmapFont`, `Keys`), Scallop CLI.

**Spec:** `docs/superpowers/specs/2026-03-24-game-replay-design.md`

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `src/main/scala/go3d/Game.scala` | Add `atMove(count: Int): Try[Game]` |
| Create | `src/main/scala/go3d/server/http4s/GetStatusAtMove.scala` | Handler for `GET /status/{gameId}/{moveCount}` |
| Modify | `src/main/scala/go3d/server/http4s/GoHttpService.scala` | Add new route |
| Modify | `src/main/scala/go3d/client/BaseClient.scala` | Add `statusAtMove(n: Int)` |
| Modify | `src/main/scala/go3d/client/InteractiveClient.scala` | Add 4 new CLI options to `ClientCLIConf` |
| Modify | `src/main/scala/go3d/client/GDXClient.scala` | Wire `ReplayState` when `--replay` present |
| Create | `src/main/scala/go3d/client/gdx/ReplayState.scala` | All client-side replay logic |
| Modify | `src/main/scala/go3d/client/gdx/GobanDisplay.scala` | HUD overlay, Space/Backspace input, replay render path |
| Modify | `src/test/scala/go3d/TestGame.scala` | Add `atMove` tests |
| Modify | `src/test/scala/go3d/server/http4s/TestGoHttpService.scala` | Add server endpoint tests |
| Modify | `src/test/scala/go3d/server/TestServer.scala` | Add integration test for replay endpoint |
| Create | `src/test/scala/go3d/client/gdx/TestReplayState.scala` | Unit tests for `ReplayState` |
| Modify | `build.sbt` | Bump version to 0.7.29 |
| Modify | `Dockerfile` | Bump version to 0.7.29 (two occurrences) |
| Modify | `run-test-game.sh` | Bump version to 0.7.29 |
| Modify | `.gitlab-ci.yml` | Bump version to 0.7.29 |

---

## Task 1: `Game.atMove` — domain helper

**Files:**
- Modify: `src/main/scala/go3d/Game.scala`
- Modify: `src/test/scala/go3d/TestGame.scala`

- [ ] **Step 1: Write the failing tests**

Add to `src/test/scala/go3d/TestGame.scala`:

```scala
@Test def testAtMoveZeroReturnsEmptyBoard(): Unit =
  val game = Game.start(TestSize).get.makeMove(Move(2, 2, 2, Black)).get
  val result = game.atMove(0).get
  Assertions.assertEquals(0, result.moves.length)
  for p <- result.goban.allPositions do
    Assertions.assertEquals(Empty, result.at(p))

@Test def testAtMoveOneReturnsFirstStone(): Unit =
  val game = Game.start(TestSize).get.makeMove(Move(2, 2, 2, Black)).get
  val result = game.atMove(1).get
  Assertions.assertEquals(1, result.moves.length)
  Assertions.assertEquals(Black, result.at(Position(2, 2, 2)))

@Test def testAtMoveFullLengthMatchesFinalGame(): Unit =
  val game = Game.start(TestSize).get
    .makeMove(Move(2, 2, 2, Black)).get
    .makeMove(Move(3, 3, 3, White)).get
  val result = game.atMove(game.moves.length).get
  Assertions.assertEquals(game.goban, result.goban)
```

- [ ] **Step 2: Run tests to confirm they fail**

```
sbt "testOnly go3d.TestGame -- -t testAtMove*"
```
Expected: FAIL with "value atMove is not a member of go3d.Game"

- [ ] **Step 3: Add `atMove` to `Game`**

In `src/main/scala/go3d/Game.scala`, add after the `isOver` method:

```scala
def atMove(count: Int): Try[Game] =
  moves.take(count).foldLeft(Game.start(size)) { (acc, move) =>
    acc.flatMap(_.makeMove(move))
  }
```

- [ ] **Step 4: Run tests to confirm they pass**

```
sbt "testOnly go3d.TestGame"
```
Expected: all TestGame tests pass

- [ ] **Step 5: Commit**

```
git add src/main/scala/go3d/Game.scala src/test/scala/go3d/TestGame.scala
git commit -m "feat: add Game.atMove domain helper for move-by-move replay (#13)"
```

---

## Task 2: `GetStatusAtMove` — server handler

**Files:**
- Create: `src/main/scala/go3d/server/http4s/GetStatusAtMove.scala`
- Modify: `src/main/scala/go3d/server/http4s/GoHttpService.scala`
- Modify: `src/test/scala/go3d/server/http4s/TestGoHttpService.scala`

- [ ] **Step 1: Write the failing tests**

Add to `TestGoHttpService.scala`:

```scala
@Test def testStatusAtMoveZeroReturnsEmptyBoard(): Unit =
  val gameId = createGameWithId()
  val blackToken = checkedRegisterPlayer(gameId, Black)
  checkedRegisterPlayer(gameId, White)
  goHttpService.httpApp.run(httpRequest(s"/set/$gameId/1/1/1", authHeader(blackToken))).unsafeRunSync()
  val request = httpRequest(s"/status/$gameId/0")
  val json = getJson(request)
  val result = decode[StatusResponse](json)
  Assertions.assertTrue(result.isRight)
  Assertions.assertEquals(Right(0), result.map(_.game.moves.length))

@Test def testStatusAtMoveOneReturnsOneMove(): Unit =
  val gameId = createGameWithId()
  val blackToken = checkedRegisterPlayer(gameId, Black)
  checkedRegisterPlayer(gameId, White)
  goHttpService.httpApp.run(httpRequest(s"/set/$gameId/1/1/1", authHeader(blackToken))).unsafeRunSync()
  val request = httpRequest(s"/status/$gameId/1")
  val json = getJson(request)
  val result = decode[StatusResponse](json)
  Assertions.assertTrue(result.isRight)
  Assertions.assertEquals(Right(1), result.map(_.game.moves.length))

@Test def testStatusAtMoveBeyondEndReturnsFinalBoard(): Unit =
  val gameId = createGameWithId()
  val blackToken = checkedRegisterPlayer(gameId, Black)
  checkedRegisterPlayer(gameId, White)
  goHttpService.httpApp.run(httpRequest(s"/set/$gameId/1/1/1", authHeader(blackToken))).unsafeRunSync()
  val request = httpRequest(s"/status/$gameId/999")
  val json = getJson(request)
  val result = decode[StatusResponse](json)
  Assertions.assertTrue(result.isRight)
  Assertions.assertEquals(Right(1), result.map(_.game.moves.length))

@Test def testStatusAtMoveNegativeClampedToZero(): Unit =
  val gameId = createGameWithId()
  val blackToken = checkedRegisterPlayer(gameId, Black)
  checkedRegisterPlayer(gameId, White)
  goHttpService.httpApp.run(httpRequest(s"/set/$gameId/1/1/1", authHeader(blackToken))).unsafeRunSync()
  // IntVar matches negative integers; the handler clamps to 0
  val request = httpRequest(s"/status/$gameId/-1")
  val json = getJson(request)
  val result = decode[StatusResponse](json)
  Assertions.assertTrue(result.isRight)
  Assertions.assertEquals(Right(0), result.map(_.game.moves.length))

@Test def testStatusAtMoveUnknownGameReturns404(): Unit =
  val actual = runRequest(uri"/status/NONEXISTENT/0")
  Assertions.assertTrue(check(actual, Status.NotFound, None: Option[String]))
```

- [ ] **Step 2: Run tests to confirm they fail**

```
sbt "testOnly go3d.server.http4s.TestGoHttpService -- -t testStatusAtMove*"
```
Expected: FAIL — route does not exist yet (404 or similar)

- [ ] **Step 3: Create `GetStatusAtMove.scala`**

```scala
package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.RequestInfo
import go3d.server.StatusResponse
import org.http4s.Request

import scala.util.Try

case class GetStatusAtMove(gameId: String, moveCount: Int, request: Request[IO])
    extends BaseHandler with LazyLogging:
  def handle: Try[GoResponse] =
    RequestInfo(request).map { requestInfo =>
      val game = Games(gameId)
      val count = moveCount.max(0).min(game.moves.length)
      val replayedGame = game.atMove(count).get
      StatusResponse(
        game        = replayedGame,
        moves       = List(),
        ready       = false,
        over        = replayedGame.isOver,
        playerColor = None,
        debug       = requestInfo.debugInfo
      )
    }
```

- [ ] **Step 4: Add route in `GoHttpService.scala`**

In `GoHttpService.scala`, add after the `"status" / GameId(gameId) / "d"` line:

```scala
case request@GET -> Root / "status" / GameId(gameId) / IntVar(moveCount) =>
  GetStatusAtMove(gameId, moveCount, request).response
```

- [ ] **Step 5: Run tests**

```
sbt "testOnly go3d.server.http4s.TestGoHttpService"
```
Expected: all TestGoHttpService tests pass

- [ ] **Step 6: Commit**

```
git add src/main/scala/go3d/server/http4s/GetStatusAtMove.scala \
        src/main/scala/go3d/server/http4s/GoHttpService.scala \
        src/test/scala/go3d/server/http4s/TestGoHttpService.scala
git commit -m "feat: add GET /status/{gameId}/{moveCount} replay endpoint (#13)"
```

---

## Task 3: `TestServer` integration test for replay endpoint

**Files:**
- Modify: `src/test/scala/go3d/server/TestServer.scala`

- [ ] **Step 1: Write the failing integration test**

Add to `TestServer` class:

```scala
@Test def testReplayEndpointReturnsMidGameState(): Unit =
  val gameSize = 5
  val newJson  = Source.fromURL(s"http://localhost:$TestPort/new/$gameSize").mkString
  val gameId   = decode[go3d.server.GameCreatedResponse](newJson).toOption.get.id

  val blackJson = Source.fromURL(s"http://localhost:$TestPort/register/$gameId/@").mkString
  val blackToken = decode[go3d.server.PlayerRegisteredResponse](blackJson).toOption.get.authToken
  val whiteJson = Source.fromURL(s"http://localhost:$TestPort/register/$gameId/O").mkString

  val move1Json = requests.get(
    s"http://localhost:$TestPort/set/$gameId/1/1/1",
    headers = Map("Authentication" -> s"Bearer $blackToken")
  ).text()

  val move2Token = decode[go3d.server.PlayerRegisteredResponse](whiteJson).toOption.get.authToken
  requests.get(
    s"http://localhost:$TestPort/set/$gameId/2/2/2",
    headers = Map("Authentication" -> s"Bearer $move2Token")
  )

  val at0 = decode[go3d.server.StatusResponse](
    Source.fromURL(s"http://localhost:$TestPort/status/$gameId/0").mkString
  ).toOption.get
  Assertions.assertEquals(0, at0.game.moves.length)

  val at1 = decode[go3d.server.StatusResponse](
    Source.fromURL(s"http://localhost:$TestPort/status/$gameId/1").mkString
  ).toOption.get
  Assertions.assertEquals(1, at1.game.moves.length)
  Assertions.assertEquals(go3d.Black, at1.game.at(go3d.Position(1, 1, 1)))

  val at2 = decode[go3d.server.StatusResponse](
    Source.fromURL(s"http://localhost:$TestPort/status/$gameId/2").mkString
  ).toOption.get
  Assertions.assertEquals(2, at2.game.moves.length)
```

- [ ] **Step 2: Run test to confirm it fails**

```
sbt "testOnly go3d.server.TestServer -- -t testReplayEndpoint*"
```
Expected: FAIL — route does not exist yet (this task follows Task 2 which adds the route; if run in order, this should PASS after Task 2)

- [ ] **Step 3: Run after Task 2 is complete**

```
sbt "testOnly go3d.server.TestServer"
```
Expected: all TestServer tests pass including the new integration test

- [ ] **Step 4: Commit**

```
git add src/test/scala/go3d/server/TestServer.scala
git commit -m "test: add TestServer integration test for replay endpoint (#13)"
```

---

## Task 4: `BaseClient.statusAtMove` — HTTP client method



**Files:**
- Modify: `src/main/scala/go3d/client/BaseClient.scala`

- [ ] **Step 1: Add `statusAtMove` to `BaseClient`**

In `BaseClient.scala`, add after the `status` method:

```scala
def statusAtMove(n: Int): Try[StatusResponse] = getSR(s"$serverURL/status/$id/$n", headers)
```

- [ ] **Step 2: Run full test suite to confirm nothing broke**

```
sbt test
```
Expected: all tests pass

- [ ] **Step 3: Commit**

```
git add src/main/scala/go3d/client/BaseClient.scala
git commit -m "feat: add BaseClient.statusAtMove for per-move status fetching (#13)"
```

---

## Task 5: `ReplayState` — client-side replay logic

**Files:**
- Create: `src/main/scala/go3d/client/gdx/ReplayState.scala`
- Create: `src/test/scala/go3d/client/gdx/TestReplayState.scala`

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/go3d/client/gdx/TestReplayState.scala`:

```scala
package go3d.client.gdx

import go3d.Black
import go3d.Move
import go3d.Pass
import go3d.White
import go3d.client.BaseClient
import go3d.server.StatusResponse
import org.junit.jupiter.api.{Assertions, Test}

import scala.util.{Failure, Success, Try}

class TestReplayState:

  private def makeStatusResponse(moveCount: Int): StatusResponse =
    var game = go3d.Game.start(3).get
    for i <- 1 to moveCount do
      val pos = go3d.Position(((i-1) % 3) + 1, ((i-1) / 3 % 3) + 1, ((i-1) / 9) + 1)
      game = game.makeMove(go3d.Move(pos, if i % 2 == 1 then Black else White)).getOrElse(game)
    StatusResponse(game = game, moves = List(), ready = false, over = false, playerColor = None, debug = "")

  private def stubClient(responses: Map[Int, Try[StatusResponse]]): BaseClient =
    new BaseClient("http://test", "TESTID", None, None):
      override def statusAtMove(n: Int): Try[StatusResponse] =
        responses.getOrElse(n, Failure(new RuntimeException(s"no stub for move $n")))

  @Test def testAdvanceIncrementsCurrentIndex(): Unit =
    val sr1 = makeStatusResponse(1)
    val sr2 = makeStatusResponse(2)
    val client = stubClient(Map(0 -> Success(makeStatusResponse(0)), 1 -> Success(sr1), 2 -> Success(sr2)))
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.currentStatus = Some(sr1)
    state.advance()
    Assertions.assertEquals(2, state.currentIndex)
    Assertions.assertEquals(Some(sr2), state.currentStatus)

  @Test def testAdvanceAtToMinusOneStopsAutoPlay(): Unit =
    val sr2 = makeStatusResponse(2)
    val client = stubClient(Map(2 -> Success(sr2)))
    val state = new ReplayState(client, from = 0, to = 2, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.isPlaying = true
    state.advance()
    Assertions.assertEquals(2, state.currentIndex)
    Assertions.assertFalse(state.isPlaying)

  @Test def testAdvanceAtEndIsNoOp(): Unit =
    val sr2 = makeStatusResponse(2)
    val client = stubClient(Map())
    val state = new ReplayState(client, from = 0, to = 2, autoPlayDelay = 1.0f)
    state.currentIndex = 2
    state.currentStatus = Some(sr2)
    state.advance()
    Assertions.assertEquals(2, state.currentIndex)
    Assertions.assertEquals(Some(sr2), state.currentStatus)

  @Test def testRewindDecrementsCurrentIndex(): Unit =
    val sr0 = makeStatusResponse(0)
    val client = stubClient(Map(0 -> Success(sr0)))
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.rewind()
    Assertions.assertEquals(0, state.currentIndex)

  @Test def testRewindAtFromIsNoOp(): Unit =
    val sr0 = makeStatusResponse(0)
    val client = stubClient(Map())
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 0
    state.currentStatus = Some(sr0)
    state.rewind()
    Assertions.assertEquals(0, state.currentIndex)
    Assertions.assertEquals(Some(sr0), state.currentStatus)

  @Test def testTickTriggerAdvanceAfterDelay(): Unit =
    val sr1 = makeStatusResponse(1)
    val sr2 = makeStatusResponse(2)
    val client = stubClient(Map(2 -> Success(sr2)))
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.isPlaying = true
    state.currentStatus = Some(sr1)
    state.tick(1.0f)
    Assertions.assertEquals(2, state.currentIndex)

  @Test def testTickIsNoOpWhenNotPlaying(): Unit =
    val sr1 = makeStatusResponse(1)
    val client = stubClient(Map())
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.isPlaying = false
    state.currentStatus = Some(sr1)
    state.tick(99.0f)
    Assertions.assertEquals(1, state.currentIndex)
    Assertions.assertEquals(Some(sr1), state.currentStatus)

  @Test def testFetchFailureLeavesCurrentStatusUnchanged(): Unit =
    val sr1 = makeStatusResponse(1)
    val client = stubClient(Map(1 -> Failure(new RuntimeException("network error"))))
    val state = new ReplayState(client, from = 0, to = 3, autoPlayDelay = 1.0f)
    state.currentIndex = 1
    state.currentStatus = Some(sr1)
    state.fetch()
    Assertions.assertEquals(Some(sr1), state.currentStatus)
```

- [ ] **Step 2: Run tests to confirm they fail**

```
sbt "testOnly go3d.client.gdx.TestReplayState"
```
Expected: FAIL — `ReplayState` does not exist

- [ ] **Step 3: Create `ReplayState.scala`**

```scala
package go3d.client.gdx

import com.typesafe.scalalogging.LazyLogging
import go3d.client.BaseClient
import go3d.server.StatusResponse

import scala.util.{Failure, Success}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class ReplayState(
  client: BaseClient,
  from: Int,
  private var to: Int,
  autoPlayDelay: Float
) extends LazyLogging:

  var currentIndex: Int = from
  var isPlaying: Boolean = true
  var elapsed: Float = 0f
  var currentStatus: Option[StatusResponse] = None
  var totalMoves: Int = to

  def init(): Unit =
    client.statusAtMove(Int.MaxValue) match
      case Success(sr) =>
        val actualTotal = sr.game.moves.length
        totalMoves = actualTotal
        to = to.min(actualTotal)
      case Failure(e) =>
        logger.error(s"ReplayState.init probe fetch failed: ${e.getMessage}")
        isPlaying = false
    currentIndex = from
    fetch()

  def fetch(): Unit =
    client.statusAtMove(currentIndex) match
      case Success(sr) => currentStatus = Some(sr)
      case Failure(e)  => logger.error(s"ReplayState.fetch($currentIndex) failed: ${e.getMessage}")

  def advance(): Unit =
    if currentIndex < to then
      currentIndex += 1
      fetch()
      if currentIndex == to then isPlaying = false

  def rewind(): Unit =
    if currentIndex > from then
      currentIndex -= 1
      fetch()

  def tick(delta: Float): Unit =
    if isPlaying then
      elapsed += delta
      if elapsed >= autoPlayDelay then
        elapsed -= autoPlayDelay
        advance()
```

- [ ] **Step 4: Run tests**

```
sbt "testOnly go3d.client.gdx.TestReplayState"
```
Expected: all 8 tests pass

- [ ] **Step 5: Run full test suite**

```
sbt test
```
Expected: all tests pass

- [ ] **Step 6: Commit**

```
git add src/main/scala/go3d/client/gdx/ReplayState.scala \
        src/test/scala/go3d/client/gdx/TestReplayState.scala
git commit -m "feat: add ReplayState for client-side replay logic (#13)"
```

---

## Task 6: `GobanDisplay` — HUD overlay and replay render path

**Files:**
- Modify: `src/main/scala/go3d/client/gdx/GobanDisplay.scala`

The `GobanDisplay` changes cannot be unit-tested (requires GL context). Verify via manual smoke test after wiring in Task 6.

- [ ] **Step 1: Add `replayState` constructor param and HUD fields**

In `GobanDisplay.scala`, change the class header from:

```scala
class GobanDisplay(client: BaseClient, val cursorFadeSeconds: Float = 10.0f)
    extends ApplicationListener with LazyLogging:
```

to:

```scala
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
class GobanDisplay(
  client: BaseClient,
  val cursorFadeSeconds: Float = 10.0f,
  replayState: Option[ReplayState] = None
) extends ApplicationListener with LazyLogging:
```

- [ ] **Step 2: Add HUD imports and fields**

Add to the imports section at the top of `GobanDisplay.scala`:

```scala
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
```

Add to the class body (after existing `private var` fields):

```scala
private var hudBatch: SpriteBatch = _
private var hudFont: BitmapFont = _
```

- [ ] **Step 3: Initialise HUD in `create()` and register input handler**

In the `create()` method, add after the existing Timer setup:

```scala
hudBatch = new SpriteBatch()
hudFont  = new BitmapFont()
hudFont.setColor(Color.WHITE)
if replayState.isDefined then
  Gdx.input.setInputProcessor(new InputAdapter {
    override def keyDown(keycode: Int): Boolean =
      keycode match
        case Keys.SPACE     => replayState.foreach(_.advance()); true
        case Keys.BACKSPACE => replayState.foreach(_.rewind());  true
        case _              => false
  })
```

- [ ] **Step 4: Add replay render path to `render()`**

Replace the render method body with:

```scala
@Override def render(): Unit =
  replayState match
    case Some(rs) =>
      rs.tick(Gdx.graphics.getDeltaTime)
      rs.currentStatus match
        case Some(sr) =>
          if game.forall(_.moves.length != sr.game.moves.length) then
            game = Some(sr.game)
            stonesModel = builder.createStones(sr.game)
        case None =>
          // init() not yet complete: leave game/stonesModel as-is (empty on first frame)
    case None =>
      // existing live-polling path (unchanged — Timer updates game via updateGame)
      ()
  val currentTime = com.badlogic.gdx.utils.TimeUtils.millis() / 1000f
  val ownFadeAlpha = calculateFadeAlpha(currentTime - ownMoveTimestamp)
  val opponentFadeAlpha = calculateFadeAlpha(currentTime - opponentMoveTimestamp)
  gdxResources.render(
    ownLastMove, opponentLastMove, ownFadeAlpha, opponentFadeAlpha,
    builder.gridModel, stonesModel
  )
  if replayState.isDefined then drawHud()
```

- [ ] **Step 5: Add `drawHud()` method**

Add after `render()`:

```scala
private def drawHud(): Unit =
  replayState.foreach { rs =>
    val moveStr  = s"Move ${rs.currentIndex} / ${rs.totalMoves}"
    val colorStr = rs.currentStatus.fold("") { sr =>
      if sr.game.isOver then "Game over"
      else s"${sr.game.moveColor} to move"
    }
    val capsStr  = rs.currentStatus.fold("") { sr =>
      s"Black captures: ${sr.game.captures(go3d.Black)}  White captures: ${sr.game.captures(go3d.White)}"
    }
    val lineHeight = hudFont.getLineHeight
    val y = Gdx.graphics.getHeight - 10f
    hudBatch.begin()
    hudFont.draw(hudBatch, moveStr,  10f, y)
    hudFont.draw(hudBatch, colorStr, 10f, y - lineHeight)
    hudFont.draw(hudBatch, capsStr,  10f, y - 2 * lineHeight)
    hudBatch.end()
  }
```

- [ ] **Step 6: Dispose HUD resources in `dispose()`**

Add to `dispose()`:

```scala
if hudBatch != null then hudBatch.dispose()
if hudFont  != null then hudFont.dispose()
```

- [ ] **Step 7: Run tests (compile check)**

```
sbt compile
```
Expected: compiles without errors or warnings

- [ ] **Step 8: Commit**

```
git add src/main/scala/go3d/client/gdx/GobanDisplay.scala
git commit -m "feat: add HUD overlay and replay render path to GobanDisplay (#13)"
```

---

## Task 7: CLI args and `GDXClient` wiring

**Files:**
- Modify: `src/main/scala/go3d/client/InteractiveClient.scala`
- Modify: `src/main/scala/go3d/client/GDXClient.scala`

`ClientTrait` defines `def mainLoop(client: BaseClient): Unit` — we cannot change its signature without updating `AsciiClient` and `BotClient` too. Instead, `GDXClient` overrides `parseArgs` to capture replay settings into object-level vars, which `mainLoop` then reads. `parseArgs` creates a second `ClientCLIConf` from the same args (the new opts are already registered, so `verify()` succeeds twice).

- [ ] **Step 1: Add CLI options to `ClientCLIConf`**

In `InteractiveClient.scala`, inside the `ClientCLIConf` class body, before `verify()`:

```scala
val replay      = toggle(default = Some(false))
val from        = opt[Int](required = false, default = Some(0))
val to          = opt[Int](required = false, default = Some(Int.MaxValue))
val replaySpeed = opt[Float](name = "replay-speed", required = false, default = Some(1.0f))
dependsOnAll(from,        List(replay))
dependsOnAll(to,          List(replay))
dependsOnAll(replaySpeed, List(replay))
dependsOnAll(replay,      List(gameId))
```

- [ ] **Step 2: Add replay state vars and override `parseArgs` in `GDXClient`**

Replace the entire content of `GDXClient.scala` with:

```scala
package go3d.client

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import go3d.client.gdx.GobanDisplay
import go3d.client.gdx.ReplayState

import scala.util.Try

object GDXClient extends InteractiveClient:

    private final val COLOR_BITS = 8
    private final val DEPTH_BITS = 16
    private final val STENCIL_BITS = 0
    private final val NUM_ANTIALIAS_SAMPLES = 4

    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var replayEnabled: Boolean = false
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var replayFrom: Int = 0
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var replayTo: Int = Int.MaxValue
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var replaySpeed: Float = 1.0f
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var cursorFade: Float = 10.0f

    override def parseArgs(args: Array[String]): Try[BaseClient] =
        val result = super.parseArgs(args)
        result.foreach { _ =>
            val conf = new ClientCLIConf(args.toList)
            replayEnabled = conf.replay()
            replayFrom    = conf.from()
            replayTo      = conf.to()
            replaySpeed   = conf.replaySpeed()
            cursorFade    = conf.cursorFadeSeconds()
        }
        result

    def mainLoop(client: BaseClient): Unit =
        println("Starting 3D Go client")
        val config = getConfiguration("3D Go", 1280, 960)
        if replayEnabled then
            val state = new ReplayState(client, replayFrom, replayTo, replaySpeed)
            state.init()
            new Lwjgl3Application(new GobanDisplay(client, cursorFade, Some(state)), config)
        else
            new Lwjgl3Application(new GobanDisplay(client, cursorFade), config)

    def getConfiguration(appName: String, width: Int, height: Int): Lwjgl3ApplicationConfiguration =
        val config = new Lwjgl3ApplicationConfiguration()
        config.disableAudio(true)
        config.setTitle(appName)
        config.setWindowedMode(width, height)
        config.setBackBufferConfig(
            COLOR_BITS, COLOR_BITS, COLOR_BITS, COLOR_BITS, DEPTH_BITS, STENCIL_BITS,
            NUM_ANTIALIAS_SAMPLES
        )
        config
```

- [ ] **Step 3: Compile**

```
sbt compile
```
Expected: no errors

- [ ] **Step 4: Run all tests**

```
sbt test
```
Expected: all tests pass

- [ ] **Step 5: Commit**

```
git add src/main/scala/go3d/client/InteractiveClient.scala \
        src/main/scala/go3d/client/GDXClient.scala
git commit -m "feat: add --replay CLI args and wire ReplayState in GDXClient (#13)"
```

---

## Task 8: Version bump and MR

- [ ] **Step 1: Bump version to 0.7.29**

Update in all four locations (see CLAUDE.md "Version Bumping"):
- `build.sbt`: `version := "0.7.29"`
- `Dockerfile`: both `ARG version=0.7.29` occurrences
- `run-test-game.sh`: `VERSION=0.7.29`
- `.gitlab-ci.yml`: `DEPLOYABLE_VERSION: 0.7.29`

- [ ] **Step 2: Run full test suite**

```
sbt test
```
Expected: all tests pass

- [ ] **Step 3: Commit version bump**

```
git add build.sbt Dockerfile run-test-game.sh .gitlab-ci.yml
git commit -m "Bump version to 0.7.29 and add game replay feature (issue #13)"
```

- [ ] **Step 4: Push and open MR**

```
git push -u origin 13-game-replay
```

Open MR targeting `master`, title: "Add game replay feature (issue #13)".

---

## Manual Smoke Test (after CI passes)

```bash
# Start server
sbt "runMain go3d.server.GoServer --port 6030 --save-dir saves" &

# Create a game and make some moves
GAME_ID=$(curl -s http://localhost:6030/new/5 | jq -r '.id')
BLACK=$(curl -s "http://localhost:6030/register/$GAME_ID/@" | jq -r '.authToken')
WHITE=$(curl -s "http://localhost:6030/register/$GAME_ID/O" | jq -r '.authToken')
curl -s -H "Authentication: Bearer $BLACK" "http://localhost:6030/set/$GAME_ID/3/3/3"
curl -s -H "Authentication: Bearer $WHITE" "http://localhost:6030/set/$GAME_ID/4/3/3"
curl -s -H "Authentication: Bearer $BLACK" "http://localhost:6030/set/$GAME_ID/2/2/2"

# Replay from move 0 with 2 seconds per move
sbt "runMain go3d.client.GDXClient --server localhost --port 6030 --game-id $GAME_ID --replay --replay-speed 2.0"

# Verify:
# - HUD shows "Move 0 / 3", "Black to move", "Black captures: 0  White captures: 0"
# - Board auto-advances every 2 seconds
# - Space advances manually, Backspace goes back
# - HUD denominator stays at 3 (total game length) even when --to is used
```
