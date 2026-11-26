package go3d.client

import go3d._
import go3d.server.{RequestInfo, StatusResponse}
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*
import java.net.UnknownHostException
import org.rogach.scallop.exceptions.ValidationFailure
import scala.util.{Failure, Success, Try}

class TestBotClient:

  @Test def testBadColor(): Unit =
    assertParseFails(
      classOf[BadColor],
      BotClient.parseArgs,
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3",
      ColorOpt,
      "bx"
    )

  @Test def testUnknownHost(): Unit =
    assertParseFails(
      classOf[UnknownHostException],
      BotClient.parseArgs,
      ServerOpt,
      "doesnt-exist",
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3",
      ColorOpt,
      "b"
    )

  @Test def testMissingServer(): Unit =
    assertParseFails(
      classOf[NoSuchElementException],
      BotClient.parseArgs,
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3",
      ColorOpt,
      "b"
    )

  @Test def testMissingPort(): Unit =
    assertParseFails(
      classOf[NoSuchElementException],
      BotClient.parseArgs,
      ServerOpt,
      "localhost",
      SizeOpt,
      "3",
      ColorOpt,
      "b"
    )

  @Test def testMissingColor(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      BotClient.parseArgs,
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3"
    )

  @Test def testMissingSize(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      BotClient.parseArgs,
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      ColorOpt,
      "b"
    )

  @Test def testConflictingArguments(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      BotClient.parseArgs,
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      SizeOpt,
      "3",
      GameIdOpt,
      ValidGameId
    )

  @Test def testStrategyIsParsed(): Unit =
    BotClient.parseArgs(Array(
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      GameIdOpt,
      ValidGameId,
      TokenOpt,
      "",
      "--strategy",
      "random"
    ))
    Assertions.assertTrue(Array("random").sameElements(BotClient.strategies))

  @Test def testStrategyWithMultipleElementsIsParsed(): Unit =
    BotClient.parseArgs(Array(
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      GameIdOpt,
      ValidGameId,
      TokenOpt,
      "",
      "--strategy",
      "closestToStarPoints,prioritiseCapture"
    ))
    Assertions.assertTrue(
      Array("closestToStarPoints", "prioritiseCapture").sameElements(BotClient.strategies)
    )

  @Test def testUrlReplacesServerAndPort(): Unit =
    val client = BotClient.parseArgs(Array(
      UrlOpt,
      "https://api.example.com/",
      GameIdOpt,
      ValidGameId,
      TokenOpt,
      ""
    ))
    Assertions.assertEquals("https://api.example.com", client.success.value.serverURL)

  @Test def testUrlWithServerFails(): Unit =
    assertParseFails(
      classOf[ValidationFailure],
      BotClient.parseArgs,
      UrlOpt,
      "https://api.example.com",
      ServerOpt,
      "localhost",
      GameIdOpt,
      ValidGameId,
      TokenOpt,
      ""
    )

  @Test def testMalformedGameIdFailsBeforeAnyRequest(): Unit =
    for id <- List("<id>", "", "ABCDE", "ABCDEFG", "ABC-EF") do
      assertParseFails(
        classOf[ValidationFailure],
        BotClient.parseArgs,
        UrlOpt,
        "https://api.example.com",
        GameIdOpt,
        id,
        TokenOpt,
        "token"
      )

  @Test def testPollIntervalIsParsed(): Unit =
    BotClient.parseArgs(Array(
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      GameIdOpt,
      ValidGameId,
      TokenOpt,
      "",
      "--poll-interval-ms",
      "500"
    )).success.value
    Assertions.assertEquals(500, BotClient.pollIntervalMs)
    BotClient.parseArgs(Array(
      ServerOpt,
      "localhost",
      PortOpt,
      ClientTestPort.toString,
      GameIdOpt,
      ValidGameId,
      TokenOpt,
      ""
    )).success.value
    Assertions.assertEquals(10, BotClient.pollIntervalMs)

  @Test def testExecutionTimeString(): Unit =
    BotClient.executionTimes = List() // the tests that play moves record times too
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
    val choice = BotClient.heuristicChoice(None)
    Assertions.assertFalse(BotClient.makeOneMove(client, status, choice).success.value)
    Assertions.assertEquals(List("set 1 1 1"), client.sent)

  @Test def testMakeOneMovePassesWhenNoMoveIsPossible(): Unit =
    val client = new RecordingClient
    val status = mockStatus(ready = true, moves = List())
    val choice = BotClient.heuristicChoice(None)
    Assertions.assertFalse(BotClient.makeOneMove(client, status, choice).success.value)
    Assertions.assertEquals(List("pass"), client.sent)

  @Test def testMakeOneMoveNarrowsDownWithStrategy(): Unit =
    val client = new RecordingClient
    val status = mockStatus(ready = true, moves = List(Position(1, 1, 1), Position(2, 2, 2)))
    val choice = BotClient.heuristicChoice(Some(SetStrategy(3, Array("closestToCenter"))))
    BotClient.makeOneMove(client, status, choice).success.value
    Assertions.assertEquals(List("set 2 2 2"), client.sent)

  @Test def testModelStoreAndSimulationsHaveDefaults(): Unit =
    BotClient.parseArgs(gameArgs()).success.value
    Assertions.assertEquals(MoveEngineSettings("models", 800), BotClient.engineSettings)

  @Test def testModelStoreAndSimulationsAreParsed(): Unit =
    BotClient.parseArgs(gameArgs("--model-store", "store", "--simulations", "100")).success.value
    Assertions.assertEquals(MoveEngineSettings("store", 100), BotClient.engineSettings)

  @Test def testSimulationsMustBePositive(): Unit =
    assertParseFails(classOf[ValidationFailure], BotClient.parseArgs, gameArgs(SimOpt, "0")*)

  @Test def testModelStrategyFailsWithoutTheEngine(): Unit =
    BotClient.parseArgs(gameArgs(StrategyOpt, "model:0")).success.value
    val error = BotClient.moveChoice(Game.start(3).success.value).failure.exception
    Assertions.assertInstanceOf(classOf[MoveEngineError], error)
    Assertions.assertTrue(error.getMessage.contains("'model'"), error.getMessage)

  @Test def testMainLoopPlaysNothingWithoutTheEngine(): Unit =
    BotClient.parseArgs(gameArgs(StrategyOpt, "model:0")).success.value
    val result = BotClient.mainLoop(new MockClient)
    Assertions.assertInstanceOf(classOf[MoveEngineError], result.failure.exception)

  @Test def testMainLoopPlaysWithoutAStrategy(): Unit =
    BotClient.parseArgs(gameArgs()).success.value
    val result = BotClient.mainLoop(new PassFailsClient)
    Assertions.assertInstanceOf(classOf[IllegalStateException], result.failure.exception)

  @Test def testEngineStrategyIsPlayedAlone(): Unit =
    BotClient.parseArgs(gameArgs(StrategyOpt, "model:0,closestToCenter")).success.value
    val error = BotClient.moveChoice(Game.start(3).success.value).failure.exception
    Assertions.assertInstanceOf(classOf[MoveEngineError], error)

  @Test def testHeuristicStrategyIsChosenWithoutAnEngine(): Unit =
    BotClient.parseArgs(gameArgs(StrategyOpt, "closestToCenter")).success.value
    val client = new RecordingClient
    val status = mockStatus(ready = true, moves = List(Position(1, 1, 1), Position(2, 2, 2)))
    val choice = BotClient.moveChoice(status.game).success.value
    BotClient.makeOneMove(client, status, choice).success.value
    Assertions.assertEquals(List("set 2 2 2"), client.sent)

  @Test def testEngineChoiceSetsTheEngineMove(): Unit =
    val client = new RecordingClient
    val status = mockStatus(ready = true, moves = List())
    val choice = BotClient.engineChoice(game => Success(Move(3, 2, 1, game.moveColor)))
    BotClient.makeOneMove(client, status, choice).success.value
    Assertions.assertEquals(List("set 3 2 1"), client.sent)

  @Test def testEngineChoicePassesWhenTheEnginePasses(): Unit =
    val client = new RecordingClient
    val status = mockStatus(ready = true, moves = List(Position(1, 1, 1)))
    val choice = BotClient.engineChoice(game => Success(Pass(game.moveColor)))
    BotClient.makeOneMove(client, status, choice).success.value
    Assertions.assertEquals(List("pass"), client.sent)

  @Test def testEveryMoveIsChosenFromTheCurrentStatus(): Unit =
    val afterOwnMove = Game.start(3).flatMap(_.makeMove(Move(1, 1, 1, Black))).success.value
    val afterOpponent = afterOwnMove.makeMove(Move(3, 3, 3, White)).success.value
    val client = new OpponentMovesClient(afterOwnMove, afterOpponent)
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    var chosenFrom = List[Game]()
    val choice: MoveChoice = status =>
      chosenFrom = chosenFrom :+ status.game
      if chosenFrom.length == 1 then Success(Some(Position(1, 1, 1)))
      else Failure(IllegalStateException("stop after the second move"))
    val start = mockStatus(ready = true, moves = List(Position(1, 1, 1)))
    Assertions.assertTrue(BotClient.playMoves(client, Some(start), choice).isFailure)
    Assertions.assertEquals(List(0, 2), chosenFrom.map(_.moves.length))

  @Test def testReadyStatusOfAGameThatIsOverEndsWithoutAMove(): Unit =
    val over = Game.start(3).flatMap(_.makeMove(Pass(Black))).flatMap(_.makeMove(Pass(White)))
      .success.value
    val client = new OpponentMovesClient(over, over)
    val ready = StatusResponse(over, List(), true, None, RequestInfo(Map(), "", "", false))
    val choice: MoveChoice = _ => Failure(IllegalStateException("a move was chosen"))
    Assertions.assertTrue(BotClient.playMoves(client, Some(ready), choice).isSuccess)

private val StrategyOpt = "--strategy"
private val SimOpt = "--simulations"

/** Options for a game that is joined with an empty token, which needs no server. */
private def gameArgs(more: String*): Array[String] =
  (Seq(ServerOpt, "localhost", PortOpt, ClientTestPort.toString, GameIdOpt, ValidGameId) ++
    Seq(TokenOpt, "") ++ more).toArray

/** Answers a move with `afterOwnMove`, not yet ready, and a status poll with `afterOpponent`, the
  * game once the opponent has moved too.
  */
private class OpponentMovesClient(afterOwnMove: Game, afterOpponent: Game)
    extends BaseClient("mock server", "mock id", None, None):
  override def set(x: Int, y: Int, z: Int): Try[StatusResponse] =
    Success(StatusResponse(afterOwnMove, List(), false, None, RequestInfo(Map(), "", "", false)))
  override def status: Try[StatusResponse] =
    Success(StatusResponse(afterOpponent, List(), true, None, RequestInfo(Map(), "", "", false)))

/** Ready with no possible move, so a bot passes, and fails the pass to end the game loop. */
private class PassFailsClient extends MockClient:
  override def pass: Try[StatusResponse] = Failure(IllegalStateException("pass sent"))
