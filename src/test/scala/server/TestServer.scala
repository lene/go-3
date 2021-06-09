package go3d.testing

import go3d.server._
import go3d.{Black, White, newGame}
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

  @Test def testGetStatusWithoutAuthFails(): Unit =
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
      s"http://localhost:$TestPort/status/${newGameResponse.id}", Map()
    )})

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


  @Ignore
  @Test def testSetStoneForBlackAtReadyStatusReturnsStatusNotReady(): Unit = ???
  @Ignore
  @Test def testSetStoneForBlackAtReadyStatusReturnsNoPossibleMoves(): Unit = ???
  @Ignore
  @Test def testGetStatusForBlackAfterBlackSetStoneReturnsStatusNotReady(): Unit = ???
  @Ignore
  @Test def testGetStatusForBlackAfterBlackSetStoneReturnsStatusNoPossibleMoves(): Unit = ???
  @Ignore
  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsReady(): Unit = ???
  @Ignore
  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsUpdatedBoard(): Unit = ???
  @Ignore
  @Test def testGetStatusForWhiteAfterBlackSetStoneReturnsPossibleMoves(): Unit = ???
  @Ignore
  @Test def testGetFullStatusWithoutAuthReturnsBoardButNothingElse(): Unit = ???

  @Ignore
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
      s"http://localhost:$TestPort/pass",
      Map("Authentication" -> s"Basic $blackToken")
    )
    Assert.assertFalse(passResponse.ready)
    Assert.assertTrue(passResponse.moves.isEmpty)

  @Ignore
  @Test def testPassTwiceReturnsGameOver(): Unit = ???


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
