package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
import java.net.UnknownHostException

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
    Assertions.assertEquals("", BotClient.executionTimeString)
    BotClient.executionTimes = BotClient.executionTimes.appended(10)
    Assertions.assertTrue(BotClient.executionTimeString.startsWith("(10ms last/10ms avg)"))
    BotClient.executionTimes = BotClient.executionTimes.appended(30)
    Assertions.assertTrue(BotClient.executionTimeString.startsWith("(30ms last/20ms avg)"))
