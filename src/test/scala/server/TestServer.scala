package go3d.testing

import go3d.server._
import go3d.{Black, White}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.http.HttpStatus
import org.junit.{After, Assert, Before, Test}

import java.io.IOException
import java.net.HttpURLConnection
import scala.io.Source
import scala.reflect.ClassTag
import io.circe.parser._

val TestPort = 64555

class TestServer:

  var jetty: Server = null

  @Before def startJetty(): Unit =
    jetty = GoServer.createServer(TestPort)
    val handler = ServletHandler()
    handler.addServletWithMapping(classOf[NewGameServlet], GoServer.newRoute)
    handler.addServletWithMapping(classOf[RegisterPlayerServlet], GoServer.registerRoute)
    jetty.setHandler(handler)
    jetty.start()

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

  @Test def testRegisterOnePlayerTwiceFails(): Unit =
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

def getJson(url: String): Source = Source.fromURL(url)

//def getResponse[T](url: String): T =
//  val json = getJson(url).mkString
//  return decode[T](json)

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
