package go3d.testing

import go3d.BadColor
import org.junit.{After, Assert, Before, Test}
import go3d.client._
import go3d.server.GoServer
import org.eclipse.jetty.server.Server
import java.io.IOException
import java.net.{ConnectException, UnknownHostException}

val ClientTestPort = 64556

class TestAsciiClient:

  var jetty: Server = null

  @Before def startJetty(): Unit =
    System.setProperty("org.eclipse.jetty.LEVEL", "OFF")
    jetty = GoServer.createServer(ClientTestPort)
    jetty.start()

  @After def stopJetty(): Unit = jetty.stop()

  @Test def testBadColor(): Unit =
    assertThrows[BadColor]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      ))
    })

  @Test def testUnknownHost(): Unit =
    assertThrows[UnknownHostException]({
      AsciiClient.parseArgs(Array(
        "--server", "doesnt exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })

  @Test def testMissingArguments(): Unit =
    assertThrows[NoSuchElementException]({
      AsciiClient.parseArgs(Array(
        "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3"
      ))
    })
    assertThrows[IllegalArgumentException]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
      ))
    })
