package go3d.server

import io.circe.parser._
import org.junit.jupiter.api.{AfterAll, Assertions, BeforeAll, BeforeEach, Test}
import requests.RequestFailedException
import org.http4s.Status

import java.io.IOException
import java.nio.file.Files
import scala.io.Source
import scala.util.Random

import go3d._
import go3d.server.http4s.GoHttpService

val TestPort = 64555

object TestServer:

  import ch.qos.logback.classic.{Level, Logger}
  import org.slf4j.LoggerFactory

  @BeforeAll def quietLogging(): Unit =
    val root = org.slf4j.Logger.ROOT_LOGGER_NAME
    LoggerFactory.getLogger(root).asInstanceOf[Logger].setLevel(Level.WARN)

  var shutdown: Option[cats.effect.IO[Unit]] = None
  @BeforeAll def startHttp4s(): Unit =
    import cats.effect.unsafe.implicits.global
    import scala.util.Try
    val server = GoHttpService(TestPort).server
    shutdown = Try(server.allocated.unsafeRunSync()._2).toOption

  @AfterAll def stopHttp4s(): Unit =
    import cats.effect.unsafe.implicits.global
    shutdown.foreach(_.unsafeRunSync())

class TestServer:

  var tempDir: Option[String] = None

  @BeforeEach def setupTempDir(): Unit =
    tempDir = Some(Files.createTempDirectory("go3d").toString)
    Games.init(tempDir.get)

  @Test def testNewGame(): Unit =
    val response = GameData.create(TestSize)
    Assertions.assertNotNull(response.id)
    Assertions.assertNotEquals("", response.id)
    Assertions.assertEquals(TestSize, response.size)

  @Test def testNewGameFailsWithBadSize(): Unit =
    Assertions.assertThrows(
      classOf[IOException], () => getJson(s"http://localhost:$TestPort/new/1")
    )
    Assertions.assertThrows(
      classOf[IOException], () => getJson(s"http://localhost:$TestPort/new/4")
    )
    Assertions.assertThrows(
      classOf[IOException], () => getJson(s"http://localhost:$TestPort/new/27")
    )

  @Test def testNewGameWithBadSizeSetsStatus400(): Unit =
    assertFailsWithStatus(
      s"http://localhost:$TestPort/new/27", Status.BadRequest.code
    )

  @Test def testRegisterOnePlayer(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, Black)
    Assertions.assertEquals(TestSize, registerResponse.game.size)
    Assertions.assertEquals(Black, registerResponse.color)

  @Test def testRegisterBadColorSetsStatus404(): Unit =
    val newGameResponse = GameData.create(TestSize)
    assertFailsWithStatus(
      s"${GameData.ServerURL}/register/${newGameResponse.id}/X", Status.NotFound.code
    )

  @Test def testRegisterTwoPlayers(): Unit =
    val newGameResponse = GameData.create(TestSize)
    GameData.register(newGameResponse.id, Black)
    val registerResponse = GameData.register(newGameResponse.id, White)
    Assertions.assertEquals(TestSize, registerResponse.game.size)
    Assertions.assertEquals(White, registerResponse.color)

  @Test def testRegisterTwoPlayersBlackLastSetsReady(): Unit =
    val newGameResponse = GameData.create(TestSize)
    GameData.register(newGameResponse.id, White)
    val registerResponse = GameData.register(newGameResponse.id, Black)
    Assertions.assertTrue(registerResponse.ready)

  @Test def testRegisterTwoPlayersWhiteLastDoesNotSetReady(): Unit =
    val newGameResponse = GameData.create(TestSize)
    GameData.register(newGameResponse.id, Black)
    val registerResponse = GameData.register(newGameResponse.id, White)
    Assertions.assertFalse(registerResponse.ready)

  @Test def testRegisterOnlyBlackDoesNotSetReady(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, Black)
    Assertions.assertFalse(registerResponse.ready)

  @Test def testRegisterOnlyWhiteDoesNotSetReady(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, White)
    Assertions.assertFalse(registerResponse.ready)

  @Test def testRegisterOnlyBlackDoesNotReturnReadyStatus(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, Black)
    val statusResponse = getSR(
      s"${GameData.ServerURL}/status/${newGameResponse.id}",
      Map("Authentication" -> s"Bearer ${registerResponse.authToken}")
    )
    Assertions.assertFalse(statusResponse.ready)

  @Test def testRegisterOnlyWhiteDoesNotReturnReadyStatus(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, White)
    val statusResponse = getSR(
      s"${GameData.ServerURL}/status/${newGameResponse.id}",
      Map("Authentication" -> s"Bearer ${registerResponse.authToken}")
    )
    Assertions.assertFalse(statusResponse.ready)

  @Test def testRegisterSamePlayerTwiceFails(): Unit =
    val newGameResponse = GameData.create(TestSize)
    GameData.register(newGameResponse.id, Black)
    Assertions.assertThrows(
      classOf[IOException], () => GameData.register(newGameResponse.id, Black)
    )

  @Test def testRegisterAtNonexistentGameFails(): Unit =
    val newGameResponse = GameData.create(TestSize)
    Assertions.assertThrows(
      classOf[IOException], () => GameData.register(newGameResponse.id + "NOPE!", Black)
    )

  @Test def testGetStatusAfterBothRegisteredForBlackIsReady(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    Assertions.assertTrue(statusResponse.ready)

  @Test def testGetStatusAfterBothRegisteredForWhiteIsNotReady(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(White)
    Assertions.assertFalse(statusResponse.ready, statusResponse.toString)

  @Test def testGetStatusAfterBothRegisteredForBlackHasPossibleMoves(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    Assertions.assertTrue(statusResponse.moves.nonEmpty)

  @Test def testGetStatusAfterBothRegisteredForWhiteHasNoPossibleMoves(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(White)
    Assertions.assertTrue(statusResponse.moves.isEmpty, statusResponse.moves.toString)

  @Test def testSetStoneWithoutAuthFails(): Unit =
    val gameData = setUpGame(TestSize)
    Assertions.assertThrows(
      classOf[RequestFailedException],
      () => getSR(s"http://localhost:$TestPort/set/${gameData.id}/1/1/1", Map())
    )

  @Test def testSetStoneWithoutAuthSetsStatus401(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/set/${gameData.id}/1/1/1", 401)

  @Test def testPassWithoutAuthSetsStatus401(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/pass/${gameData.id}", 401)

  @Test def testSetStoneForBlackAtReadyStatusSucceeds(): Unit = gameWithBlackAt111(TestSize)

  @Test def testSetStoneForBlackAtReadyStatusReturnsUpdatedBoard(): Unit =
    val setResponse = gameWithBlackAt111(TestSize)
    Assertions.assertEquals(Black, setResponse.game.at(1, 1, 1))

  @Test def testSetStoneForBlackAtReadyStatusReturnsStatusNotReady(): Unit =
    val setResponse = gameWithBlackAt111(TestSize)
    Assertions.assertFalse(setResponse.ready)

  @Test def testSetStoneForBlackAtReadyStatusReturnsNoPossibleMoves(): Unit =
    val setResponse = gameWithBlackAt111(TestSize)
    Assertions.assertTrue(setResponse.moves.isEmpty)

  @Test def testSetStoneWhenNotReadyFails(): Unit =
    val gameData = setUpGame(TestSize)
    Assertions.assertFalse(gameData.status(White).ready)
    Assertions.assertThrows(
      classOf[RequestFailedException], () => gameData.set(White, 1, 1, 1)
    )

  @Test def testSetStoneWhenNotReadySetsStatus400(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/set/${gameData.id}/1/1/1",
      Status.BadRequest.code,
      Map("Authentication" -> s"Bearer ${gameData.token(White)}")
    )

  @Test def testPassWhenNotReadyFails(): Unit =
    val gameData = setUpGame(TestSize)
    Assertions.assertFalse(gameData.status(White).ready)
    Assertions.assertThrows(classOf[RequestFailedException], () => gameData.pass(White))

  @Test def testPassWhenNotReadySetsStatus400(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/pass/${gameData.id}",
      Status.BadRequest.code,
      Map("Authentication" -> s"Bearer ${gameData.token(White)}")
    )

  @Test def testGetStatusForBlackAfterBlackSetStoneReturnsStatusNotReady(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(Black)
    Assertions.assertFalse(statusResponseAgain.ready)

  @Test def testGetStatusForBlackAfterBlackSetStoneReturnsStatusNoPossibleMoves(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(Black)
    Assertions.assertTrue(statusResponseAgain.moves.isEmpty)

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsReady(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(White)
    Assertions.assertTrue(statusResponseAgain.ready)

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsUpdatedBoard(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(White)
    Assertions.assertEquals(Black, statusResponseAgain.game.at(pos))

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsPossibleMoves(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(White)
    Assertions.assertTrue(statusResponseAgain.moves.nonEmpty)

  @Test def testGetStatusWithoutAuthReturnsBoardButNothingElse(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status()
    Assertions.assertFalse(statusResponse.ready)
    val setResponse = gameData.set(Black, 1, 1, 1)
    val statusResponseAgain = gameData.status()
    Assertions.assertFalse(statusResponseAgain.ready)
    Assertions.assertEquals(0, statusResponseAgain.moves.length)
    Assertions.assertEquals(Black, statusResponseAgain.game.at(1, 1, 1))

  @Test def testPassReturnsNonReadyStatus(): Unit =
    val gameData = setUpGame(TestSize)
    val passResponse = gameData.pass(Black)
    Assertions.assertFalse(passResponse.ready)
    Assertions.assertTrue(passResponse.moves.isEmpty)

  @Test def testPassTwiceReturnsGameOver(): Unit =
    val gameData = setUpGame(TestSize)
    val pass1Response = gameData.pass(Black)
    val pass2Response = gameData.pass(White)
    Assertions.assertTrue(pass2Response.game.isOver)

  @Test def testPlayListOfMoves(): Unit =
    val gameData = setUpGame(TestSize)
    playListOfMoves(gameData, CaptureMoves.dropRight(1))
    val game = gameData.status().game
    for move <- CaptureMoves.dropRight(1) do
      Assertions.assertEquals(move.color, game.at(move.position), move.toString+"\n"+game.toString)

  @Test def testCaptureStone(): Unit =
    val gameData = setUpGame(TestSize)
    playListOfMoves(gameData, CaptureMoves)
    val game = gameData.status().game
    Assertions.assertEquals(Empty, game.at(Position(2, 2, 1)), "\n"+game.toString)

  @Test def testCaptureTwoDisjointStonesWithOneMove(): Unit =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Black), Move(1, 1, 1, White),
      Move(4, 1, 1, Black), Move(5, 1, 1, White),
      Pass(Black), Move(2, 2, 1, White),
      Pass(Black), Move(4, 2, 1, White),
      Pass(Black), Move(2, 1, 2, White),
      Pass(Black), Move(4, 1, 2, White),
      Pass(Black)
    )
    val gameData = setUpGame(5)
    val statusResponse = playListOfMoves(gameData, moves)
    Assertions.assertTrue(statusResponse.game.captures.isEmpty)
    val statusResponseAgain = playListOfMoves(gameData,List(Move(3, 1, 1, White)))
    Assertions.assertEquals(2, statusResponseAgain.game.captures(White))

  @Test def testPlayRandomGame(): Unit =
    val gameData = setUpGame(TestSize)
    gameData.playRandomGame(true)

  @Test def testPlayRandomGameIsSaved(): Unit =
    val gameData = setUpGame(TestSize)
    gameData.playRandomGame(false)
    Assertions.assertTrue(
      IOForTests.exists(gameData.id + ".json"), s"${gameData.id} in ${IOForTests.files}?"
    )

  @Test def testSavedRandomGameIsSameAsPlayed(): Unit =
    val gameData = setUpGame(TestSize)
    gameData.playRandomGame(false)
    val savedGame = Games.readGame(IOForTests.open(gameData.id + ".json"))
    Assertions.assertEquals(Games(gameData.id), savedGame.game)

  @Test def testTooLongURLSetsStatus400WhenCreatingGame(): Unit =
    assertFailsWithStatus(s"http://localhost:$TestPort/new/123", 400)

  @Test def testTooLongURLSetsStatus414WhenRegisteringPlayer(): Unit =
    val newGameResponse = GameData.create(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/register/${newGameResponse.id}/@/XX", 404)

  @Test def testTooLongURLSetsStatus414WhenRegisteringPlayerEvenIfGameIdWrong(): Unit =
    assertFailsWithStatus(s"http://localhost:$TestPort/register/NOTID!/@/XX", 404)

  @Test def testTooLongURLSetsStatus414WhenSetting(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/set/${gameData.id}/12/12/12/X", 404,
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )

  @Test def testTooLongURLSetsStatus414WhenSettingEvenIfNotReady(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/set/${gameData.id}/12/12/12/X", 404,
      Map("Authentication" -> s"Bearer ${gameData.token(White)}")
    )

  @Test def testTooLongURLSetsStatus414WhenPassing(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/pass/${gameData.id}/X", 404,
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )

  @Test def testTooLongURLSetsStatus414WhenFetchingStatus(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/status/${gameData.id}/X", 404,
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )

  @Test def testTooLongURLSetsStatus414WhenFetchingStatusIfUnauthenticated(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/status/${gameData.id}/X", 404)

  @Test def testNonexistentGameSetsStatus404WhenRegisteringPlayer(): Unit =
    val newGameResponse = GameData.create(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/register/NOPE!!/@", 404)

  @Test def testNonexistentGameSetsStatus404WhenSetting(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/set/NOPE!!/1/1/1", 404,
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )

  @Test def testNonexistentGameSetsStatus404WhenPassing(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/pass/NOPE!!", 404,
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )

  @Test def testNonexistentGameSetsStatus404WhenFetchingStatus(): Unit =
    assertFailsWithStatus(s"http://localhost:$TestPort/status/NOPE!!", 404)

  @Test def testHealth(): Unit =
    val response = requests.get(s"http://localhost:$TestPort/health")
    Assertions.assertEquals("1", response.text())

  @Test def testHealthFailsWithExtraData(): Unit =
    assertFailsWithStatus(s"http://localhost:$TestPort/health/xyz", 404)

  @Test def testRegisterWithoutDebugDoesNotPassDebugInfo(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = getPRR(s"${GameData.ServerURL}/register/${newGameResponse.id}/@")
    Assertions.assertFalse(registerResponse.debug.headers.nonEmpty)

  @Test def testSetStoneWithDebug(): Unit =
    val gameData = setUpGame(TestSize)
    val setResponse = getSR(
      s"${GameData.ServerURL}/set/${gameData.id}/1/1/1/d",
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )
    Assertions.assertTrue(setResponse.debug.headers.nonEmpty)

  @Test def testSetStoneWithoutDebugDoesNotPassDebugInfo(): Unit =
    val gameData = setUpGame(TestSize)
    val setResponse = getSR(
      s"${GameData.ServerURL}/set/${gameData.id}/1/1/1",
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )
    Assertions.assertFalse(setResponse.debug.headers.nonEmpty)

  @Test def testSetStoneWithDebugOnBigField(): Unit =
    val gameData = setUpGame(11)
    val setResponse = getSR(
      s"${GameData.ServerURL}/set/${gameData.id}/11/11/11/d",
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )
    Assertions.assertTrue(setResponse.debug.headers.nonEmpty)

  @Test def testPassWithDebug(): Unit =
    val gameData = setUpGame(TestSize)
    val passResponse = getSR(
      s"${GameData.ServerURL}/pass/${gameData.id}/d",
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )
    Assertions.assertTrue(passResponse.debug.headers.nonEmpty)

  @Test def testPassWithoutDebugDoesNotPassDebugInfo(): Unit =
    val gameData = setUpGame(TestSize)
    val passResponse = getSR(
      s"${GameData.ServerURL}/pass/${gameData.id}",
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )
    Assertions.assertFalse(passResponse.debug.headers.nonEmpty)

  @Test def testGetStatusWithDebug(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = getSR(
      s"${GameData.ServerURL}/status/${gameData.id}/d",
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )
    Assertions.assertTrue(statusResponse.debug.headers.nonEmpty)

  @Test def testGetStatusWithoutDebugDoesNotPassDebugInfo(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = getSR(
      s"${GameData.ServerURL}/status/${gameData.id}",
      Map("Authentication" -> s"Bearer ${gameData.token(Black)}")
    )
    Assertions.assertFalse(statusResponse.debug.headers.nonEmpty)

  @Test def testGetStatusWithDebugWithoutAuthDoesNotPassDebugInfo(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = getSR(s"${GameData.ServerURL}/status/${gameData.id}/d", Map())
    Assertions.assertFalse(statusResponse.debug.headers.nonEmpty, statusResponse.toString)

  @Test def testGetOpenGamesReturnsResponse(): Unit =
    val response = getOGR(s"${GameData.ServerURL}/openGames")
    Assertions.assertTrue(response.isInstanceOf[OpenGamesResponse])

  @Test def testGetOpenGamesReturns404IfRouteHasTrailingSlash(): Unit =
    Assertions.assertThrows(
      classOf[java.io.FileNotFoundException],
      () => getOGR(s"${GameData.ServerURL}/openGames/")
    )

  @Test def testGetOpenGamesDoesNotReturnGameWithNoPlayer(): Unit =
    val newGameResponse = GameData.create(3)
    val response = getOGR(s"${GameData.ServerURL}/openGames")
    Assertions.assertFalse(response.ids.isEmpty)
    Assertions.assertFalse(response.ids.contains(newGameResponse.id))

  @Test def testGetOpenGamesReturnsOneRegisteredGame(): Unit =
    val newGameResponse = GameData.create(3)
    val blackRegistered = GameData.register(newGameResponse.id, Black)
    val response = getOGR(s"${GameData.ServerURL}/openGames")
    Assertions.assertFalse(response.ids.isEmpty)
    Assertions.assertTrue(response.ids.contains(newGameResponse.id))

  @Test def testGetOpenGamesDoesNotReturnGameWithNoBlackPlayer(): Unit =
    val newGameResponse = GameData.create(3)
    GameData.register(newGameResponse.id, White)
    val response = getOGR(s"${GameData.ServerURL}/openGames")
    Assertions.assertFalse(response.ids.isEmpty)
    Assertions.assertFalse(response.ids.contains(newGameResponse.id))

  @Test def testGetOpenGamesDoesNotReturnGameWithTwoPlayers(): Unit =
    val newGameResponse = GameData.create(3)
    GameData.register(newGameResponse.id, Black)
    GameData.register(newGameResponse.id, White)
    val response = getOGR(s"${GameData.ServerURL}/openGames")
    Assertions.assertFalse(response.ids.isEmpty)
    Assertions.assertFalse(response.ids.contains(newGameResponse.id))

  @Test def testDoublePassReturnsGameOver(): Unit =
    val gameData: GameData = setUpGame(3)
    gameData.pass(Black)
    val statusResponse = gameData.pass(White)
    Assertions.assertTrue(statusResponse.over, statusResponse.toString)

  @Test def testSubsequentStatusRequestAfterDoublePassReturnsGameOver(): Unit =
    val gameData: GameData = setUpGame(3)
    gameData.pass(Black)
    gameData.pass(White)
    val statusResponse = gameData.status()
    Assertions.assertTrue(statusResponse.over, statusResponse.toString)

  @Test def testGameOverAfterFullNumberOfMoves(): Unit =
    val gameData: GameData = setUpGame(3)
    var color = Black
    for (x <- 1 to 3; y <- 1 to 3; z <- 1 to 3)
      try gameData.set(Move(Position(x, y, z), color))
      catch case _: RequestFailedException => gameData.pass(color)
      color = !color

    val statusResponse = gameData.status()
    Assertions.assertTrue(statusResponse.over, statusResponse.toString)

  @Test def testSettingTheSameColorTwiceGivesError(): Unit =
    val gameData: GameData = setUpGame(3)
    gameData.set(Move(Position(1, 1, 1), Black))
    Assertions.assertThrows(
      classOf[RequestFailedException], () => gameData.set(Move(Position(1, 1, 1), Black))
    )

  @Test def testSettingTheSameColorTwiceErrorMessageContainsWhy(): Unit =
    val gameData: GameData = setUpGame(3)
    gameData.set(Move(Position(1, 1, 1), Black))
    try
      gameData.set(Move(Position(1, 1, 2), Black))
    catch
      case e: RequestFailedException =>
        Assertions.assertEquals(Status.BadRequest.code, e.response.statusCode)
        Assertions.assertTrue(e.response.text().contains(classOf[NotReadyToSet].getSimpleName), e.response.text())

  @Test def testSettingToSuicideErrorMessageContainsWhy(): Unit =
    val gameData: GameData = setUpGame(3)
    playListOfMoves(
      gameData, List(
        Move(Position(1, 1, 2), Black), Pass(White),
        Move(Position(1, 2, 1), Black), Pass(White),
        Move(Position(2, 1, 1), Black)
      )
    )
    try
      gameData.set(Move(Position(1, 1, 1), White))
    catch
      case e: RequestFailedException =>
        Assertions.assertEquals(Status.BadRequest.code, e.response.statusCode)
        Assertions.assertTrue(e.response.text().contains(classOf[Suicide].getSimpleName), e.response.text())

  @Test def testSettingOutsideBoardErrorMessageContainsWhy(): Unit =
    val gameData: GameData = setUpGame(3)
    try
      gameData.set(Move(Position(1, 1, 4), Black))
    catch
      case e: RequestFailedException =>
        Assertions.assertEquals(Status.BadRequest.code, e.response.statusCode)
        Assertions.assertTrue(e.response.text().contains(classOf[OutsideBoard].getSimpleName))

  @Test def testSettingAfterGameOverErrorMessageContainsWhy(): Unit =
    val gameData: GameData = setUpGame(3)
    gameData.pass(Black)
    gameData.pass(White)
    try
      gameData.set(Move(Position(1, 1, 1), Black))
    catch
      case e: RequestFailedException =>
        Assertions.assertEquals(Status.Gone.code, e.response.statusCode)
        Assertions.assertTrue(e.response.text().contains(classOf[GameOver].getSimpleName))

  @Test def testAddedGameIsNotWrittenBeforeFirstMove(): Unit =
    val gameData: GameData = setUpGame(3)
    Assertions.assertFalse(Games.fileIO.get.getActiveGames.contains(gameData.id))

  @Test def testAddedGameIsWrittenAfterFirstMove(): Unit =
    val gameData: GameData = setUpGame(3)
    gameData.set(Move(Position(1, 1, 1), Black))
    Assertions.assertTrue(Games.fileIO.get.getActiveGames.contains(gameData.id))

  @Test def testFinishedGameIsNoLongerActive(): Unit =
    val gameData: GameData = setUpGame(3)
    val previousNumberOfActiveGames = Games.numActiveGames
    gameData.pass(Black)
    gameData.pass(White)
    Assertions.assertEquals(previousNumberOfActiveGames - 1, Games.numActiveGames)

  @Test def testFinishedGameIsListedAsArchived(): Unit =
    val gameData: GameData = setUpGame(3)
    val previousNumberOfArchivedGames = Games.numArchivedGames
    gameData.pass(Black)
    gameData.pass(White)
    Assertions.assertEquals(previousNumberOfArchivedGames + 1, Games.numArchivedGames)

  @Test def testFinishedGameIsMovedToArchiveFolder(): Unit =
    val gameData: GameData = setUpGame(3)
    gameData.pass(Black)
    gameData.pass(White)
    Assertions.assertTrue(Games.fileIO.get.getArchivedGames.contains(gameData.id), s"${Games.fileIO.get.getArchivedGames} does not contain ${gameData.id}")
    Assertions.assertFalse(Games.fileIO.get.getActiveGames.contains(gameData.id))

def playListOfMoves(gameData: GameData, moves: Iterable[Move | Pass]): StatusResponse =
    var statusResponse: StatusResponse = null
    for move <- moves do
      statusResponse = move match
        case m: Move => gameData.set(m)
        case p: Pass => gameData.pass(p.color)
    statusResponse

def randomChoice[T](elements: List[T]): T = elements((new Random).nextInt(elements.length))

def getJson(url: String): Source = Source.fromURL(url)

def getPRR(url: String): PlayerRegisteredResponse =
  val json = getJson(url).mkString
  val result = decode[PlayerRegisteredResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  result.getOrElse(null)

def getGCR(url: String): GameCreatedResponse =
  val json = getJson(url).mkString
  val result = decode[GameCreatedResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  result.getOrElse(null)

def getSR(url: String, header: Map[String, String]): StatusResponse =
  val response = requests.get(url, headers = header)
  val json = response.text()
  val result = decode[StatusResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  result.getOrElse(null)

def getOGR(url: String): OpenGamesResponse =
  val json = getJson(url).mkString
  val result = decode[OpenGamesResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  result.getOrElse(null)

def setUpGame(size: Int): GameData =
  val newGameResponse = GameData.create(size)
  val blackRegistered = GameData.register(newGameResponse.id, Black)
  val whiteRegistered = GameData.register(newGameResponse.id, White)
  val tokens = Map(Black -> blackRegistered.authToken, White -> whiteRegistered.authToken)
  GameData(newGameResponse.id, tokens)

def gameWithBlackAt111(size: Int): StatusResponse =
  setUpGame(size).set(Move(Position(1, 1, 1), Black))

def assertFailsWithStatus(url: String, expectedStatus: Int,
                          headers: Map[String, String] = Map()): Unit =
  try
    requests.get(url, headers = headers)
    Assertions.fail("request unexpectedly succeeded")
  catch
    case e: RequestFailedException => Assertions.assertEquals(
      expectedStatus, e.response.statusCode, e.response.text()
    )
    case e: Throwable => Assertions.fail(
      s"expected RequestFailedException, got ${e.getClass.getSimpleName}"
    )
