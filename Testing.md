# Comprehensive Testing Strategy for GDX Cursor Positioning

## Executive Summary

This document outlines a multi-layered testing approach for automatically validating cursor positioning in the 3D Go GDX client. The strategy combines unit tests, integration tests, and visual regression tests to ensure cursors (green for own moves, red for opponent moves) are correctly positioned on the 3D board.

---

## Testing Layers

### Layer 1: Unit Tests (Non-Visual)
**Goal**: Test cursor position calculation logic without requiring OpenGL context

**Test File**: `src/test/scala/go3d/client/gdx/TestCursorLogic.scala`

#### Test Cases:

1. **Test `ownLastMove` calculation**
   - Empty game (no moves) → None
   - Game with only Black moves → returns Black's last move
   - Game with only White moves → returns White's last move
   - Game with mixed moves → returns own color's last move
   - Game with passes → ignores passes, returns last actual move
   - Client as Black → finds last Black move
   - Client as White → finds last White move

2. **Test `opponentLastMove` calculation**
   - Empty game → None
   - Game with only own moves → None
   - Game with only opponent moves → returns opponent's last move
   - Game with mixed moves → returns opponent color's last move
   - Game with passes → ignores passes, returns last actual opponent move
   - Client as Black → finds last White move
   - Client as White → finds last Black move

3. **Test board-centering offset calculation**
   - Board size 3 → offset = -2.0f
   - Board size 5 → offset = -3.0f
   - Board size 7 → offset = -4.0f
   - Formula: `-(boardSize + 1) / 2f`

4. **Test cursor transformation calculation** (without actual ModelInstance)
   - Position(1, 1, 1) with size 3 → (-1.0f, -1.0f, -1.0f)
   - Position(3, 3, 3) with size 3 → (1.0f, 1.0f, 1.0f)
   - Position(4, 4, 4) with size 7 → (0.0f, 0.0f, 0.0f) (center)
   - Verify scaling is always 1.1f for both cursors

---

### Layer 2: Integration Tests (Headless)
**Goal**: Test GobanDisplay logic with mocked game states without full rendering

**Test File**: `src/test/scala/go3d/client/gdx/TestGobanDisplay.scala`

#### Test Approach:
Use headless backend or mock GDXResources to test logic without OpenGL

#### Test Cases:

1. **Test cursor visibility with game progression**
   - New game → both cursors None
   - After Black's first move → green cursor at Black's move, red cursor None (for Black player)
   - After White's first move → green cursor at White's move, red cursor at Black's move (for White player)
   - After second Black move → green cursor updated, red cursor at White's last move

2. **Test cursor positions after captures**
   - Move at position that captures stones → cursors show last moves, not captured positions
   - Verify captured stones don't affect cursor positioning

3. **Test cursor positions with passes**
   - Black passes → cursor remains at last actual move (not pass)
   - White passes → cursor remains at last actual move
   - Both pass → cursors remain at last actual moves

4. **Test cursor switching based on player color**
   - Client registered as Black → green = own (Black), red = opponent (White)
   - Client registered as White → green = own (White), red = opponent (Black)
   - Watch-only client (no token) → both cursors None

5. **Test update cycle**
   - Game state changes → cursors update on next render
   - Same game state → cursors don't recalculate unnecessarily

---

### Layer 3: Visual Validation Tests (Headless Rendering)
**Goal**: Capture and validate actual cursor rendering without manual inspection

**Test File**: `src/test/scala/go3d/client/gdx/TestCursorRendering.scala`

#### Test Approach:
Use libGDX headless backend to render to framebuffer, then analyze pixels

#### Test Cases:

1. **Test cursor color rendering**
   - Green cursor renders with correct color values (0.2, 0.5, 0.1)
   - Red cursor renders with correct color values (0.5, 0.1, 0.1)

2. **Test cursor size**
   - Cursor scale is 1.1x stone size
   - Cursor is visible but doesn't overlap adjacent positions

3. **Test cursor positioning precision**
   - Cursor center aligns with grid intersection
   - No offset errors across different board positions

---

### Layer 4: End-to-End Integration Tests
**Goal**: Test full client-server interaction with cursor positioning

**Test File**: `src/test/scala/go3d/client/gdx/TestCursorE2E.scala`

#### Test Approach:
Start test server, register two clients, make moves, verify cursor states

#### Test Cases:

1. **Two-player game simulation**
   - Start server
   - Register Black client
   - Register White client
   - Black makes move at (3,3,3)
   - Verify Black client: green at (3,3,3), red = None
   - Verify White client: green = None, red at (3,3,3)
   - White makes move at (4,4,4)
   - Verify Black client: green at (3,3,3), red at (4,4,4)
   - Verify White client: green at (4,4,4), red at (3,3,3)

2. **Multi-move sequence**
   - Play 10 moves alternating
   - After each move, verify cursors track last moves for each color
   - Verify no cursor position leakage between turns

3. **Watch-only client**
   - Start game with two players
   - Connect watch-only client (no token)
   - Verify watch client has no cursors

---

## Implementation Plan

### Phase 1: Refactoring for Testability (REQUIRED FIRST)

**Current Problem**: `GobanDisplay`, `GDXResources`, and `SphereMarker` are tightly coupled to libGDX rendering, making unit testing difficult.

**Solutions**:

1. **Extract cursor position calculation**

   Create `src/main/scala/go3d/client/gdx/CursorLogic.scala`:

   ```scala
   package go3d.client.gdx

   import go3d.{Color, Game, Position}

   object CursorLogic:

     def calculateOwnLastMove(game: Option[Game], playerColor: Option[Color]): Option[Position] =
       playerColor.flatMap { myColor =>
         game.flatMap { g =>
           g.moves.reverse.find(_.color == myColor).flatMap(_.optionalPosition)
         }
       }

     def calculateOpponentLastMove(game: Option[Game], playerColor: Option[Color]): Option[Position] =
       playerColor.flatMap { myColor =>
         game.flatMap { g =>
           g.moves.reverse.find(_.color != myColor).flatMap(_.optionalPosition)
         }
       }

     def calculateBoardOffset(boardSize: Int): Float =
       -(boardSize + 1) / 2f

     def calculateCursorTransform(position: Position, boardSize: Int): (Float, Float, Float) =
       val offset = calculateBoardOffset(boardSize)
       (
         position.x.toFloat + offset,
         position.y.toFloat + offset,
         position.z.toFloat + offset
       )
   ```

2. **Create testable Marker interface with inspection methods**

   Update `src/main/scala/go3d/client/gdx/ParticleMarker.scala`:

   ```scala
   trait Marker:
     def render(modelBatch: ModelBatch, position: Option[Position]): Unit
     def getLastRenderedPosition: Option[Position]  // For testing
     def getColor: (Float, Float, Float)  // For testing
   ```

3. **Make GDXResources return cursor state**

   Add to `src/main/scala/go3d/client/gdx/GDXResources.scala`:

   ```scala
   case class CursorState(
     ownPosition: Option[Position],
     opponentPosition: Option[Position]
   )

   // In GDXResources:
   def getCurrentCursorState(ownMove: Option[Position], opponentMove: Option[Position]): CursorState =
     CursorState(ownMove, opponentMove)
   ```

4. **Refactor GobanDisplay to use CursorLogic**

   Update `src/main/scala/go3d/client/gdx/GobanDisplay.scala`:

   ```scala
   private def ownLastMove: Option[Position] =
     CursorLogic.calculateOwnLastMove(game, client.playerColor)

   private def opponentLastMove: Option[Position] =
     CursorLogic.calculateOpponentLastMove(game, client.playerColor)
   ```

---

### Phase 2: Unit Tests Implementation

Create `src/test/scala/go3d/client/gdx/TestCursorLogic.scala`:

```scala
package go3d.client.gdx

import go3d._
import org.junit.jupiter.api.{Assertions, Test}

class TestCursorLogic:

  @Test def testCalculateOwnLastMoveEmptyGame(): Unit =
    val game = Some(Game.start(3))
    val result = CursorLogic.calculateOwnLastMove(game, Some(Black))
    Assertions.assertEquals(None, result)

  @Test def testCalculateOwnLastMoveWithBlackMoves(): Unit =
    val game = Some(Game.start(3).set(Position(1,1,1)))
    val result = CursorLogic.calculateOwnLastMove(game, Some(Black))
    Assertions.assertEquals(Some(Position(1,1,1)), result)

  @Test def testCalculateOwnLastMoveIgnoresOpponentMoves(): Unit =
    val game = Some(
      Game.start(3)
        .set(Position(1,1,1))  // Black
        .set(Position(2,2,2))  // White
    )
    val result = CursorLogic.calculateOwnLastMove(game, Some(Black))
    Assertions.assertEquals(Some(Position(1,1,1)), result)

  @Test def testCalculateOwnLastMoveWithMultipleMoves(): Unit =
    val game = Some(
      Game.start(3)
        .set(Position(1,1,1))  // Black
        .set(Position(2,2,2))  // White
        .set(Position(1,2,1))  // Black
        .set(Position(2,1,2))  // White
    )
    val result = CursorLogic.calculateOwnLastMove(game, Some(Black))
    Assertions.assertEquals(Some(Position(1,2,1)), result)

  @Test def testCalculateOwnLastMoveIgnoresPasses(): Unit =
    val game = Some(
      Game.start(3)
        .set(Position(1,1,1))  // Black
        .pass()                // White passes
        .set(Position(2,2,2))  // Black
    )
    val result = CursorLogic.calculateOwnLastMove(game, Some(Black))
    Assertions.assertEquals(Some(Position(2,2,2)), result)

  @Test def testCalculateOwnLastMoveWhitePlayer(): Unit =
    val game = Some(
      Game.start(3)
        .set(Position(1,1,1))  // Black
        .set(Position(2,2,2))  // White
    )
    val result = CursorLogic.calculateOwnLastMove(game, Some(White))
    Assertions.assertEquals(Some(Position(2,2,2)), result)

  @Test def testCalculateOwnLastMoveNoPlayerColor(): Unit =
    val game = Some(Game.start(3).set(Position(1,1,1)))
    val result = CursorLogic.calculateOwnLastMove(game, None)
    Assertions.assertEquals(None, result)

  @Test def testCalculateOpponentLastMoveEmptyGame(): Unit =
    val game = Some(Game.start(3))
    val result = CursorLogic.calculateOpponentLastMove(game, Some(Black))
    Assertions.assertEquals(None, result)

  @Test def testCalculateOpponentLastMoveOnlyOwnMoves(): Unit =
    val game = Some(Game.start(3).set(Position(1,1,1)))
    val result = CursorLogic.calculateOpponentLastMove(game, Some(Black))
    Assertions.assertEquals(None, result)

  @Test def testCalculateOpponentLastMoveWithOpponentMove(): Unit =
    val game = Some(
      Game.start(3)
        .set(Position(1,1,1))  // Black
        .set(Position(2,2,2))  // White
    )
    val result = CursorLogic.calculateOpponentLastMove(game, Some(Black))
    Assertions.assertEquals(Some(Position(2,2,2)), result)

  @Test def testCalculateOpponentLastMoveMultipleMoves(): Unit =
    val game = Some(
      Game.start(3)
        .set(Position(1,1,1))  // Black
        .set(Position(2,2,2))  // White
        .set(Position(1,2,1))  // Black
        .set(Position(2,1,2))  // White
    )
    val result = CursorLogic.calculateOpponentLastMove(game, Some(Black))
    Assertions.assertEquals(Some(Position(2,1,2)), result)

  @Test def testCalculateOpponentLastMoveIgnoresPasses(): Unit =
    val game = Some(
      Game.start(3)
        .set(Position(1,1,1))  // Black
        .set(Position(2,2,2))  // White
        .pass()                // Black passes
    )
    val result = CursorLogic.calculateOpponentLastMove(game, Some(Black))
    Assertions.assertEquals(Some(Position(2,2,2)), result)

  @Test def testCalculateBoardOffsetSize3(): Unit =
    Assertions.assertEquals(-2.0f, CursorLogic.calculateBoardOffset(3), 0.001f)

  @Test def testCalculateBoardOffsetSize5(): Unit =
    Assertions.assertEquals(-3.0f, CursorLogic.calculateBoardOffset(5), 0.001f)

  @Test def testCalculateBoardOffsetSize7(): Unit =
    Assertions.assertEquals(-4.0f, CursorLogic.calculateBoardOffset(7), 0.001f)

  @Test def testCalculateCursorTransformCenterSize3(): Unit =
    val (x, y, z) = CursorLogic.calculateCursorTransform(Position(2,2,2), 3)
    Assertions.assertEquals(0.0f, x, 0.001f)
    Assertions.assertEquals(0.0f, y, 0.001f)
    Assertions.assertEquals(0.0f, z, 0.001f)

  @Test def testCalculateCursorTransformCorner(): Unit =
    val (x, y, z) = CursorLogic.calculateCursorTransform(Position(1,1,1), 3)
    Assertions.assertEquals(-1.0f, x, 0.001f)
    Assertions.assertEquals(-1.0f, y, 0.001f)
    Assertions.assertEquals(-1.0f, z, 0.001f)

  @Test def testCalculateCursorTransformOppositeCorner(): Unit =
    val (x, y, z) = CursorLogic.calculateCursorTransform(Position(3,3,3), 3)
    Assertions.assertEquals(1.0f, x, 0.001f)
    Assertions.assertEquals(1.0f, y, 0.001f)
    Assertions.assertEquals(1.0f, z, 0.001f)

  @Test def testCalculateCursorTransformCenterSize7(): Unit =
    val (x, y, z) = CursorLogic.calculateCursorTransform(Position(4,4,4), 7)
    Assertions.assertEquals(0.0f, x, 0.001f)
    Assertions.assertEquals(0.0f, y, 0.001f)
    Assertions.assertEquals(0.0f, z, 0.001f)
```

---

### Phase 3: Integration Tests

Create `src/test/scala/go3d/client/gdx/TestGobanDisplay.scala`:

```scala
package go3d.client.gdx

import go3d._
import go3d.client.BaseClient
import go3d.server.{RequestInfo, StatusResponse}
import org.junit.jupiter.api.{Assertions, Test}

class TestGobanDisplay:

  class MockClientWithGame(game: Game, color: Option[Color])
    extends BaseClient("mock", "mock", None, color):
    override def status: StatusResponse =
      StatusResponse(game, List(), true, false, None, RequestInfo(Map(), "", "", false))

  @Test def testCursorPositionsEmptyGame(): Unit =
    val game = Game.start(3)
    val client = MockClientWithGame(game, Some(Black))

    val ownMove = CursorLogic.calculateOwnLastMove(Some(game), client.playerColor)
    val opponentMove = CursorLogic.calculateOpponentLastMove(Some(game), client.playerColor)

    Assertions.assertEquals(None, ownMove)
    Assertions.assertEquals(None, opponentMove)

  @Test def testBlackClientAfterFirstMove(): Unit =
    val game = Game.start(3).set(Position(1,1,1))
    val client = MockClientWithGame(game, Some(Black))

    val ownMove = CursorLogic.calculateOwnLastMove(Some(game), client.playerColor)
    val opponentMove = CursorLogic.calculateOpponentLastMove(Some(game), client.playerColor)

    Assertions.assertEquals(Some(Position(1,1,1)), ownMove)
    Assertions.assertEquals(None, opponentMove)

  @Test def testWhiteClientAfterTwoMoves(): Unit =
    val game = Game.start(3)
      .set(Position(1,1,1))  // Black
      .set(Position(3,3,3))  // White
    val client = MockClientWithGame(game, Some(White))

    val ownMove = CursorLogic.calculateOwnLastMove(Some(game), client.playerColor)
    val opponentMove = CursorLogic.calculateOpponentLastMove(Some(game), client.playerColor)

    Assertions.assertEquals(Some(Position(3,3,3)), ownMove)
    Assertions.assertEquals(Some(Position(1,1,1)), opponentMove)

  @Test def testCursorPositionsAfterCapture(): Unit =
    // Create a situation where a stone is captured
    val game = Game.start(3)
      .set(Position(2,2,2))  // Black
      .set(Position(1,2,2))  // White
      .set(Position(3,2,2))  // Black
      .set(Position(2,1,2))  // White
      .set(Position(2,3,2))  // Black
      .set(Position(2,2,1))  // White
      .set(Position(1,1,1))  // Black (unrelated)
      .set(Position(2,2,3))  // White - captures Black at (2,2,2)

    val client = MockClientWithGame(game, Some(White))

    val ownMove = CursorLogic.calculateOwnLastMove(Some(game), client.playerColor)
    val opponentMove = CursorLogic.calculateOpponentLastMove(Some(game), client.playerColor)

    // Cursors should show last moves, not captured positions
    Assertions.assertEquals(Some(Position(2,2,3)), ownMove)
    Assertions.assertEquals(Some(Position(1,1,1)), opponentMove)

  @Test def testCursorPositionsWithPasses(): Unit =
    val game = Game.start(3)
      .set(Position(1,1,1))  // Black
      .pass()                // White passes
      .set(Position(2,2,2))  // Black
      .pass()                // White passes

    val client = MockClientWithGame(game, Some(Black))

    val ownMove = CursorLogic.calculateOwnLastMove(Some(game), client.playerColor)
    val opponentMove = CursorLogic.calculateOpponentLastMove(Some(game), client.playerColor)

    // Should show last actual moves, ignoring passes
    Assertions.assertEquals(Some(Position(2,2,2)), ownMove)
    Assertions.assertEquals(None, opponentMove)  // White only passed, never placed stone

  @Test def testWatchOnlyClientHasNoCursors(): Unit =
    val game = Game.start(3)
      .set(Position(1,1,1))  // Black
      .set(Position(2,2,2))  // White
    val client = MockClientWithGame(game, None)  // No player color = watch-only

    val ownMove = CursorLogic.calculateOwnLastMove(Some(game), client.playerColor)
    val opponentMove = CursorLogic.calculateOpponentLastMove(Some(game), client.playerColor)

    Assertions.assertEquals(None, ownMove)
    Assertions.assertEquals(None, opponentMove)
```

---

### Phase 4: E2E Tests

Extend `src/test/scala/go3d/server/TestServer.scala` with cursor-specific validation:

```scala
@Test def testCursorPositioningInRealGame(): Unit =
  val server = startTestServer()
  val gameId = createGame(server, 3)
  val blackToken = registerPlayer(server, gameId, Black)
  val whiteToken = registerPlayer(server, gameId, White)

  // Black makes first move
  setStone(server, gameId, blackToken, Position(3,3,3))

  // Verify via status endpoint that move was recorded
  val status1 = getStatus(server, gameId)
  Assertions.assertEquals(1, status1.game.moves.length)

  // Verify cursor logic for Black client
  val blackOwnCursor = CursorLogic.calculateOwnLastMove(Some(status1.game), Some(Black))
  val blackOpponentCursor = CursorLogic.calculateOpponentLastMove(Some(status1.game), Some(Black))
  Assertions.assertEquals(Some(Position(3,3,3)), blackOwnCursor)
  Assertions.assertEquals(None, blackOpponentCursor)

  // White makes second move
  setStone(server, gameId, whiteToken, Position(4,4,4))

  val status2 = getStatus(server, gameId)
  Assertions.assertEquals(2, status2.game.moves.length)

  // Verify cursor logic for both clients
  val blackOwnCursor2 = CursorLogic.calculateOwnLastMove(Some(status2.game), Some(Black))
  val blackOpponentCursor2 = CursorLogic.calculateOpponentLastMove(Some(status2.game), Some(Black))
  Assertions.assertEquals(Some(Position(3,3,3)), blackOwnCursor2)
  Assertions.assertEquals(Some(Position(4,4,4)), blackOpponentCursor2)

  val whiteOwnCursor = CursorLogic.calculateOwnLastMove(Some(status2.game), Some(White))
  val whiteOpponentCursor = CursorLogic.calculateOpponentLastMove(Some(status2.game), Some(White))
  Assertions.assertEquals(Some(Position(4,4,4)), whiteOwnCursor)
  Assertions.assertEquals(Some(Position(3,3,3)), whiteOpponentCursor)

@Test def testCursorPositioningMultiMoveSequence(): Unit =
  val server = startTestServer()
  val gameId = createGame(server, 5)
  val blackToken = registerPlayer(server, gameId, Black)
  val whiteToken = registerPlayer(server, gameId, White)

  val moves = List(
    (Black, Position(3,3,3)),
    (White, Position(4,4,4)),
    (Black, Position(2,2,2)),
    (White, Position(5,5,5)),
    (Black, Position(1,1,1)),
  )

  var lastBlackMove: Option[Position] = None
  var lastWhiteMove: Option[Position] = None

  moves.foreach { case (color, position) =>
    val token = if color == Black then blackToken else whiteToken
    setStone(server, gameId, token, position)

    if color == Black then lastBlackMove = Some(position)
    else lastWhiteMove = Some(position)

    val status = getStatus(server, gameId)

    // Verify Black client's view
    val blackOwn = CursorLogic.calculateOwnLastMove(Some(status.game), Some(Black))
    val blackOpponent = CursorLogic.calculateOpponentLastMove(Some(status.game), Some(Black))
    Assertions.assertEquals(lastBlackMove, blackOwn)
    Assertions.assertEquals(lastWhiteMove, blackOpponent)

    // Verify White client's view
    val whiteOwn = CursorLogic.calculateOwnLastMove(Some(status.game), Some(White))
    val whiteOpponent = CursorLogic.calculateOpponentLastMove(Some(status.game), Some(White))
    Assertions.assertEquals(lastWhiteMove, whiteOwn)
    Assertions.assertEquals(lastBlackMove, whiteOpponent)
  }
```

---

## Test Execution Strategy

### 1. **Continuous Testing**

```bash
# Run unit tests only (fast, < 1 second)
sbt "testOnly *TestCursorLogic"

# Run integration tests (moderate, ~5 seconds)
sbt "testOnly *TestGobanDisplay"

# Run E2E tests (slower, ~10 seconds)
sbt "testOnly *TestServer -- -z cursor"

# Run all cursor-related tests
sbt "testOnly *Test*Cursor*"

# Run all tests
sbt test
```

### 2. **Pre-commit Hook**

Add to `.git_hooks/pre-commit`:

```bash
#!/bin/bash
echo "Running cursor logic tests..."
sbt "testOnly *TestCursorLogic" || {
  echo "Cursor logic tests failed. Commit aborted."
  exit 1
}
echo "Cursor logic tests passed."
```

### 3. **CI/CD Pipeline**

Example GitHub Actions workflow:

```yaml
name: Test Cursor Positioning

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK
        uses: actions/setup-java@v2
        with:
          java-version: '16'
      - name: Run cursor unit tests
        run: sbt "testOnly *TestCursorLogic"
      - name: Run cursor integration tests
        run: sbt "testOnly *TestGobanDisplay"
      - name: Run all tests
        run: sbt test
```

---

## Metrics and Coverage Goals

### Coverage Targets:

- **CursorLogic object**: 100% (pure functions, easy to test)
- **GobanDisplay cursor methods**: 90% (business logic well-covered)
- **SphereMarker.render**: 75% (some rendering code hard to test headlessly)
- **Integration scenarios**: 80% (cover main workflows)
- **E2E scenarios**: 60% (representative sample of real usage)

### Success Criteria:

1. ✅ All position calculation logic has unit tests
2. ✅ All player color scenarios covered (Black client, White client, watch-only)
3. ✅ All game state transitions tested (empty, one move, multiple moves, passes, captures)
4. ✅ No regressions when modifying cursor code
5. ✅ Unit tests run in < 5 seconds
6. ✅ Full test suite runs in < 60 seconds
7. ✅ Tests are maintainable and easy to understand
8. ✅ Tests serve as documentation for cursor behavior

---

## Benefits of This Strategy

### 1. **Fast Feedback Loop**
- Unit tests run in milliseconds without OpenGL initialization
- Developers get immediate feedback on cursor logic changes
- Can run tests in watch mode during development

### 2. **Comprehensive Coverage**
- Tests cover all edge cases: empty games, passes, captures, different player colors
- Multi-layered approach catches bugs at different levels
- Integration tests verify component interaction

### 3. **Maintainable Tests**
- Separation of concerns makes tests easy to understand
- Pure functions in CursorLogic are trivial to test
- Mock clients make integration testing straightforward

### 4. **Regression Prevention**
- Any cursor positioning bug will be caught before deployment
- Refactoring is safe with comprehensive test coverage
- New features can't break existing cursor behavior

### 5. **Documentation**
- Tests serve as executable specification of cursor behavior
- New developers can understand cursor logic by reading tests
- Examples show how cursors behave in different scenarios

### 6. **Refactoring Safety**
- Can refactor rendering code with confidence
- Tests verify behavior, not implementation
- Red-green-refactor workflow is smooth

---

## Implementation Timeline

### Week 1: Foundation
- **Day 1-2**: Refactor cursor logic into CursorLogic object
- **Day 3-4**: Implement unit tests for CursorLogic
- **Day 5**: Verify all unit tests pass, achieve 100% coverage

### Week 2: Integration
- **Day 1-2**: Add cursor state inspection to GobanDisplay
- **Day 3-4**: Implement integration tests
- **Day 5**: Code review and test refinement

### Week 3: E2E
- **Day 1-2**: Add E2E tests to TestServer
- **Day 3**: Add pre-commit hooks
- **Day 4**: Document testing strategy (this file)
- **Day 5**: Team review and training

### Ongoing
- Add new test cases as edge cases are discovered
- Monitor test execution time, optimize if > 60 seconds
- Update documentation as cursor behavior evolves
- Refactor tests to reduce duplication

---

## Common Pitfalls and Solutions

### Pitfall 1: Tests are too slow
**Solution**: Keep unit tests pure and fast. Only use integration/E2E tests when necessary.

### Pitfall 2: Tests are brittle
**Solution**: Test behavior, not implementation. Use the CursorLogic interface rather than inspecting internal state.

### Pitfall 3: Hard to test rendering
**Solution**: Separate calculation logic from rendering. Test calculations thoroughly, rendering lightly.

### Pitfall 4: Test duplication
**Solution**: Use helper functions and fixtures. Extract common test setup to shared utilities.

### Pitfall 5: Tests don't catch bugs
**Solution**: Write tests for edge cases and failure scenarios, not just happy path.

---

## Future Enhancements

### 1. **Property-Based Testing**
Use ScalaCheck to generate random game sequences and verify cursor invariants:
- Own cursor always matches last move of player's color
- Opponent cursor always matches last move of opponent's color
- Cursors are never at the same position (unless both None)

### 2. **Visual Regression Testing**
Capture screenshots of cursor rendering and compare with baseline:
- Use libGDX headless backend to render to texture
- Save PNG snapshots for visual comparison
- Detect pixel-level differences in cursor appearance

### 3. **Performance Testing**
Verify cursor updates don't cause performance degradation:
- Benchmark cursor calculation time
- Test with large game histories (100+ moves)
- Ensure O(n) complexity, not O(n²)

### 4. **Mutation Testing**
Use mutation testing to verify test quality:
- Tools like Stryker can mutate code
- Verify tests catch all mutations
- Improve test coverage for missed mutations

---

## Appendix: Test Data Fixtures

### Common Test Games

```scala
object TestFixtures:

  def emptyGame(size: Int = 3): Game =
    Game.start(size)

  def oneMove(size: Int = 3): Game =
    Game.start(size).set(Position(1,1,1))

  def twoMoves(size: Int = 3): Game =
    Game.start(size)
      .set(Position(1,1,1))
      .set(Position(2,2,2))

  def gameWithPasses(size: Int = 3): Game =
    Game.start(size)
      .set(Position(1,1,1))
      .pass()
      .set(Position(2,2,2))
      .pass()

  def gameWithCapture(size: Int = 3): Game =
    // Game where last move captures a stone
    Game.start(3)
      .set(Position(2,2,2))  // Black
      .set(Position(1,2,2))  // White
      .set(Position(3,2,2))  // Black
      .set(Position(2,1,2))  // White
      .set(Position(2,3,2))  // Black
      .set(Position(2,2,1))  // White
      .set(Position(1,1,1))  // Black
      .set(Position(2,2,3))  // White - captures Black at (2,2,2)
```

---

## Appendix: Running Tests

### Running Specific Test Classes

```bash
# Run only CursorLogic tests
sbt "testOnly go3d.client.gdx.TestCursorLogic"

# Run only GobanDisplay tests
sbt "testOnly go3d.client.gdx.TestGobanDisplay"

# Run tests matching pattern
sbt "testOnly *Cursor*"
```

### Running Specific Test Methods

```bash
# Run single test method
sbt "testOnly go3d.client.gdx.TestCursorLogic -- -z calculateOwnLastMove"

# Run all tests containing "empty"
sbt "testOnly go3d.client.gdx.TestCursorLogic -- -z empty"
```

### Running Tests in Watch Mode

```bash
# Automatically re-run tests on file changes
sbt ~test

# Watch specific test class
sbt "~testOnly go3d.client.gdx.TestCursorLogic"
```

### Generating Coverage Reports

```bash
# Run tests with coverage
sbt clean coverage test coverageReport

# Open coverage report
open target/scala-3.3.1/scoverage-report/index.html
```

---

## Questions and Answers

### Q: Why separate CursorLogic from GobanDisplay?
**A**: Separation of concerns. Pure calculation logic is easy to test without OpenGL. GobanDisplay focuses on rendering.

### Q: Do we need visual regression tests?
**A**: Not initially. Start with logic tests. Add visual tests if rendering bugs occur frequently.

### Q: How do we test watch-only clients?
**A**: Create MockClient with playerColor = None. Verify cursors are both None.

### Q: What if tests are too slow?
**A**: Keep unit tests pure (no OpenGL). Use headless backend for integration tests. Mock server for E2E tests.

### Q: Should we test cursor colors?
**A**: Yes, but lightly. Test that green/red colors are set correctly. Full visual validation is optional.

### Q: How do we test cursor scaling?
**A**: Test that transform uses 1.1f scale factor. Visual size testing is harder and less critical.

---

## Conclusion

This testing strategy provides a comprehensive, maintainable approach to validating cursor positioning in the GDX client. By separating calculation logic from rendering, we can achieve high test coverage with fast, reliable tests. The multi-layered approach ensures bugs are caught early and refactoring is safe.

Start with Phase 1 (refactoring for testability) and Phase 2 (unit tests). These provide the most value with the least effort. Add integration and E2E tests as needed based on bug frequency and team confidence.

**Remember**: Tests are documentation. Write them clearly, name them well, and keep them simple. Future developers (including yourself) will thank you.
