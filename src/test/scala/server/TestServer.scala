package go3d.testing

import go3d.server._
import go3d.{Black, White, newGame, Move, Pass, Empty, Position}
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

val TestPort = 64555

class TestServer:

  var jetty: Server = null

  @Before def startJetty(): Unit =
    jetty = GoServer.createServer(TestPort)
    val handler = ServletHandler()
    handler.addServletWithMapping(classOf[NewGameServlet], GoServer.newRoute)
    handler.addServletWithMapping(classOf[RegisterPlayerServlet], GoServer.registerRoute)
    handler.addServletWithMapping(classOf[StatusServlet], GoServer.statusRoute)
    handler.addServletWithMapping(classOf[SetServlet], GoServer.setRoute)
    handler.addServletWithMapping(classOf[PassServlet], GoServer.passRoute)
    jetty.setHandler(handler)
    jetty.start()

  @Before def setupTempDir(): Unit =
    Io.init(Files.createTempDirectory("go3d").toString)

  @After def stopJetty(): Unit = jetty.stop()

  @Test def testNewGame(): Unit =
    val response = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    Assert.assertNotNull(response.id)
    Assert.assertNotEquals("", response.id)
    Assert.assertEquals(TestSize, response.size)

  @Test def testNewGameFailsWithBadSize(): Unit =
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/1")})
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/4")})
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/27")})

  @Test def testRegisterOnePlayer(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val registerResponse = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    Assert.assertEquals(TestSize, registerResponse.game.size)
    Assert.assertEquals(Black, registerResponse.color)

  @Test def testRegisterTwoPlayers(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val registerResponse = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    Assert.assertEquals(TestSize, registerResponse.game.size)
    Assert.assertEquals(White, registerResponse.color)

  @Test def testRegisterTwoPlayersBlackLastSetsReady(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val registerResponse = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    Assert.assertTrue(registerResponse.ready)

  @Test def testRegisterTwoPlayersWhiteLastDoesNotSetReady(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val registerResponse = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    Assert.assertFalse(registerResponse.ready)

  @Test def testRegisterOnePlayerDoesNotSetReady(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val registerResponse = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    Assert.assertFalse(registerResponse.ready)

  @Test def testRegisterSamePlayerTwiceFails(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    assertThrows[IOException](
      {getPRR(
        s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
      )}
    )

  @Test def testRegisterAtNonexistentGameFails(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    assertThrows[IOException](
      {getPRR(
        s"http://localhost:$TestPort/register/${newGameResponse.id+"NOPE"}/@"
      )}
    )

  @Test def testGetStatusAfterBothRegisteredForBlackIsReady(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertTrue(statusResponse.ready)

  @Test def testGetStatusAfterBothRegisteredForWhiteIsNotReady(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val whiteToken = whiteRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $whiteToken")
    )
    Assert.assertFalse(statusResponse.toString, statusResponse.ready)

  @Test def testGetStatusAfterBothRegisteredForBlackHasPossibleMoves(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertTrue(statusResponse.moves.nonEmpty)

  @Test def testGetStatusAfterBothRegisteredForWhiteHasNoPossibleMoves(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val whiteToken = whiteRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $whiteToken")
    )
    Assert.assertTrue(statusResponse.moves.toString, statusResponse.moves.isEmpty)

  @Test def testSetStoneWithoutAuthFails(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    assertThrows[RequestFailedException]({getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/1/1/1", Map()
    )})

  @Test def testSetStoneWithoutAuthSetsStatus401(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    try
      val response = requests.get(s"http://localhost:$TestPort/set/${newGameResponse.id}/1/1/1")
    catch
      case e: RequestFailedException => Assert.assertEquals(401, e.response.statusCode)
      case _ => Assert.fail()

  @Test def testSetStoneForBlackAtReadyStatusSucceeds(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertTrue(statusResponse.ready)
    Assert.assertTrue(statusResponse.moves.nonEmpty)
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )

  @Test def testSetStoneForBlackAtReadyStatusReturnsUpdatedBoard(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertTrue(statusResponse.ready)
    Assert.assertTrue(statusResponse.moves.nonEmpty)
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertEquals(Black, setResponse.game.at(move))

  @Test def testSetStoneForBlackAtReadyStatusReturnsStatusNotReady(): Unit =
    val newGameResponse = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    val blackRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/@")
    val whiteRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/O")
    val blackToken = blackRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertTrue(statusResponse.ready)
    Assert.assertTrue(statusResponse.moves.nonEmpty)
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertFalse(setResponse.ready)

  @Test def testSetStoneForBlackAtReadyStatusReturnsNoPossibleMoves(): Unit =
    val newGameResponse = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    val blackRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/@")
    val whiteRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/O")
    val blackToken = blackRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertTrue(statusResponse.ready)
    Assert.assertTrue(statusResponse.moves.nonEmpty)
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertTrue(setResponse.moves.isEmpty)

  @Test def testGetStatusForBlackAfterBlackSetStoneReturnsStatusNotReady(): Unit =
    val newGameResponse = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    val blackRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/@")
    val whiteRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/O")
    val blackToken = blackRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val statusResponseAgain = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertFalse(statusResponseAgain.ready)

  @Test def testGetStatusForBlackAfterBlackSetStoneReturnsStatusNoPossibleMoves(): Unit =
    val newGameResponse = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    val blackRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/@")
    val whiteRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/O")
    val blackToken = blackRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val statusResponseAgain = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertTrue(statusResponseAgain.moves.isEmpty)

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsReady(): Unit =
    val newGameResponse = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    val blackRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/@")
    val whiteRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/O")
    val blackToken = blackRegistered.authToken
    val whiteToken = whiteRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val statusResponseAgain = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $whiteToken")
    )
    Assert.assertTrue(statusResponseAgain.ready)

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsUpdatedBoard(): Unit =
    val newGameResponse = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    val blackRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/@")
    val whiteRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/O")
    val blackToken = blackRegistered.authToken
    val whiteToken = whiteRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val statusResponseAgain = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $whiteToken")
    )
    Assert.assertEquals(Black, statusResponseAgain.game.at(move))

  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsPossibleMoves(): Unit =
    val newGameResponse = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    val blackRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/@")
    val whiteRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/O")
    val blackToken = blackRegistered.authToken
    val whiteToken = whiteRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val move = statusResponse.moves.last
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val statusResponseAgain = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $whiteToken")
    )
    Assert.assertTrue(statusResponseAgain.moves.nonEmpty)

  @Test def testGetStatusWithoutAuthReturnsBoardButNothingElse(): Unit =
    val newGameResponse = getGCR(s"http://localhost:$TestPort/new/$TestSize")
    val blackRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/@")
    val whiteRegistered = getPRR(s"http://localhost:$TestPort/register/${newGameResponse.id}/O")
    val blackToken = blackRegistered.authToken
    val whiteToken = whiteRegistered.authToken
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}", Map()
    )
    Assert.assertFalse(statusResponse.ready)
    val setResponse = getSR(
      s"http://localhost:$TestPort/set/${newGameResponse.id}/1/1/1",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val statusResponseAgain = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}", Map()
    )
    Assert.assertFalse(statusResponseAgain.ready)
    Assert.assertEquals(0, statusResponseAgain.moves.length)
    Assert.assertEquals(Black, statusResponseAgain.game.at(1, 1, 1))

  @Test def testPassReturnsNonReadyStatus(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val passResponse = getSR(
      s"http://localhost:$TestPort/pass/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertFalse(passResponse.ready)
    Assert.assertTrue(passResponse.moves.isEmpty)

  @Test def testPassTwiceReturnsGameOver(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val whiteToken = whiteRegistered.authToken
    val pass1Response = getSR(
      s"http://localhost:$TestPort/pass/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $blackToken")
    )
    val pass2Response = getSR(
      s"http://localhost:$TestPort/pass/${newGameResponse.id}",
      Map("Authentication" -> s"Basic $whiteToken")
    )
    Assert.assertTrue(pass2Response.game.isOver)

  @Test def testPlayListOfMoves(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val whiteToken = whiteRegistered.authToken
    playListOfMoves(newGameResponse.id, blackToken, whiteToken, CaptureMoves.dropRight(1))
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}", Map()
    )
    for move <- CaptureMoves.dropRight(1) do
      Assert.assertEquals(
        move.toString+"\n"+statusResponse.game.toString,
        move.color, statusResponse.game.at(move.position)
      )

  @Test def testCaptureStone(): Unit =
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val whiteToken = whiteRegistered.authToken
    playListOfMoves(newGameResponse.id, blackToken, whiteToken, CaptureMoves)
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}", Map()
    )
    Assert.assertEquals(
      "\n"+statusResponse.game.toString,
      Empty, statusResponse.game.at(Position(2, 2, 1))
    )

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
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/5"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val blackToken = blackRegistered.authToken
    val whiteToken = whiteRegistered.authToken

    playListOfMoves(newGameResponse.id, blackToken, whiteToken, moves)
    val statusResponse = getSR(
      s"http://localhost:$TestPort/status/${newGameResponse.id}", Map()
    )
    Assert.assertTrue(statusResponse.game.captures.isEmpty)
    val statusResponseAgain = playListOfMoves(
      newGameResponse.id, blackToken, whiteToken,List(Move(3, 1, 1, White))
    )
    Assert.assertEquals(2, statusResponseAgain.game.captures(Black))

  @Test def testPlayRandomGame(): Unit =
    import scala.util.Random
    val random = new Random
    val newGameResponse = getGCR(
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val blackRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val whiteRegistered = getPRR(
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    val tokens = Map(Black -> blackRegistered.authToken, White -> whiteRegistered.authToken)
    var gameOver = false
    var color = Black
    while !gameOver do
      val statusResponse = getSR(
        s"http://localhost:$TestPort/status/${newGameResponse.id}",
        Map("Authentication" -> s"Basic ${tokens(color)}")
      )
      if statusResponse.ready then
        val move = Move(
          statusResponse.game.possibleMoves(color)(
            random.nextInt(statusResponse.game.possibleMoves(color).length)
          ), color
        )
        val setResponse = getSR(
          s"http://localhost:$TestPort/set/${newGameResponse.id}/${move.x}/${move.y}/${move.z}",
          Map("Authentication" -> s"Basic ${tokens(color)}")
        )
        gameOver = setResponse.game.isOver
      else
        val passResponse = getSR(
          s"http://localhost:$TestPort/pass/${newGameResponse.id}",
          Map("Authentication" -> s"Basic ${tokens(color)}")
        )
        gameOver = passResponse.game.isOver
      color = !color


  def playListOfMoves(gameId: String, blackToken: String, whiteToken: String,
                      moves: Iterable[Move | Pass]): StatusResponse =
    var statusResponse: StatusResponse = null
    for move <- moves do
      val token = if move.color == Black then blackToken else whiteToken
      val url =
        move match
          case m: Move => s"http://localhost:$TestPort/set/$gameId/${m.x}/${m.y}/${m.z}"
          case p: Pass => s"http://localhost:$TestPort/pass/$gameId"
      statusResponse = getSR(url, Map("Authentication" -> s"Basic $token"))
    return statusResponse

def getJson(url: String): Source = Source.fromURL(url)

//def getResponse[T](url: String): T =
//  val json = getJson(url).mkString
//  return decode[T](json)

//def getSR(url: String, token: String): StatusResponse =
//  val json = getJson(url).mkString
//  val result = decode[StatusResponse](json)
//  if result.isLeft then throw ServerException(result.left.getOrElse(null).getMessage)
//  return result.getOrElse(null)

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
