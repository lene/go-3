package go3d.server.service

import go3d.BadBoardSize
import go3d.BadColor
import go3d.Black
import go3d.Color
import go3d.Game
import go3d.GameOver
import go3d.BadKomi
import go3d.Ko
import go3d.Komi
import go3d.OutsideBoard
import go3d.PositionOccupied
import go3d.Suicide
import go3d.White
import go3d.server.DuplicateColor
import go3d.server.NonexistentGame
import go3d.server.NotReadyToSet
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.util.{Failure, Try}

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
  def unarchivedFinishedGames(): Try[List[String]] = inner.unarchivedFinishedGames()

/** An archive that fails for one game and delegates to `inner` for all others. */
class FailingForGameArchive(inner: GameArchive, failingGameId: String) extends GameArchive:
  def archive(gameId: String, saveGame: go3d.server.SaveGame): Try[String] =
    if gameId == failingGameId then Failure(IllegalStateException("archive unavailable"))
    else inner.archive(gameId, saveGame)
  def url(gameId: String): Try[Option[String]] = inner.url(gameId)

class TestGameService:
  private val store = InMemoryGameStore()
  private val archive = InMemoryGameArchive(false)
  private val counter = AtomicInteger()
  private val service = serviceFor(store, archive)

  private def serviceFor(gameStore: GameStore, gameArchive: GameArchive): GameService =
    new GameService(
      gameStore,
      gameArchive,
      () => NowMillis,
      () => "G" + counter.incrementAndGet().toString,
      () => "T" + counter.incrementAndGet().toString
    )

  /** A game of `size` with both players registered: (game id, black token, white token). */
  private def startedGame(size: Int): (String, String, String) = startedGame(size, Komi.Default)

  private def startedGame(size: Int, komi: Double): (String, String, String) =
    val gameId = service.newGame(size, komi).success.value.id
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

  @Test def testNewGameHasDefaultKomi(): Unit =
    val created = service.newGame(5).success.value
    Assertions.assertEquals(0.5, service.status(created.id, None).success.value.game.komi)

  @Test def testNewGameWithKomiStoresKomi(): Unit =
    val created = service.newGame(5, 6.5).success.value
    Assertions.assertEquals(5, created.size)
    Assertions.assertEquals(6.5, service.status(created.id, None).success.value.game.komi)
    Assertions.assertEquals(Some(6.5), store.getGame(created.id).success.value.map(_.game.komi))

  @Test def testNewGameWithInvalidKomiFailsAndStoresNothing(): Unit =
    for komi <- List(0.0, 7.0, -7.0, Double.NaN, 27.5, 6.500000000000001) do
      Assertions.assertInstanceOf(classOf[BadKomi], service.newGame(3, komi).failure.exception)
    Assertions.assertEquals(0, store.gameCount)

  @Test def testNewGameChecksSizeBeforeKomi(): Unit =
    Assertions.assertInstanceOf(classOf[BadBoardSize], service.newGame(4, 7.0).failure.exception)

  @Test def testRegisterResponseCarriesKomi(): Unit =
    val gameId = service.newGame(3, 6.5).success.value.id
    Assertions.assertEquals(6.5, service.register(gameId, Black).success.value.game.komi)
    Assertions.assertEquals(6.5, service.register(gameId, White).success.value.game.komi)

  @Test def testKomiSurvivesMovesPassesAndReplay(): Unit =
    val (gameId, black, white) = startedGame(3, 6.5)
    service.set(gameId, black, 2, 2, 2).success.value
    Assertions.assertEquals(6.5, service.set(gameId, white, 1, 1, 1).success.value.game.komi)
    Assertions.assertEquals(6.5, service.pass(gameId, black).success.value.game.komi)
    Assertions.assertEquals(6.5, service.status(gameId, Some(white)).success.value.game.komi)
    for count <- 0 to 3 do
      Assertions.assertEquals(6.5, service.statusAt(gameId, count).success.value.game.komi)

  @Test def testKomiSurvivesCapture(): Unit =
    val (gameId, black, white) = startedGame(5, 6.5)
    playPositions(gameId, black, white, GamePositions.BeforeKo)
    val game = service.status(gameId, None).success.value.game
    Assertions.assertTrue(game.captures.nonEmpty)
    Assertions.assertEquals(6.5, game.komi)

  /** Plays the capture-free game that black leads by one point on a board of size 3 and ends it. */
  private def finishedGameWithKomi(komi: Double): Game =
    val (gameId, black, white) = startedGame(3, komi)
    playPositions(gameId, black, white, GamePositions.BlackLeadsByOne)
    service.pass(gameId, white).success.value
    service.pass(gameId, black).success.value.game

  @Test def testWinnerWithDefaultKomiIsBlack(): Unit =
    val game = finishedGameWithKomi(Komi.Default)
    Assertions.assertTrue(game.isOver)
    Assertions.assertEquals(Some(Black), game.winner)

  @Test def testWinnerWithLargeKomiIsWhite(): Unit =
    val game = finishedGameWithKomi(6.5)
    Assertions.assertTrue(game.isOver)
    Assertions.assertEquals(Some(White), game.winner)

  @Test def testFinishedGameIsArchivedWithKomi(): Unit =
    val (gameId, black, white) = startedGame(3, 6.5)
    service.pass(gameId, black).success.value
    service.pass(gameId, white).success.value
    Assertions.assertEquals(Some(6.5), archive.archived(gameId).map(_.game.komi))

  @Test def testDefaultServiceGeneratesValidIds(): Unit =
    val gameId = GameService(store, archive).newGame(3).success.value.id
    Assertions.assertTrue(go3d.server.IdGenerator.isValidId(gameId))

  @Test def testNewGameWithBadSizeFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[BadBoardSize],
      service.newGame(1).failure.exception
    )

  @Test def testRegisterNonexistentGameFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[NonexistentGame],
      service.register("NOGAME", Black).failure.exception
    )

  @Test def testRegisterDuplicateColorFails(): Unit =
    val gameId = service.newGame(3).success.value.id
    service.register(gameId, Black).success.value
    Assertions.assertInstanceOf(
      classOf[DuplicateColor],
      service.register(gameId, Black).failure.exception
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
      classOf[GameOver],
      service.register(gameId, Black).failure.exception
    )

  @Test def testRegisterEmptyColorFails(): Unit =
    val gameId = service.newGame(3).success.value.id
    Assertions.assertInstanceOf(
      classOf[BadColor],
      service.register(gameId, go3d.Empty).failure.exception
    )
    Assertions.assertInstanceOf(
      classOf[BadColor],
      service.register(gameId, go3d.Sentinel).failure.exception
    )
    Assertions.assertTrue(service.openGames().success.value.isEmpty)

  @Test def testStatusOfNonexistentGameFails(): Unit =
    Assertions.assertInstanceOf(
      classOf[NonexistentGame],
      service.status("NOGAME", None).failure.exception
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
      classOf[InvalidToken],
      service.status(gameId, Some("WRONG")).failure.exception
    )

  @Test def testTokenOfOtherGameIsInvalid(): Unit =
    val (_, black, _) = startedGame(3)
    val (otherGameId, _, _) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[InvalidToken],
      service.set(otherGameId, black, 1, 1, 1).failure.exception
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
      classOf[NonexistentGame],
      service.set("NOGAME", "T1", 1, 1, 1).failure.exception
    )

  @Test def testSetWithInvalidTokenFails(): Unit =
    val (gameId, _, _) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[InvalidToken],
      service.set(gameId, "WRONG", 1, 1, 1).failure.exception
    )

  @Test def testSetOnWrongTurnFails(): Unit =
    val (gameId, _, white) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[NotReadyToSet],
      service.set(gameId, white, 1, 1, 1).failure.exception
    )

  @Test def testPassOnWrongTurnFails(): Unit =
    val (gameId, _, white) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[NotReadyToSet],
      service.pass(gameId, white).failure.exception
    )

  @Test def testSetOutsideBoardFails(): Unit =
    val (gameId, black, _) = startedGame(3)
    Assertions.assertInstanceOf(
      classOf[OutsideBoard],
      service.set(gameId, black, 4, 1, 1).failure.exception
    )

  @Test def testSetBelowBoardFails(): Unit =
    val (gameId, black, _) = startedGame(3)
    Assertions.assertTrue(service.set(gameId, black, 0, 1, 1).isFailure)

  @Test def testSetOnOccupiedPositionFails(): Unit =
    val (gameId, black, white) = startedGame(3)
    service.set(gameId, black, 2, 2, 2).success.value
    Assertions.assertInstanceOf(
      classOf[PositionOccupied],
      service.set(gameId, white, 2, 2, 2).failure.exception
    )

  @Test def testSuicideFails(): Unit =
    val (gameId, black, white) = startedGame(3)
    playPositions(gameId, black, white, GamePositions.BeforeSuicide)
    Assertions.assertInstanceOf(
      classOf[Suicide],
      service.set(gameId, black, 1, 1, 1).failure.exception
    )

  @Test def testKoFails(): Unit =
    val (gameId, black, white) = startedGame(5)
    playPositions(gameId, black, white, GamePositions.BeforeKo)
    Assertions.assertInstanceOf(
      classOf[Ko],
      service.set(gameId, black, 2, 2, 3).failure.exception
    )

  /** Plays the positions in order, black first, alternating. */
  private def playPositions(
      gameId: String,
      black: String,
      white: String,
      positions: List[GamePositions.Position]
  ): Unit =
    for (token, (x, y, z)) <- GamePositions.withTokens(black, white, positions) do
      service.set(gameId, token, x, y, z).success.value

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
      classOf[NonexistentGame],
      service.statusAt("NOGAME", 1).failure.exception
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

  /** A game that ended while the archive was down: it has no archive key. Returns its id. */
  private def gameWithFailedArchive(failingService: GameService): String =
    val gameId = failingService.newGame(3).success.value.id
    val black = failingService.register(gameId, Black).success.value.authToken
    val white = failingService.register(gameId, White).success.value.authToken
    failingService.pass(gameId, black).success.value
    failingService.pass(gameId, white).success.value
    gameId

  private def failingArchiveService: GameService = serviceFor(store, InMemoryGameArchive(true))

  @Test def testRecoveryArchivesFinishedGamesWithoutArchive(): Unit =
    val ids = List.fill(2)(gameWithFailedArchive(failingArchiveService))
    val result = service.recoverArchives().success.value
    Assertions.assertEquals(ids.toSet, result.archived.toSet)
    Assertions.assertEquals(List.empty[String], result.failed)
    for id <- ids do
      Assertions.assertTrue(archive.archived(id).exists(_.game.isOver))
      Assertions.assertEquals(
        Some(("archives/" + id + ".json", NowMillis / 1000 + GameService.RetentionSeconds)),
        store.completion(id)
      )

  @Test def testRecoveryOfArchivedAndRunningGamesDoesNothing(): Unit =
    finishedGame()
    val (running, black, _) = startedGame(3)
    service.pass(running, black).success.value
    Assertions.assertEquals(
      ArchiveRecovery(List(), List()),
      service.recoverArchives().success.value
    )
    Assertions.assertEquals(None, store.completion(running))

  @Test def testSecondRecoveryHasNothingToDo(): Unit =
    val id = gameWithFailedArchive(failingArchiveService)
    Assertions.assertEquals(List(id), service.recoverArchives().success.value.archived)
    val completion = store.completion(id)
    Assertions.assertEquals(
      ArchiveRecovery(List(), List()),
      service.recoverArchives().success.value
    )
    Assertions.assertEquals(completion, store.completion(id))

  @Test def testRecoveryReportsFailedGamesAndContinues(): Unit =
    val ids = List.fill(2)(gameWithFailedArchive(failingArchiveService))
    val result = failingArchiveService.recoverArchives().success.value
    Assertions.assertEquals(List.empty[String], result.archived)
    Assertions.assertEquals(ids.toSet, result.failed.toSet)
    Assertions.assertTrue(ids.forall(store.completion(_).isEmpty))

  @Test def testRecoveryContinuesAfterOneGameFails(): Unit =
    val ids = List.fill(2)(gameWithFailedArchive(failingArchiveService))
    val flaky = serviceFor(store, FailingForGameArchive(archive, ids.head))
    val result = flaky.recoverArchives().success.value
    Assertions.assertEquals(List(ids.head), result.failed)
    Assertions.assertEquals(ids.drop(1), result.archived)
    Assertions.assertEquals(None, store.completion(ids.head))
    Assertions.assertTrue(store.completion(ids(1)).isDefined)

  @Test def testRecoveryFailsWhenStoreCannotBeScanned(): Unit =
    Assertions.assertTrue(serviceFor(FailingGameStore(), archive).recoverArchives().isFailure)

  @Test def testArchiveFailureMessageStartsWithAlarmMarker(): Unit =
    Assertions.assertEquals(
      s"${GameService.ArchiveFailedMarker} game G1",
      GameService.archiveFailureMessage("G1")
    )

  @Test def testMetricFilterOfArchiveAlarmMatchesMarker(): Unit =
    val terraform = Files.readString(Path.of("infra/terraform/go3d-readonly/main.tf"))
    val pattern = s"\\\"${GameService.ArchiveFailedMarker}\\\""
    Assertions.assertTrue(terraform.contains(pattern), "metric filter pattern differs from marker")

  @Test def testRetentionPeriods(): Unit =
    Assertions.assertEquals(7L * 24 * 3600, GameService.RetentionSeconds)
    Assertions.assertEquals(30L * 24 * 3600, DynamoDBGameStore.InactiveRetentionSeconds)

  @Test def testMoveAfterGameOverFails(): Unit =
    val (gameId, black, _) = finishedGame()
    Assertions.assertInstanceOf(
      classOf[GameOver],
      service.set(gameId, black, 1, 1, 1).failure.exception
    )

  @Test def testConcurrentMoveFails(): Unit =
    val (gameId, black, _) = startedGame(3)
    val stored = store.getGame(gameId).success.value
    // another request stores a move between this request's read and its write
    val racingService = serviceFor(
      InterleavingStore(
        store,
        () =>
          stored.foreach(s => store.updateGame(gameId, s.version, s.game).success.value)
      ),
      archive
    )
    Assertions.assertInstanceOf(
      classOf[ConcurrentModification],
      racingService.set(gameId, black, 1, 1, 1).failure.exception
    )
    Assertions.assertEquals(
      go3d.Empty,
      service.status(gameId, None).success.value.game.at(1, 1, 1)
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
