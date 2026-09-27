package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*
import java.net.UnknownHostException
import org.rogach.scallop.exceptions.ValidationFailure

class TestBotClient:

  @Test def testBadColor(): Unit =
    assertParseFails(
      classOf[BadColor], BotClient.parseArgs,
      "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--color", "bx"
    )

  @Test def testUnknownHost(): Unit =
    assertParseFails(
      classOf[UnknownHostException], BotClient.parseArgs,
      "--server", "doesnt-exist", "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
    )

  @Test def testMissingServer(): Unit =
    assertParseFails(
      classOf[NoSuchElementException], BotClient.parseArgs,
      "--port", ClientTestPort.toString, "--size", "3", "--color", "b"
    )

  @Test def testMissingPort(): Unit =
    assertParseFails(
      classOf[NoSuchElementException], BotClient.parseArgs,
      "--server", "localhost", "--size", "3", "--color", "b"
    )

  @Test def testMissingColor(): Unit =
    assertParseFails(
      classOf[ValidationFailure], BotClient.parseArgs,
      "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3"
    )

  @Test def testMissingSize(): Unit =
    assertParseFails(
      classOf[ValidationFailure], BotClient.parseArgs,
      "--server", "localhost", "--port", ClientTestPort.toString, "--color", "b"
    )

  @Test def testConflictingArguments(): Unit =
    assertParseFails(
      classOf[ValidationFailure], BotClient.parseArgs,
      "--server", "localhost", "--port", ClientTestPort.toString, "--size", "3", "--game-id", "1"
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

  @Test def testWaitUntilReadyPollsUntilReady(): Unit =
    val client = new NotReadyClient(3)
    Assertions.assertTrue(BotClient.waitUntilReady(client).success.value.ready)
    Assertions.assertEquals(3, client.statusCalls)

  @Test def testWaitUntilReadyPollsUntilReadyForInteractiveClient(): Unit =
    val client = new NotReadyClient(2)
    Assertions.assertTrue(AsciiClient.waitUntilReady(client).success.value.ready)
    Assertions.assertEquals(2, client.statusCalls)

  @Test def testMakeOneMoveSetsWhenAMoveIsPossible(): Unit =
    val client = new RecordingClient
    val status = mockStatus(ready = true, moves = List(Position(1, 1, 1)))
    val (over, _) = BotClient.makeOneMove(client, status, status.game, None).success.value
    Assertions.assertFalse(over)
    Assertions.assertEquals(List("set 1 1 1"), client.sent)

  @Test def testMakeOneMovePassesWhenNoMoveIsPossible(): Unit =
    val client = new RecordingClient
    val status = mockStatus(ready = true, moves = List())
    val (over, _) = BotClient.makeOneMove(client, status, status.game, None).success.value
    Assertions.assertFalse(over)
    Assertions.assertEquals(List("pass"), client.sent)

  @Test def testMakeOneMoveNarrowsDownWithStrategy(): Unit =
    val client = new RecordingClient
    val status = mockStatus(ready = true, moves = List(Position(1, 1, 1), Position(2, 2, 2)))
    val strategy = Some(SetStrategy(3, Array("closestToCenter")))
    BotClient.makeOneMove(client, status, status.game, strategy).success.value
    Assertions.assertEquals(List("set 2 2 2"), client.sent)
