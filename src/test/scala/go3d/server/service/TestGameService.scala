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
import go3d.server.OpenGame
import go3d.server.StatusResponse
import io.circe.DecodingFailure
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.util.{Failure, Success, Try}

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

  private def assertOverAndWinner(
      status: StatusResponse,
      over: Boolean,
      winner: Option[Color]
  ): Unit =
    Assertions.assertEquals(over, status.over)
    Assertions.assertEquals(winner, status.winner)
    Assertions.assertEquals(winner, status.game.winner)

  /** Plays the capture-free `positions` on a board of size 3 and ends the game with two passes,
    * and checks that only the end of the game names the winner, also in a replay, whose count is
    * clamped to the moves played.
    */
  private def assertWinnerOnlyWhenOver(
      komi: Double,
      positions: List[GamePositions.Position],
      winner: Color
  ): Unit =
    val (gameId, black, white) = startedGame(3, komi)
    val sets = GamePositions.withTokens(black, white, positions)
      .map { case (token, (x, y, z)) => service.set(gameId, token, x, y, z).success.value }
    val passes = GamePositions.passingTokens(black, white, positions)
      .map(token => service.pass(gameId, token).success.value)
    val end = positions.length + passes.length
    (sets ++ passes.init).foreach(assertOverAndWinner(_, false, None))
    assertOverAndWinner(passes.last, true, Some(winner))
    assertOverAndWinner(service.status(gameId, None).success.value, true, Some(winner))
    assertOverAndWinner(service.status(gameId, Some(white)).success.value, true, Some(winner))
    for count <- List(end, end + 1) do
      assertOverAndWinner(service.statusAt(gameId, count).success.value, true, Some(winner))
    for count <- List(-1, 0, end - 1) do
      assertOverAndWinner(service.statusAt(gameId, count).success.value, false, None)

  @Test def testWinnerWithDefaultKomiIsBlack(): Unit =
    assertWinnerOnlyWhenOver(Komi.Default, GamePositions.BlackLeadsByOne, Black)

  @Test def testWinnerWithLargeKomiIsWhite(): Unit =
    assertWinnerOnlyWhenOver(6.5, GamePositions.BlackLeadsByOne, White)

  @Test def testWinnerOfEmptyBoardWithNegativeKomiIsBlack(): Unit =
    assertWinnerOnlyWhenOver(-0.5, GamePositions.EmptyBoard, Black)

  @Test def testWinnerOfEmptyBoardWithDefaultKomiIsWhite(): Unit =
    assertWinnerOnlyWhenOver(Komi.Default, GamePositions.EmptyBoard, White)

  @Test def testStatusOfGameInProgressHasNoWinner(): Unit =
    val (gameId, black, _) = startedGame(3, 6.5)
    service.set(gameId, black, 2, 2, 2).success.value
    assertOverAndWinner(service.status(gameId, None).success.value, false, None)
    assertOverAndWinner(service.status(gameId, Some(black)).success.value, false, None)

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

  private def openGames(): List[OpenGame] = service.openGames().success.value

  /** A game of size 3 with komi `komi` and only Black registered; returns its id. */
  private def openGame(komi: Double): String =
    val gameId = service.newGame(3, komi).success.value.id
    service.register(gameId, Black).success.value
    gameId

  @Test def testOpenGamesListsGamesWaitingForWhite(): Unit =
    val gameId = service.newGame(3).success.value.id
    service.register(gameId, Black).success.value
    Assertions.assertTrue(openGames().map(_.id).contains(gameId))
    service.register(gameId, White).success.value
    Assertions.assertFalse(openGames().map(_.id).contains(gameId))

  @Test def testOpenGamesCarryTheKomiOfEachGame(): Unit =
    val withoutKomi = service.newGame(3).success.value.id
    service.register(withoutKomi, Black).success.value
    val large = openGame(6.5)
    val negative = openGame(-0.5)
    Assertions.assertEquals(
      Set(OpenGame(withoutKomi, 0.5), OpenGame(large, 6.5), OpenGame(negative, -0.5)),
      openGames().toSet
    )
    Assertions.assertEquals(3, openGames().length)

  @Test def testOpenGamesKeepTheOrderOfTheStore(): Unit =
    for komi <- List(0.5, 6.5, -0.5, 1.5, 2.5) do openGame(komi)
    Assertions.assertEquals(
      store.openGames().success.value.toList,
      openGames().map(_.id)
    )

  @Test def testOpenGamesLeaveOutGamesWithBothOrNoPlayers(): Unit =
    val open = openGame(6.5)
    val full = openGame(6.5)
    service.register(full, White).success.value
    service.newGame(3, 6.5).success.value
    Assertions.assertEquals(List(OpenGame(open, 6.5)), openGames())

  @Test def testNoOpenGamesAreAnEmptyList(): Unit =
    service.newGame(3, 6.5).success.value
    Assertions.assertEquals(List(), openGames())

  @Test def testOpenGamesLeaveOutAGameThatIsGoneBeforeTheRead(): Unit =
    val gone = openGame(6.5)
    val kept = openGame(1.5)
    store.removeGame(gone)
    Assertions.assertTrue(store.openGames().success.value.contains(gone))
    Assertions.assertEquals(List(OpenGame(kept, 1.5)), openGames())

  /** The service on `gameStore` after adding a game of komi 6.5 with only Black registered for
    * each of `gameIds`.
    */
  private def withOpenGames(gameStore: InMemoryGameStore, gameIds: List[String]): GameService =
    for gameId <- gameIds do
      gameStore.addOpenGame(gameId, Game.start(3, 6.5).success.value).success.value
    serviceFor(gameStore, archive)

  private val GameIdsAroundBad = List("GOOD01", "BAD001", "GOOD02")

  @Test def testOpenGamesFailIfAnOpenGameCannotBeRead(): Unit =
    val unreadable = FailingReadStore("BAD001", IllegalStateException("store unavailable"))
    val unreadableService = withOpenGames(unreadable, GameIdsAroundBad)
    Assertions.assertEquals(3, unreadable.openGames().success.value.length)
    Assertions.assertInstanceOf(
      classOf[IllegalStateException],
      unreadableService.openGames().failure.exception
    )

  @Test def testOpenGamesLeaveOutAndLogAGameThatDoesNotDecode(): Unit =
    val undecodable = FailingReadStore("BAD001", DecodingFailure("bad komi", List()))
    val undecodableService = withOpenGames(undecodable, GameIdsAroundBad)
    val (listed, errors) = LogCapture.errorsDuring(classOf[GameService])(
      undecodableService.openGames()
    )
    val readable = undecodable.openGames().success.value.toList.filter(_ != "BAD001")
    Assertions.assertEquals(2, readable.length)
    Assertions.assertEquals(readable.map(OpenGame(_, 6.5)), listed.success.value)
    Assertions.assertTrue(errors.exists(_.contains("BAD001")), errors.toString)

  @Test def testOpenGamesLeaveOutAGameWhoseWhiteRegisteredAfterTheListing(): Unit =
    val staleListing = new InMemoryGameStore:
      // the players Scan listed JOINED before its White registered
      override def openGames(): Try[Array[String]] = Success(Array("JOINED", "OPEN01"))
    val staleService = withOpenGames(staleListing, List("JOINED", "OPEN01"))
    staleListing.registerPlayer("JOINED", White, "whiteJOINED").success.value
    Assertions.assertEquals(List(OpenGame("OPEN01", 6.5)), staleService.openGames().success.value)

  @Test def testOpenGamesFailIfTheStoreCannotListThem(): Unit =
    Assertions.assertInstanceOf(
      classOf[IllegalStateException],
      serviceFor(FailingGameStore(), archive).openGames().failure.exception
    )

class TestUnconfiguredArchive:
  @Test def testArchiveFails(): Unit =
    val saveGame = go3d.server.SaveGame(Game.start(3).success.value, Map())
    Assertions.assertTrue(UnconfiguredArchive.archive("G1", saveGame).isFailure)

  @Test def testUnconfiguredArchiveHasNoUrl(): Unit =
    Assertions.assertEquals(None, UnconfiguredArchive.url("G1").success.value)
