package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
import java.net.UnknownHostException
import org.rogach.scallop.exceptions.ValidationFailure
import org.scalatest.TryValues.*

class TestBotClient:

  @Test def testBadColor(): Unit =
    Assertions.assertInstanceOf(
      classOf[BadColor],
      BotClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
      )).failure.exception
    )

  @Test def testUnknownHost(): Unit =
    Assertions.assertInstanceOf(
      classOf[UnknownHostException],
      BotClient.parseArgs(Array(
        "--server", "doesnt-exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      )).failure.exception
    )

  @Test def testMissingServer(): Unit =
    Assertions.assertInstanceOf(
      classOf[NoSuchElementException],
      BotClient.parseArgs(Array(
        "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
      )).failure.exception
    )

  @Test def testMissingPort(): Unit =
    Assertions.assertInstanceOf(
      classOf[NoSuchElementException],
      BotClient.parseArgs(Array(
        "--server", "localhost", "--size", "3", "--color", "b"
      )).failure.exception
    )

  @Test def testMissingColor(): Unit =
    Assertions.assertInstanceOf(
      classOf[ValidationFailure],
      BotClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3"
      )).failure.exception
    )

  @Test def testMissingSize(): Unit =
    Assertions.assertInstanceOf(
      classOf[ValidationFailure],
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
      )).failure.exception
    )

  @Test def testConflictingArguments(): Unit =
    Assertions.assertInstanceOf(
      classOf[ValidationFailure],
      AsciiClient.parseArgs(Array(
        "--server", "localhost", "--port", ClientTestPort.toString,
        "--size", "3", "--game-id", "1"
      )).failure.exception
    )

  @Test def testStrategyIsParsed(): Unit =
    BotClient.parseArgs(Array(
      "--server", "localhost", "--port", ClientTestPort.toString, "--game-id", "",  "--token", "",
      "--strategy", "random"
    ))
    Assertions.assertTrue(Array("random").sameElements(BotClient.strategies))

  @Test def testStrategyWithMultipleElementsIsParsed(): Unit =
    BotClient.parseArgs(Array(
      "--server", "localhost", "--port", ClientTestPort.toString, "--game-id", "",  "--token", "",
      "--strategy", "closestToStarPoints,prioritiseCapture"
    ))
    Assertions.assertTrue(
      Array("closestToStarPoints","prioritiseCapture").sameElements(BotClient.strategies)
    )


  @Test def testExecutionTimeString(): Unit =
    Assertions.assertEquals("", BotClient.executionTimeString)
    BotClient.executionTimes = BotClient.executionTimes.appended(10)
    Assertions.assertTrue(BotClient.executionTimeString.startsWith("(10ms last/10ms avg)"))
    BotClient.executionTimes = BotClient.executionTimes.appended(30)
    Assertions.assertTrue(BotClient.executionTimeString.startsWith("(30ms last/20ms avg)"))

  @Test def testWaitUntilReadyReturnsStatusWhenReady(): Unit =
    val client = new MockClient
    val status = BotClient.waitUntilReady(client)
    Assertions.assertTrue(status.isSuccess)
    Assertions.assertTrue(status.success.value.ready)
    Assertions.assertEquals(3, status.success.value.game.size)
