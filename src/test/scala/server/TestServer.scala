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
    val response = getResponse[GameCreatedResponse](s"http://localhost:$TestPort/new/$TestSize")
    Assert.assertNotNull(response.id)
    Assert.assertNotEquals("", response.id)
    Assert.assertEquals(TestSize, response.size)

  @Test def testNewGameFailsWithBadSize(): Unit =
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/1")})
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/4")})
    assertThrows[IOException]({getJson(s"http://localhost:$TestPort/new/27")})

  @Test def testRegisterOnePlayer(): Unit =
    val newGameResponse = getResponse[GameCreatedResponse](
      s"http://localhost:$TestPort/new/$TestSize"
    )
    val registerResponse = getResponse[PlayerRegisteredResponse](
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    Assert.assertEquals(TestSize, registerResponse.game.size)
    Assert.assertEquals(Black, registerResponse.color)

  @Test def testRegisterTwoPlayers(): Unit =
    val newGameResponse = getResponse[GameCreatedResponse](
      s"http://localhost:$TestPort/new/$TestSize"
    )
    getResponse[PlayerRegisteredResponse](
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    val registerResponse = getResponse[PlayerRegisteredResponse](
      s"http://localhost:$TestPort/register/${newGameResponse.id}/O"
    )
    Assert.assertEquals(TestSize, registerResponse.game.size)
    Assert.assertEquals(White, registerResponse.color)

  @Test def testRegisterOnePlayerTwiceFails(): Unit =
    val newGameResponse = getResponse[GameCreatedResponse](
      s"http://localhost:$TestPort/new/$TestSize"
    )
    getResponse[PlayerRegisteredResponse](
      s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
    )
    assertThrows[IOException](
      {getResponse[PlayerRegisteredResponse](
        s"http://localhost:$TestPort/register/${newGameResponse.id}/@"
      )}
    )

  @Test def testRegisterAtNonexistentGameFails(): Unit =
    val newGameResponse = getResponse[GameCreatedResponse](
      s"http://localhost:$TestPort/new/$TestSize"
    )
    assertThrows[IOException](
      {getResponse[PlayerRegisteredResponse](
        s"http://localhost:$TestPort/register/${newGameResponse.id+"NOPE"}/@"
      )}
    )

def getJson(url: String): Source = Source.fromURL(url)

def getResponse[T](url: String)(implicit ct: ClassTag[T]): T =
  val json = getJson(url).mkString
  return Jsonify.fromJson[T](json)