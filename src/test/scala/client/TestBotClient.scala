package go3d.testing

import go3d.{BadColor, Position}
import go3d.client.*
import go3d.server.GoServer
import org.eclipse.jetty.server.Server
import org.junit.{After, Assert, Before, Test}

import java.io.IOException
import java.net.{ConnectException, UnknownHostException}


class TestBotClient:

  @Test def testBadColor(): Unit =
    assertThrows[BadColor]({
      BotClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      ))
    })

  @Test def testUnknownHost(): Unit =
    assertThrows[UnknownHostException]({
      BotClient.parseArgs(Array(
        "--server", "doesnt exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })

  @Test def testMissingArguments(): Unit =
    assertThrows[NoSuchElementException]({
      BotClient.parseArgs(Array(
        "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      BotClient.parseArgs(Array(
        "--server", "localhost", "--size", "3", "--color", "b"
      ))
    })
    assertThrows[NoSuchElementException]({
      BotClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3"
      ))
    })
    assertThrows[IllegalArgumentException]({
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
      ))
    })

  @Test def testExecutionTimeString(): Unit =
    Assert.assertEquals("", BotClient.executionTimeString)
    BotClient.executionTimes = BotClient.executionTimes.appended(10)
    Assert.assertTrue(BotClient.executionTimeString.startsWith("(10ms last/10ms avg)"))
    BotClient.executionTimes = BotClient.executionTimes.appended(30)
    Assert.assertTrue(BotClient.executionTimeString.startsWith("(30ms last/20ms avg)"))
