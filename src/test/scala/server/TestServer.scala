package go3d.testing

import go3d.server._
import go3d.{Black, Color, Empty, Move, Pass, Position, White, newGame}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.http.HttpStatus
import org.junit.{After, Assert, Before, Ignore, Test}

import java.io.IOException
import java.net.HttpURLConnection
import scala.io.Source
import scala.reflect.ClassTag
import io.circe.parser._
import requests._

import java.nio.file.Files
import scala.util.Random

val TestPort = 64555

case class GameData(id: String, token: Map[Color, String]):
  def this(gameId: String, blackToken: String, whiteToken: String) =
    this(gameId, Map(Black -> blackToken, White -> whiteToken))

  def status(color: Color): StatusResponse =
    getSR(s"${GameData.ServerURL}/status/${id}", Map("Authentication" -> s"Basic ${token(color)}"))
  def status(): StatusResponse = getSR(s"${GameData.ServerURL}/status/${id}", Map())

  def set(color: Color, x: Int, y: Int, z: Int): StatusResponse =
    getSR(
      s"${GameData.ServerURL}/set/${id}/${x}/${y}/${z}", Map("Authentication" -> s"Basic ${token(color)}")
    )
  def set(move: Move): StatusResponse = set(move.color, move.x, move.y, move.z)

  def pass(color: Color): StatusResponse =
    getSR(s"${GameData.ServerURL}/pass/${id}", Map("Authentication" -> s"Basic ${token(color)}"))

object GameData:
  val ServerURL = s"http://localhost:$TestPort"
  def create(size: Int): GameCreatedResponse = getGCR(s"$ServerURL/new/$size")
  def register(id: String, color: Color): PlayerRegisteredResponse =
    getPRR(s"$ServerURL/register/$id/$color")

class TestServer:

  var jetty: Server = null

  @Before def quietLogging(): Unit =
    import ch.qos.logback.classic.{Level,Logger}
    import org.slf4j.LoggerFactory
    val root = org.slf4j.Logger.ROOT_LOGGER_NAME
    LoggerFactory.getLogger(root).asInstanceOf[Logger].setLevel(Level.WARN)

  @Before def startJetty(): Unit =
    System.setProperty("org.eclipse.jetty.LEVEL", "OFF")
    jetty = GoServer.createServer(TestPort)
    jetty.start()

  @Before def setupTempDir(): Unit = Io.init(Files.createTempDirectory("go3d").toString)

  @After def stopJetty(): Unit = jetty.stop()

  @Test def testNewGame(): Unit =
    val response = GameData.create(TestSize)
    Assert.assertNotNull(response.id)
    Assert.assertNotEquals("", response.id)
    Assert.assertEquals(TestSize, response.size)

  @Test def testNewGameFailsWithBadSize(): Unit =
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/1")})
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/4")})
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/27")})

  @Test def testNewGameWithBadSizeSetsStatus400(): Unit =
    assertFailsWithStatus(s"http://localhost:$TestPort/new/27", 400)

  @Test def testRegisterOnePlayer(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, Black)
    Assert.assertEquals(TestSize, registerResponse.game.size)
    Assert.assertEquals(Black, registerResponse.color)

  @Test def testRegisterBadColorSetsStatus400(): Unit =
    val newGameResponse = GameData.create(TestSize)
    assertFailsWithStatus(s"${GameData.ServerURL}/register/${newGameResponse.id}/X", 400)

  @Test def testRegisterTwoPlayers(): Unit =
    val newGameResponse = GameData.create(TestSize)
    GameData.register(newGameResponse.id, Black)
    val registerResponse = GameData.register(newGameResponse.id, White)
    Assert.assertEquals(TestSize, registerResponse.game.size)
    Assert.assertEquals(White, registerResponse.color)

  @Test def testRegisterTwoPlayersBlackLastSetsReady(): Unit =
    val newGameResponse = GameData.create(TestSize)
    GameData.register(newGameResponse.id, White)
    val registerResponse = GameData.register(newGameResponse.id, Black)
    Assert.assertTrue(registerResponse.ready)

  @Test def testRegisterTwoPlayersWhiteLastDoesNotSetReady(): Unit =
    val newGameResponse = GameData.create(TestSize)
    GameData.register(newGameResponse.id, Black)
    val registerResponse = GameData.register(newGameResponse.id, White)
    Assert.assertFalse(registerResponse.ready)

  @Test def testRegisterOnlyBlackDoesNotSetReady(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, Black)
    Assert.assertFalse(registerResponse.ready)

  @Test def testRegisterOnlyWhiteDoesNotSetReady(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, White)
    Assert.assertFalse(registerResponse.ready)

  @Test def testRegisterOnlyBlackDoesNotReturnReadyStatus(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, Black)
    val statusResponse = getSR(
      s"${GameData.ServerURL}/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic ${registerResponse.authToken}")
    )
    Assert.assertFalse(statusResponse.ready)

  @Test def testRegisterOnlyWhiteDoesNotReturnReadyStatus(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = GameData.register(newGameResponse.id, White)
    val statusResponse = getSR(
      s"${GameData.ServerURL}/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic ${registerResponse.authToken}")
    )
    Assert.assertFalse(statusResponse.ready)

  @Test def testRegisterSamePlayerTwiceFails(): Unit =
    val newGameResponse = GameData.create(TestSize)
    GameData.register(newGameResponse.id, Black)
    assertThrows[IOException]({GameData.register(newGameResponse.id, Black)})

  @Test def testRegisterAtNonexistentGameFails(): Unit =
    val newGameResponse = GameData.create(TestSize)
    assertThrows[IOException]({GameData.register(newGameResponse.id + "NOPE!", Black)})

  @Test def testGetStatusAfterBothRegisteredForBlackIsReady(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    Assert.assertTrue(statusResponse.ready)

  @Test def testGetStatusAfterBothRegisteredForWhiteIsNotReady(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(White)
    Assert.assertFalse(statusResponse.toString, statusResponse.ready)

  @Test def testGetStatusAfterBothRegisteredForBlackHasPossibleMoves(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    Assert.assertTrue(statusResponse.moves.nonEmpty)

  @Test def testGetStatusAfterBothRegisteredForWhiteHasNoPossibleMoves(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(White)
    Assert.assertTrue(statusResponse.moves.toString, statusResponse.moves.isEmpty)

  @Test def testSetStoneWithoutAuthFails(): Unit =
    val gameData = setUpGame(TestSize)
    assertThrows[RequestFailedException]({getSR(
      s"http://localhost:$TestPort/set/${gameData.id}/1/1/1", Map()
    )})

  @Test def testSetStoneWithoutAuthSetsStatus401(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/set/${gameData.id}/1/1/1", 401)

  @Test def testPassWithoutAuthSetsStatus401(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/pass/${gameData.id}", 401)

  @Test def testSetStoneForBlackAtReadyStatusSucceeds(): Unit = gameWithBlackAt111(TestSize)

  @Test def testSetStoneForBlackAtReadyStatusReturnsUpdatedBoard(): Unit =
    val setResponse = gameWithBlackAt111(TestSize)
    Assert.assertEquals(Black, setResponse.game.at(1, 1, 1))

  @Test def testSetStoneForBlackAtReadyStatusReturnsStatusNotReady(): Unit =
    val setResponse = gameWithBlackAt111(TestSize)
    Assert.assertFalse(setResponse.ready)

  @Test def testSetStoneForBlackAtReadyStatusReturnsNoPossibleMoves(): Unit =
    val setResponse = gameWithBlackAt111(TestSize)
    Assert.assertTrue(setResponse.moves.isEmpty)

  @Test def testSetStoneWhenNotReadyFails(): Unit =
    val gameData = setUpGame(TestSize)
    Assert.assertFalse(gameData.status(White).ready)
    assertThrows[RequestFailedException]({gameData.set(White, 1, 1, 1)})

  @Test def testSetStoneWhenNotReadySetsStatus400(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/set/${gameData.id}/1/1/1", 400,
      Map("Authentication" -> s"Basic ${gameData.token(White)}")
    )

  @Test def testPassWhenNotReadyFails(): Unit =
    val gameData = setUpGame(TestSize)
    Assert.assertFalse(gameData.status(White).ready)
    assertThrows[RequestFailedException]({gameData.pass(White)})

  @Test def testPassWhenNotReadySetsStatus400(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/pass/${gameData.id}", 400,
      Map("Authentication" -> s"Basic ${gameData.token(White)}")
    )

  @Test def testGetStatusForBlackAfterBlackSetStoneReturnsStatusNotReady(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(Black)
    Assert.assertFalse(statusResponseAgain.ready)

  @Test def testGetStatusForBlackAfterBlackSetStoneReturnsStatusNoPossibleMoves(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(Black)
    Assert.assertTrue(statusResponseAgain.moves.isEmpty)

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsReady(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(White)
    Assert.assertTrue(statusResponseAgain.ready)

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsUpdatedBoard(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(White)
    Assert.assertEquals(Black, statusResponseAgain.game.at(pos))

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsPossibleMoves(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status(Black)
    val pos = statusResponse.moves.last
    val setResponse = gameData.set(Move(pos, Black))
    val statusResponseAgain = gameData.status(White)
    Assert.assertTrue(statusResponseAgain.moves.nonEmpty)

  @Test def testGetStatusWithoutAuthReturnsBoardButNothingElse(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = gameData.status()
    Assert.assertFalse(statusResponse.ready)
    val setResponse = gameData.set(Black, 1, 1, 1)
    val statusResponseAgain = gameData.status()
    Assert.assertFalse(statusResponseAgain.ready)
    Assert.assertEquals(0, statusResponseAgain.moves.length)
    Assert.assertEquals(Black, statusResponseAgain.game.at(1, 1, 1))

  @Test def testPassReturnsNonReadyStatus(): Unit =
    val gameData = setUpGame(TestSize)
    val passResponse = gameData.pass(Black)
    Assert.assertFalse(passResponse.ready)
    Assert.assertTrue(passResponse.moves.isEmpty)

  @Test def testPassTwiceReturnsGameOver(): Unit =
    val gameData = setUpGame(TestSize)
    val pass1Response = gameData.pass(Black)
    val pass2Response = gameData.pass(White)
    Assert.assertTrue(pass2Response.game.isOver)

  @Test def testPlayListOfMoves(): Unit =
    val gameData = setUpGame(TestSize)
    playListOfMoves(gameData, CaptureMoves.dropRight(1))
    val game = gameData.status().game
    for move <- CaptureMoves.dropRight(1) do
      Assert.assertEquals(move.toString+"\n"+game.toString, move.color, game.at(move.position))

  @Test def testCaptureStone(): Unit =
    val gameData = setUpGame(TestSize)
    playListOfMoves(gameData, CaptureMoves)
    val game = gameData.status().game
    Assert.assertEquals("\n"+game.toString, Empty, game.at(Position(2, 2, 1)))

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
    Assert.assertTrue(statusResponse.game.captures.isEmpty)
    val statusResponseAgain = playListOfMoves(gameData,List(Move(3, 1, 1, White)))
    Assert.assertEquals(2, statusResponseAgain.game.captures(Black))

  @Test def testPlayRandomGame(): Unit =
    val gameData = setUpGame(TestSize)
    playRandomGame(gameData)

  @Test def testPlayRandomGameIsSaved(): Unit =
    val gameData = setUpGame(TestSize)
    playRandomGame(gameData)
    Assert.assertTrue(Io.exists(gameData.id + ".json"))

  @Test def testSavedRandomGameIsSameAsPlayed(): Unit =
    val gameData = setUpGame(TestSize)
    playRandomGame(gameData)
    val savedGame = readGame(Io.open(gameData.id + ".json"))
    Assert.assertEquals(Games(gameData.id), savedGame.game)

  @Test def testTooLongURLSetsStatus414WhenCreatingGame(): Unit =
    assertFailsWithStatus(s"http://localhost:$TestPort/new/123", 414)

  @Test def testTooLongURLSetsStatus414WhenRegisteringPlayer(): Unit =
    val newGameResponse = GameData.create(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/register/${newGameResponse.id}/@/XX", 414)

  @Test def testTooLongURLSetsStatus414WhenRegisteringPlayerEvenIfGameIdWrong(): Unit =
    assertFailsWithStatus(s"http://localhost:$TestPort/register/NOTID!/@/XX", 414)

  @Test def testTooLongURLSetsStatus414WhenSetting(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/set/${gameData.id}/12/12/12/X", 414,
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )

  @Test def testTooLongURLSetsStatus414WhenSettingEvenIfNotReady(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/set/${gameData.id}/12/12/12/X", 414,
      Map("Authentication" -> s"Basic ${gameData.token(White)}")
    )

  @Test def testTooLongURLSetsStatus414WhenPassing(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/pass/${gameData.id}/X", 414,
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )

  @Test def testTooLongURLSetsStatus414WhenFetchingStatus(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/status/${gameData.id}/X", 414,
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )

  @Test def testTooLongURLSetsStatus414WhenFetchingStatusIfUnauthenticated(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/status/${gameData.id}/X", 414)

  @Test def testNonexistentGameSetsStatus404WhenRegisteringPlayer(): Unit =
    val newGameResponse = GameData.create(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/register/NOPE!!/@", 404)

  @Test def testNonexistentGameSetsStatus404WhenSetting(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/set/NOPE!!/1/1/1", 404,
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )

  @Test def testNonexistentGameSetsStatus404WhenPassing(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(
      s"http://localhost:$TestPort/pass/NOPE!!", 404,
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )

  @Test def testNonexistentGameSetsStatus404WhenFetchingStatus(): Unit =
    val gameData = setUpGame(TestSize)
    assertFailsWithStatus(s"http://localhost:$TestPort/status/NOPE!!", 404)

  @Test def testHealth(): Unit =
    val response = requests.get(s"http://localhost:$TestPort/health")
    Assert.assertEquals("1", response.text())

  @Test def testHealthFailsWithExtraData(): Unit =
    assertFailsWithStatus(s"http://localhost:$TestPort/health/xyz", 404)

  @Test def testRegisterWithDebug(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = getPRR(s"${GameData.ServerURL}/register/${newGameResponse.id}/@/d")
    Assert.assertTrue(registerResponse.debug.headers.nonEmpty)

  @Test def testRegisterWithoutDebugDoesNotPassDebugInfo(): Unit =
    val newGameResponse = GameData.create(TestSize)
    val registerResponse = getPRR(s"${GameData.ServerURL}/register/${newGameResponse.id}/@")
    Assert.assertFalse(registerResponse.debug.headers.nonEmpty)

  @Test def testSetStoneWithDebug(): Unit =
    val gameData = setUpGame(TestSize)
    val setResponse = getSR(
      s"${GameData.ServerURL}/set/${gameData.id}/1/1/1/d",
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )
    Assert.assertTrue(setResponse.debug.headers.nonEmpty)

  @Test def testSetStoneWithoutDebugDoesNotPassDebugInfo(): Unit =
    val gameData = setUpGame(TestSize)
    val setResponse = getSR(
      s"${GameData.ServerURL}/set/${gameData.id}/1/1/1",
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )
    Assert.assertFalse(setResponse.debug.headers.nonEmpty)

  @Test def testSetStoneWithDebugOnBigField(): Unit =
    val gameData = setUpGame(11)
    val setResponse = getSR(
      s"${GameData.ServerURL}/set/${gameData.id}/11/11/11/d",
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )
    Assert.assertTrue(setResponse.debug.headers.nonEmpty)

  @Test def testPassWithDebug(): Unit =
    val gameData = setUpGame(TestSize)
    val passResponse = getSR(
      s"${GameData.ServerURL}/pass/${gameData.id}/d",
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )
    Assert.assertTrue(passResponse.debug.headers.nonEmpty)

  @Test def testPassWithoutDebugDoesNotPassDebugInfo(): Unit =
    val gameData = setUpGame(TestSize)
    val passResponse = getSR(
      s"${GameData.ServerURL}/pass/${gameData.id}",
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )
    Assert.assertFalse(passResponse.debug.headers.nonEmpty)

  @Test def testGetStatusWithDebug(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = getSR(
      s"${GameData.ServerURL}/status/${gameData.id}/d",
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )
    Assert.assertTrue(statusResponse.debug.headers.nonEmpty)

  @Test def testGetStatusWithoutDebugDoesNotPassDebugInfo(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = getSR(
      s"${GameData.ServerURL}/status/${gameData.id}",
      Map("Authentication" -> s"Basic ${gameData.token(Black)}")
    )
    Assert.assertFalse(statusResponse.debug.headers.nonEmpty)

  @Test def testGetStatusWithDebugWithoutAuthDoesNotPassDebugInfo(): Unit =
    val gameData = setUpGame(TestSize)
    val statusResponse = getSR(s"${GameData.ServerURL}/status/${gameData.id}/d", Map())
    Assert.assertFalse(statusResponse.debug.headers.nonEmpty)

  def playListOfMoves(gameData: GameData, moves: Iterable[Move | Pass]): StatusResponse =
    var statusResponse: StatusResponse = null
    for move <- moves do
      statusResponse = move match
        case m: Move => gameData.set(m)
        case p: Pass => gameData.pass(p.color)
    return statusResponse

def randomChoice[T](elements: List[T]): T = elements((new Random).nextInt(elements.length))

def getJson(url: String): Source = Source.fromURL(url)

def getPRR(url: String): PlayerRegisteredResponse =
  val json = getJson(url).mkString
  val result = decode[PlayerRegisteredResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  return result.getOrElse(null)

def getGCR(url: String): GameCreatedResponse =
  val json = getJson(url).mkString
  val result = decode[GameCreatedResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  return result.getOrElse(null)

def getSR(url: String, header: Map[String, String]): StatusResponse =
  val response = requests.get(url, headers = header)
  val json = response.text()
  val result = decode[StatusResponse](json)
  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
  return result.getOrElse(null)

def setUpGame(size: Int): GameData =
  val newGameResponse = GameData.create(size)
  val blackRegistered = GameData.register(newGameResponse.id, Black)
  val whiteRegistered = GameData.register(newGameResponse.id, White)
  val tokens = Map(Black -> blackRegistered.authToken, White -> whiteRegistered.authToken)
  return GameData(newGameResponse.id, tokens)

def gameWithBlackAt111(size: Int): StatusResponse =
  setUpGame(size).set(Move(Position(1, 1, 1), Black))

def playRandomGame(gameData: GameData) =
    var gameOver = false
    var color = Black
    while !gameOver do
      val statusResponse = gameData.status(color)
      if statusResponse.ready then
        val move = Move(randomChoice(statusResponse.game.possibleMoves(color)), color)
        gameOver = gameData.set(move).game.isOver
      else
        gameOver = gameData.pass(color).game.isOver
      color = !color

def assertFailsWithStatus(url: String, expectedStatus: Int,
                          headers: Map[String, String] = Map()): Unit =
  try
    val response = requests.get(url, headers = headers)
    Assert.fail("request unexpectedly succeeded")
  catch
    case e: RequestFailedException => Assert.assertEquals(
      e.response.text(), expectedStatus, e.response.statusCode
    )
    case e: Throwable => Assert.fail(
      s"expected RequestFailedException, got ${e.getClass.getSimpleName}"
    )
