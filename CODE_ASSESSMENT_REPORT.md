# Code Assessment Report: Go-3D

**Date:** November 24, 2025
**Version Assessed:** 0.7.16
**Scala Version:** 3.7.3

---

## Executive Summary

This is a well-architected 3D Go game implementation with clear separation of concerns, comprehensive test coverage, and good use of modern Scala idioms. The codebase demonstrates pragmatic engineering decisions, balancing functional programming principles with performance requirements. Recent static analysis work has significantly improved code quality, particularly eliminating all null usages.

**Overall Assessment: Good quality codebase with room for targeted improvements**

---

## 1. Architecture Analysis

### 1.1 Strengths

#### Clear Layer Separation
The codebase follows a clean three-tier architecture:
- **Domain Layer** (`go3d.*`): Pure game logic with minimal dependencies
- **Server Layer** (`go3d.server.*`): HTTP API with Cats Effect IO for async operations
- **Client Layer** (`go3d.client.*`): Multiple client implementations sharing common abstractions

#### Smart Board Representation
The `Goban` class uses a sentinel-value approach with `(size+2)^3` array:
```
Board size N -> Array size N+2
Index 0 and N+1 = Sentinel values
Index 1 to N = Playable area
```
This eliminates boundary checking in neighbor calculations, a significant performance optimization for the frequent liberty calculations.

#### Effective Use of Scala 3 Features
- Union types for moves: `Move | Pass`
- Given instances for Circe codecs
- Case classes for domain model immutability
- Tail recursion annotations for iterative algorithms

### 1.2 Areas for Improvement

#### 1.2.1 Server Singleton Pattern (High Priority)

**Issue:** `Games` and `Players` objects use mutable global state.

```scala
// Games.scala
object Games:
  private val activeGames: mutable.Map[String, Game] = mutable.Map()
  private val archivedGames: mutable.Map[String, Game] = mutable.Map()
  var fileIO: Option[FileIO] = None
```

**Concerns:**
- Makes testing harder (requires careful state cleanup)
- Not thread-safe for concurrent requests
- Prevents multiple server instances or easy scaling

**Recommendation:** Refactor to dependency injection pattern using Cats Effect `Ref` for thread-safe state:
```scala
case class GameStore(
  activeGames: Ref[IO, Map[String, Game]],
  archivedGames: Ref[IO, Map[String, Game]],
  fileIO: FileIO
)
```

#### 1.2.2 HTTP API Design (Medium Priority)

**Issue:** All endpoints use GET requests, including state-modifying operations.

```scala
// GoHttpService.scala
case request@GET -> Root / "set" / GameId(gameId) / IntVar(x) / IntVar(y) / IntVar(z)
case request@GET -> Root / "pass" / GameId(gameId)
```

**Concerns:**
- Violates REST principles (GET should be idempotent)
- Risk of accidental replays via browser history/caching
- Problematic for logging and security auditing

**Recommendation:** Use POST for state-modifying operations:
- `POST /games` - Create new game
- `POST /games/{id}/moves` - Make a move (with JSON body)
- `POST /games/{id}/pass` - Pass turn
- `GET /games/{id}` - Get status (keep as GET)

#### 1.2.3 Infinite Server Loop (Medium Priority)

**Issue:** `GoServer.scala` has an unreachable shutdown mechanism:

```scala
while true do
  Thread.sleep(1000)
  if false then shutdown.unsafeRunSync()  // Dead code
```

**Recommendation:** Implement proper graceful shutdown:
```scala
val shutdownSignal = SignalHandler.install(Signal("INT"), ...) // Handle SIGINT
shutdownSignal.flatMap(_ => shutdown).unsafeRunSync()
```

#### 1.2.4 Error Handling Consistency (Medium Priority)

**Issue:** Mixed error handling patterns across the codebase:
- Domain layer: Custom exceptions (`IllegalMove` hierarchy)
- Server layer: Mix of exceptions and `Either`
- Client layer: Exception catching with recovery

**Recommendation:** Standardize on `Either[Error, A]` or `IO.raiseError` for recoverable errors, keeping exceptions only for truly exceptional conditions.

---

## 2. Code Quality Analysis

### 2.1 Strengths

#### Null-Free Codebase
All null usages have been eliminated (previously 12 instances). This significantly improves type safety and reduces NPE risks.

#### Comprehensive Test Suite
- 33 test files with 4,091 lines of test code
- 2.3:1 test-to-production ratio
- Good coverage of domain logic, server endpoints, and client behavior
- Performance regression tests (Bug #66)

#### Consistent Code Style
- 100-character line limit enforced
- Consistent naming conventions
- Well-organized imports

### 2.2 Areas for Improvement

#### 2.2.1 Unsafe Collection Operations (High Priority)

**Issue:** 27 occurrences of `.head`, `.last`, `.tail` which throw on empty collections.

**Locations:**
- `SetStrategy.scala:87` - `minBy(_._1)._2` can fail on empty groups
- `Games.scala:76` - `saveGame.players.last` assumes non-empty map
- `Game.scala:24` - `moves.last.isInstanceOf[Pass]` unchecked

**Recommendation:** Replace with safe alternatives:
```scala
// Before
moves.last.isInstanceOf[Pass]

// After
moves.lastOption.exists(_.isInstanceOf[Pass])
```

#### 2.2.2 OptionPartial Usage (Medium Priority)

**Issue:** 5 occurrences of `.get` on `Option` which throw `NoSuchElementException`.

**Example in `Games.scala:56`:**
```scala
def archivedGameIds: Iterable[String] = fileIO.get.getArchivedGames
```

**Recommendation:** Use pattern matching or `getOrElse`:
```scala
def archivedGameIds: Iterable[String] =
  fileIO.fold(Iterable.empty[String])(_.getArchivedGames)
```

#### 2.2.3 Mutable State in Clients (Low Priority)

**Issue:** Bot client uses mutable vars for configuration:
```scala
object BotClient:
  var executionTimes: List[Long] = List()
  private[client] var strategies: Array[String] = Array()
  private var maxThinkingTimeMs: Int = 0
```

**Recommendation:** Pass configuration through parameters or use a config case class.

#### 2.2.4 Early Returns (Low Priority)

**Issue:** 20 occurrences of `return` statements. While acceptable in Scala 3, they can make code harder to reason about.

**Example in `Goban.scala:68`:**
```scala
private def hasLibertiesHelper(...): Boolean =
  if at(move.position) != move.color then return false
  if visited.contains(move.position) then return false
  // ...
```

**Recommendation:** Consider refactoring hot paths to use guard clauses or early pattern matching for clarity, but acknowledge that returns are sometimes clearer for complex algorithms.

---

## 3. Functionality Analysis

### 3.1 Strengths

#### Complete Go Rule Implementation
- Liberty calculations correctly handle connected groups
- Ko rule prevents immediate recapture
- Suicide rule properly enforced
- Scoring includes territory and captures

#### Multiple Client Options
- **AsciiClient:** Terminal-based for CLI interaction
- **GDXClient:** 3D visualization with LibGDX
- **BotClient:** Automated play with configurable strategies

#### Flexible Bot Strategy System
Eight composable strategies that can be combined:
- `random`, `closestToCenter`, `onStarPoints`
- `maximizeOwnLiberties`, `minimizeOpponentLiberties`
- `prioritiseCapture`, `maximizeDistance`

### 3.2 Areas for Improvement

#### 3.2.1 Game State Synchronization (High Priority)

**Issue:** No WebSocket support for real-time updates. Clients must poll:
```scala
// BotClient.scala
private val PULL_WAIT_MS = 10
while !status.ready do
  Thread.sleep(PULL_WAIT_MS)
  status = client.status
```

**Impact:** Inefficient for network and CPU, poor UX for human players.

**Recommendation:** Implement WebSocket endpoint for game events:
```scala
GET /ws/games/{gameId} -> WebSocket connection
Events: MoveEvent, CaptureEvent, GameOverEvent
```

#### 3.2.2 Undo/Redo Support (Medium Priority)

**Issue:** No ability to undo moves in practice games.

**Recommendation:** Add optional undo support for non-competitive games:
```scala
def undoLastMove: Game =
  if moves.isEmpty then this
  else restoreFromCaptures(moves.init)
```

#### 3.2.3 Time Controls (Medium Priority)

**Issue:** No time control support for competitive play.

**Recommendation:** Add configurable time controls:
- Fischer increment
- Byoyomi periods
- Simple countdown

#### 3.2.4 Game Resume (Low Priority)

**Issue:** GDXClient is watch-only, cannot make moves.

**Recommendation:** Allow authenticated GDXClient to make moves via keyboard/mouse input.

---

## 4. Performance Analysis

### 4.1 Strengths

#### Optimized Liberty Calculation
The visited set prevents redundant checks in connected group detection:
```scala
private def hasLibertiesHelper(move: Move, visited: mutable.Set[Position]): Boolean =
  if visited.contains(move.position) then return false
  visited += move.position
  // ...
```

#### Deep Copy Optimization
Bug #66 fix eliminates unnecessary deep copies during liberty checks, significantly improving performance.

### 4.2 Areas for Improvement

#### 4.2.1 Board Copying on Every Move (Medium Priority)

**Issue:** Every move creates a full board copy:
```scala
def setStone(move: Move): Goban =
  val newStones = deepCopy(stones)  // O(n^3) copy
  newStones(x)(y)(z) = color
  Goban(size, newStones)
```

For a 7x7x7 board, this copies 729 cells per move.

**Recommendation:** Consider structural sharing or copy-on-write optimization for frequently accessed paths.

#### 4.2.2 Lazy Areas Recalculation (Low Priority)

**Issue:** `areas` is lazy but recalculated entirely on access:
```scala
lazy val areas: Set[Area] = calculateAreas()
```

Since `Goban` is immutable, each new board recalculates all areas.

**Recommendation:** For AI-heavy usage, consider incremental area updates.

---

## 5. Security Analysis

### 5.1 Strengths

- Authentication tokens for move authorization
- Bearer token pattern in headers
- Input validation for board positions

### 5.2 Areas for Improvement

#### 5.2.1 Token Generation (High Priority)

**Issue:** Token generation method not visible in reviewed code, but should use `SecureRandom`.

**Recommendation:** Verify tokens are:
- Cryptographically random (use `SecureRandom`)
- Sufficiently long (32+ bytes)
- URL-safe encoding

#### 5.2.2 Rate Limiting (Medium Priority)

**Issue:** No rate limiting on API endpoints.

**Recommendation:** Add rate limiting to prevent:
- Denial of service
- Brute force token guessing
- Excessive polling

#### 5.2.3 Input Validation (Low Priority)

**Issue:** Board size validated but worth reviewing all inputs:
```scala
if size < MinBoardSize then throw BadBoardSize(size, "too small")
if size > MaxBoardSize then throw BadBoardSize(size, "too big")
```

**Recommendation:** Ensure all external inputs are validated before use.

---

## 6. Testing Analysis

### 6.1 Strengths

- **High Coverage:** Test files cover all major components
- **Multiple Test Styles:** JUnit 5 + ScalaTest
- **Performance Tests:** `TestBug66Performance` prevents regressions
- **Test Utilities:** `Common.scala`, `MockClient.scala` support test development

### 6.2 Areas for Improvement

#### 6.2.1 Integration Tests (Medium Priority)

**Issue:** Limited end-to-end testing of full game scenarios.

**Recommendation:** Add integration tests that:
- Start server
- Register two players
- Play complete game
- Verify final score

#### 6.2.2 Property-Based Testing (Low Priority)

**Issue:** No property-based tests for invariants.

**Recommendation:** Add ScalaCheck properties:
```scala
forAll { (moves: List[Move]) =>
  // Invariant: game.score values are always non-negative
  game.score.values.forall(_ >= 0)
}
```

---

## 7. Recommendations Summary

### High Priority
1. **Refactor global state** - Use DI with Cats Effect Ref for thread-safe state
2. **Fix unsafe collection operations** - Replace `.head`/`.last`/`.tail` with safe alternatives
3. **Add WebSocket support** - Real-time game updates instead of polling
4. **Verify token security** - Ensure cryptographically secure token generation

### Medium Priority
5. **Use proper HTTP methods** - POST for state-modifying operations
6. **Add rate limiting** - Prevent abuse and DoS
7. **Implement graceful shutdown** - Proper signal handling
8. **Add time controls** - For competitive play
9. **Improve error handling** - Consistent Either/IO pattern

### Low Priority
10. **Add undo support** - For practice games
11. **Reduce client mutable state** - Use immutable configuration
12. **Add property-based tests** - Verify game invariants
13. **Enable GDX client moves** - Full interactive 3D client

---

## 8. Conclusion

Go-3D is a well-designed and implemented project demonstrating solid software engineering practices. The domain model is clean and well-tested, the server architecture leverages modern Scala libraries effectively, and the multiple client options showcase flexibility.

The main areas for improvement are:
1. **Architecture:** Moving from singleton state to DI for better testability and scalability
2. **API Design:** Adopting REST conventions for the HTTP API
3. **Real-time:** Adding WebSocket support for better UX
4. **Safety:** Eliminating remaining unsafe operations (head/last/tail)

The codebase is in good shape for continued development. Addressing the high-priority items would significantly improve reliability and maintainability, while the lower-priority items represent opportunities for feature enhancement and polish.

---

*Report generated by Claude Code Assessment*
