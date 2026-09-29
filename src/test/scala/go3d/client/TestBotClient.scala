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
      ServerOpt, "localhost", PortOpt, ClientTestPort.toString, SizeOpt, "3", ColorOpt, "bx"
    )

  @Test def testUnknownHost(): Unit =
    assertParseFails(
      classOf[UnknownHostException], BotClient.parseArgs,
      ServerOpt, "doesnt-exist", PortOpt, ClientTestPort.toString, SizeOpt, "3", ColorOpt, "b"
    )

  @Test def testMissingServer(): Unit =
    assertParseFails(
      classOf[NoSuchElementException], BotClient.parseArgs,
      PortOpt, ClientTestPort.toString, SizeOpt, "3", ColorOpt, "b"
    )

  @Test def testMissingPort(): Unit =
    assertParseFails(
      classOf[NoSuchElementException], BotClient.parseArgs,
      ServerOpt, "localhost", SizeOpt, "3", ColorOpt, "b"
    )

  @Test def testMissingColor(): Unit =
    assertParseFails(
      classOf[ValidationFailure], BotClient.parseArgs,
      ServerOpt, "localhost", PortOpt, ClientTestPort.toString, SizeOpt, "3"
    )

  @Test def testMissingSize(): Unit =
    assertParseFails(
      classOf[ValidationFailure], BotClient.parseArgs,
      ServerOpt, "localhost", PortOpt, ClientTestPort.toString, ColorOpt, "b"
    )

  @Test def testConflictingArguments(): Unit =
    assertParseFails(
      classOf[ValidationFailure], BotClient.parseArgs,
      ServerOpt, "localhost", PortOpt, ClientTestPort.toString, SizeOpt, "3", GameIdOpt, "1"
    )

  @Test def testStrategyIsParsed(): Unit =
    BotClient.parseArgs(Array(
      ServerOpt, "localhost", PortOpt, ClientTestPort.toString, GameIdOpt, "",  TokenOpt, "",
      "--strategy", "random"
    ))
    Assertions.assertTrue(Array("random").sameElements(BotClient.strategies))

  @Test def testStrategyWithMultipleElementsIsParsed(): Unit =
    BotClient.parseArgs(Array(
      ServerOpt, "localhost", PortOpt, ClientTestPort.toString, GameIdOpt, "",  TokenOpt, "",
      "--strategy", "closestToStarPoints,prioritiseCapture"
    ))
    Assertions.assertTrue(
      Array("closestToStarPoints","prioritiseCapture").sameElements(BotClient.strategies)
    )


  @Test def testUrlReplacesServerAndPort(): Unit =
    val client = BotClient.parseArgs(Array(
      UrlOpt, "https://api.example.com/", GameIdOpt, "", TokenOpt, ""
    ))
    Assertions.assertEquals("https://api.example.com", client.success.value.serverURL)

  @Test def testUrlWithServerFails(): Unit =
    assertParseFails(
      classOf[ValidationFailure], BotClient.parseArgs,
      UrlOpt, "https://api.example.com", ServerOpt, "localhost", GameIdOpt, "", TokenOpt, ""
    )

  @Test def testPollIntervalIsParsed(): Unit =
    BotClient.parseArgs(Array(
      ServerOpt, "localhost", PortOpt, ClientTestPort.toString, GameIdOpt, "",  TokenOpt, "",
      "--poll-interval-ms", "500"
    )).success.value
    Assertions.assertEquals(500, BotClient.pollIntervalMs)
    BotClient.parseArgs(Array(
      ServerOpt, "localhost", PortOpt, ClientTestPort.toString, GameIdOpt, "",  TokenOpt, ""
    )).success.value
    Assertions.assertEquals(10, BotClient.pollIntervalMs)

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
