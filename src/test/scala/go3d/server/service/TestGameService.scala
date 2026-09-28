package go3d.server.service

import go3d.BadBoardSize
import go3d.BadColor
import go3d.Black
import go3d.Color
import go3d.Game
import go3d.GameOver
import go3d.Ko
import go3d.OutsideBoard
import go3d.PositionOccupied
import go3d.Suicide
import go3d.White
import go3d.server.DuplicateColor
import go3d.server.NonexistentGame
import go3d.server.NotReadyToSet
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.util.Try

private val NowMillis = 1_000_000L

/** Delegates to `inner`, but lets another write happen right after the next `getGame`. */
class InterleavingStore(inner: InMemoryGameStore, interleave: () => Unit) extends GameStore:
  private val interleaved = AtomicBoolean(false)
  def createGame(gameId: String, game: Game): Try[Unit] = inner.createGame(gameId, game)
  def getGame(gameId: String): Try[Option[StoredGame]] =
    val result = inner.getGame(gameId)
    if !interleaved.getAndSet(true) then interleave()
    result
  def updateGame(gameId: String, expectedVersion: Long, game: Game): Try[Unit] =
    inner.updateGame(gameId, expectedVersion, game)
  def registerPlayer(gameId: String, color: Color, tokenHash: String): Try[Unit] =
    inner.registerPlayer(gameId, color, tokenHash)
  def playerColor(gameId: String, tokenHash: String): Try[Option[Color]] =
    inner.playerColor(gameId, tokenHash)
  def openGames(): Try[Array[String]] = inner.openGames()
  def markCompleted(gameId: String, archiveKey: String, expiresAt: Long): Try[Unit] =
    inner.markCompleted(gameId, archiveKey, expiresAt)

class TestGameService:
  private val store = InMemoryGameStore()
  private val archive = InMemoryGameArchive(false)
  private val counter = AtomicInteger()
  private val service = serviceFor(store, archive)

  private def serviceFor(gameStore: GameStore, gameArchive: GameArchive): GameService =
    new GameService(
      gameStore, gameArchive, () => NowMillis,
      () => "G" + counter.incrementAndGet().toString,
      () => "T" + counter.incrementAndGet().toString
    )

  /** A game of `size` with both players registered: (game id, black token, white token). */
  private def startedGame(size: Int): (String, String, String) =
    val gameId = service.newGame(size).success.value.id
    val black = service.register(gameId, Black).success.value.authToken
    val white = service.register(gameId, White).success.value.authToken
    (gameId, black, white)

  private def finishedGame(): (String, String, String) =
    val (gameId, black, white) = startedGame(3)
    service.pass(gameId, black).success.value
    service.pass(gameId, white).success.value
    (gameId, black, white)

  @Test def testNewGameIsStoredEmpty(): Unit =
    val created = service.newGame(5).success.value
    Assertions.assertEquals(5, created.size)
    val game = service.status(created.id, None).success.value.game
    Assertions.assertEquals(5, game.size)
    Assertions.assertTrue(game.moves.isEmpty)

  @Test def testDefaultServiceGeneratesValidIds(): Unit =
    val gameId = GameService(store, archive).newGame(3).success.value.id
    Assertions.assertTrue(go3d.server.IdGenerator.isValidId(gameId))

  @Test def testNewGameWithBadSizeFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[BadBoardSize], service.newGame(1).failure.exception
    )

  @Test def testRegisterNonexistentGameFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[NonexistentGame], service.register("NOGAME", Black).failure.exception
    )

  @Test def testRegisterDuplicateColorFails(): Unit =
    val gameId = service.newGame(3).success.value.id
    service.register(gameId, Black).success.value
    Assertions.assertInstanceOf(
      classOf[DuplicateColor], service.register(gameId, Black).failure.exception
    )

  @Test def testRegisterReturnsDistinctTokens(): Unit =
    val (_, black, white) = startedGame(3)
    Assertions.assertNotEquals(black, white)

  @Test def testBlackRegisteringAfterWhiteIsReady(): Unit =
    val gameId = service.newGame(3).success.value.id
    Assertions.assertFalse(service.register(gameId, White).success.value.ready)
    Assertions.assertTrue(service.register(gameId, Black).success.value.ready)

  @Test def testRegisterAfterGameOverFails(): Unit =
    val (gameId, _, _) = finishedGame()
    Assertions.assertInstanceOf(
      classOf[GameOver], service.register(gameId, Black).failure.exception
    )

  @Test def testRegisterEmptyColorFails(): Unit =
    val gameId = service.newGame(3).success.value.id
    Assertions.assertInstanceOf(
      classOf[BadColor], service.register(gameId, go3d.Empty).failure.exception
    )
    Assertions.assertInstanceOf(
      classOf[BadColor], service.register(gameId, go3d.Sentinel).failure.exception
    )
    Assertions.assertTrue(service.openGames().success.value.isEmpty)

  @Test def testStatusOfNonexistentGameFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[NonexistentGame], service.status("NOGAME", None).failure.exception
    )

  @Test def testStatusWithoutTokenShowsBoardOnly(): Unit =
    val (gameId, _, _) = startedGame(3)
    val status = service.status(gameId, None).success.value
    Assertions.assertFalse(status.ready)
    Assertions.assertTrue(status.moves.isEmpty)
    Assertions.assertEquals(None, status.playerColor)

  @Test def testStatusForPlayerToMoveIsReady(): Unit =
    val (gameId, black, _) = startedGame(3)
    val status = service.status(gameId, Some(black)).success.value
    Assertions.assertTrue(status.ready)
    Assertions.assertEquals(27, status.moves.length)
    Assertions.assertEquals(Some(Black), status.playerColor)

  @Test def testStatusForPlayerNotToMoveIsNotReady(): Unit =
    val (gameId, _, white) = startedGame(3)
    Assertions.assertFalse(service.status(gameId, Some(white)).success.value.ready)

  @Test def testStatusBeforeOpponentRegisteredIsNotReady(): Unit =
    val gameId = service.newGame(3).success.value.id
    val black = service.register(gameId, Black).success.value.authToken
    Assertions.assertFalse(service.status(gameId, Some(black)).success.value.ready)

  @Test def testStatusWithInvalidTokenFails(): Unit =
    val (gameId, _, _) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[InvalidToken], service.status(gameId, Some("WRONG")).failure.exception
    )

  @Test def testTokenOfOtherGameIsInvalid(): Unit =
    val (_, black, _) = startedGame(3)
    val (otherGameId, _, _) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[InvalidToken], service.set(otherGameId, black, 1, 1, 1).failure.exception
    )

  @Test def testSetStoresMove(): Unit =
    val (gameId, black, _) = startedGame(3)
    val response = service.set(gameId, black, 2, 2, 2).success.value
    Assertions.assertFalse(response.ready)
    Assertions.assertEquals(Black, service.status(gameId, None).success.value.game.at(2, 2, 2))

  @Test def testOpponentIsReadyAfterSet(): Unit =
    val (gameId, black, white) = startedGame(3)
    service.set(gameId, black, 2, 2, 2).success.value
    Assertions.assertTrue(service.status(gameId, Some(white)).success.value.ready)

  @Test def testSetOnNonexistentGameFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[NonexistentGame], service.set("NOGAME", "T1", 1, 1, 1).failure.exception
    )

  @Test def testSetWithInvalidTokenFails(): Unit =
    val (gameId, _, _) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[InvalidToken], service.set(gameId, "WRONG", 1, 1, 1).failure.exception
    )

  @Test def testSetOnWrongTurnFails(): Unit =
    val (gameId, _, white) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[NotReadyToSet], service.set(gameId, white, 1, 1, 1).failure.exception
    )

  @Test def testPassOnWrongTurnFails(): Unit =
    val (gameId, _, white) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[NotReadyToSet], service.pass(gameId, white).failure.exception
    )

  @Test def testSetOutsideBoardFails(): Unit =
    val (gameId, black, _) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[OutsideBoard], service.set(gameId, black, 4, 1, 1).failure.exception
    )

  @Test def testSetBelowBoardFails(): Unit =
    val (gameId, black, _) = startedGame(3)
    Assertions.assertTrue(service.set(gameId, black, 0, 1, 1).isFailure)

  @Test def testSetOnOccupiedPositionFails(): Unit =
    val (gameId, black, white) = startedGame(3)
    service.set(gameId, black, 2, 2, 2).success.value
    Assertions.assertInstanceOf(
      classOf[PositionOccupied], service.set(gameId, white, 2, 2, 2).failure.exception
    )

  @Test def testSuicideFails(): Unit =
    val (gameId, black, white) = startedGame(3)
    // white occupies all three neighbors of the corner 1,1,1 while black plays elsewhere
    service.set(gameId, black, 3, 3, 3).success.value
    service.set(gameId, white, 2, 1, 1).success.value
    service.set(gameId, black, 3, 3, 2).success.value
    service.set(gameId, white, 1, 2, 1).success.value
    service.set(gameId, black, 3, 2, 3).success.value
    service.set(gameId, white, 1, 1, 2).success.value
    Assertions.assertInstanceOf(
      classOf[Suicide], service.set(gameId, black, 1, 1, 1).failure.exception
    )

  @Test def testKoFails(): Unit =
    val (gameId, black, white) = startedGame(5)
    // the position of TestGame.testPossibleMovesWithKo: white's last move captures 2,2,3
    val moves = List(
      (black, (2, 2, 3)), (white, (2, 2, 4)), (black, (2, 3, 2)), (white, (2, 3, 3)),
      (black, (2, 1, 2)), (white, (2, 1, 3)), (black, (3, 2, 2)), (white, (3, 2, 3)),
      (black, (1, 2, 2)), (white, (1, 2, 3)), (black, (2, 2, 1)), (white, (2, 2, 2))
    )
    for (token, (x, y, z)) <- moves do service.set(gameId, token, x, y, z).success.value
    Assertions.assertInstanceOf(
      classOf[Ko], service.set(gameId, black, 2, 2, 3).failure.exception
    )

  /** A started game with black at 1,1,1 and white at 3,3,3: its id. */
  private def gameWithTwoMoves(): String =
    val (gameId, black, white) = startedGame(3)
    service.set(gameId, black, 1, 1, 1).success.value
    service.set(gameId, white, 3, 3, 3).success.value
    gameId

  @Test def testStatusAtMoveReplaysFirstMoves(): Unit =
    val status = service.statusAt(gameWithTwoMoves(), 1).success.value
    Assertions.assertEquals(1, status.game.moves.length)
    Assertions.assertEquals(Black, status.game.at(1, 1, 1))
    Assertions.assertEquals(go3d.Empty, status.game.at(3, 3, 3))
    Assertions.assertFalse(status.ready)
    Assertions.assertEquals(None, status.playerColor)

  @Test def testStatusAtMoveClampsCount(): Unit =
    val gameId = gameWithTwoMoves()
    Assertions.assertEquals(0, service.statusAt(gameId, -1).success.value.game.moves.length)
    Assertions.assertEquals(0, service.statusAt(gameId, 0).success.value.game.moves.length)
    Assertions.assertEquals(2, service.statusAt(gameId, 99).success.value.game.moves.length)

  @Test def testStatusAtMoveOfNonexistentGameFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[NonexistentGame], service.statusAt("NOGAME", 1).failure.exception
    )

  @Test def testArchivedUrlOnlyForFinishedGame(): Unit =
    val (running, _, _) = startedGame(3)
    Assertions.assertEquals(None, service.archivedUrl(running).success.value)
    val (finished, _, _) = finishedGame()
    Assertions.assertTrue(service.archivedUrl(finished).success.value.exists(_.contains(finished)))

  @Test def testArchivedUrlFailsWhenArchiveFails(): Unit =
    val failingService = serviceFor(store, InMemoryGameArchive(true))
    Assertions.assertTrue(failingService.archivedUrl("G1").isFailure)

  @Test def testTwoPassesEndGame(): Unit =
    val (gameId, _, _) = finishedGame()
    Assertions.assertTrue(service.status(gameId, None).success.value.over)

  @Test def testFinishedGameIsArchivedAndExpires(): Unit =
    val (gameId, _, _) = finishedGame()
    Assertions.assertTrue(archive.archived(gameId).exists(_.game.isOver))
    Assertions.assertEquals(
      Some(("archives/" + gameId + ".json", NowMillis / 1000 + GameService.RetentionSeconds)),
      store.completion(gameId)
    )

  @Test def testUnfinishedGameIsNotArchived(): Unit =
    val (gameId, black, _) = startedGame(3)
    service.pass(gameId, black).success.value
    Assertions.assertEquals(None, archive.archived(gameId))
    Assertions.assertEquals(None, store.completion(gameId))

  @Test def testArchiveFailureDoesNotFailFinalMove(): Unit =
    val failingService = serviceFor(store, InMemoryGameArchive(true))
    val gameId = failingService.newGame(3).success.value.id
    val black = failingService.register(gameId, Black).success.value.authToken
    val white = failingService.register(gameId, White).success.value.authToken
    failingService.pass(gameId, black).success.value
    Assertions.assertTrue(failingService.pass(gameId, white).success.value.over)
    Assertions.assertEquals(None, store.completion(gameId))

  @Test def testMoveAfterGameOverFails(): Unit =
    val (gameId, black, _) = finishedGame()
    Assertions.assertInstanceOf(
      classOf[GameOver], service.set(gameId, black, 1, 1, 1).failure.exception
    )

  @Test def testConcurrentMoveFails(): Unit =
    val (gameId, black, _) = startedGame(3)
    val stored = store.getGame(gameId).success.value
    // another request stores a move between this request's read and its write
    val racingService = serviceFor(
      InterleavingStore(store, () =>
        stored.foreach(s => store.updateGame(gameId, s.version, s.game).success.value)
      ),
      archive
    )
    Assertions.assertInstanceOf(
      classOf[ConcurrentModification], racingService.set(gameId, black, 1, 1, 1).failure.exception
    )
    Assertions.assertEquals(
      go3d.Empty, service.status(gameId, None).success.value.game.at(1, 1, 1)
    )

  @Test def testOpenGamesListsGamesWaitingForWhite(): Unit =
    val gameId = service.newGame(3).success.value.id
    service.register(gameId, Black).success.value
    Assertions.assertTrue(service.openGames().success.value.contains(gameId))
    service.register(gameId, White).success.value
    Assertions.assertFalse(service.openGames().success.value.contains(gameId))

class TestUnconfiguredArchive:
  @Test def testArchiveFails(): Unit =
    val saveGame = go3d.server.SaveGame(Game.start(3).success.value, Map())
    Assertions.assertTrue(UnconfiguredArchive.archive("G1", saveGame).isFailure)

  @Test def testUnconfiguredArchiveHasNoUrl(): Unit =
    Assertions.assertEquals(None, UnconfiguredArchive.url("G1").success.value)
