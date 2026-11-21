package go3d.server.lambda

import com.amazonaws.services.lambda.runtime.events.{
  APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent
}
import go3d.{BadKomiTexts, Black, Color, White}
import go3d.server.{
  GameCreatedResponse, OpenGame, OpenGamesResponse, PlayerRegisteredResponse, StatusResponse,
  decodeGameCreatedResponse, decodeOpenGamesResponse, decodePlayerRegisteredResponse,
  decodeStatusResponse
}
import go3d.server.service.{
  FailingGameStore, GamePositions, GameService, InMemoryGameArchive, InMemoryGameStore,
  InterleavingStore, StatusAssertions, StoredGame
}
import io.circe.Json
import io.circe.parser.{decode, parse}
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

private val GameId = "ABCDEF"
private val NewGamePath = "/new/3"

private def statusPath(gameId: String): String = "/status/" + gameId
private def registerPath(gameId: String, color: String): String =
  "/register/" + gameId + "/" + color
private def setCenterPath(gameId: String): String = "/set/" + gameId + "/2/2/2"
private def passPath(gameId: String): String = "/pass/" + gameId
private def replayPath(gameId: String, moveCount: Int): String =
  statusPath(gameId) + "/" + moveCount.toString
private def archivedPath(gameId: String): String = "/archived/" + gameId

private val StatusPath = statusPath(GameId)
private val OpenGamesPath = "/openGames"
private val NoOpenGamesJson = """{"ids": [], "games": []}"""

// The AWS Lambda API allows a null Context and LambdaHandler does not use it;
// testNullPathReturns404 passes a null path on purpose.
@SuppressWarnings(Array("org.wartremover.warts.Null"))
class TestLambdaHandler:

  private val handler = new LambdaHandler()
  private val store = InMemoryGameStore()
  private val storeHandler = new LambdaHandler(Some(GameService(store, InMemoryGameArchive(false))))

  private def failingHandler: LambdaHandler =
    new LambdaHandler(Some(GameService(FailingGameStore(), InMemoryGameArchive(false))))

  private def request(path: String, method: String = "GET"): APIGatewayProxyRequestEvent =
    new APIGatewayProxyRequestEvent().withPath(path).withHttpMethod(method)

  private def withAuth(path: String, header: String): APIGatewayProxyRequestEvent =
    request(path).withHeaders(Map("Authentication" -> header).asJava)

  private def call(path: String): APIGatewayProxyResponseEvent =
    storeHandler.handleRequest(request(path), null)

  private def callWithToken(path: String, token: String): APIGatewayProxyResponseEvent =
    storeHandler.handleRequest(withAuth(path, "Bearer " + token), null)

  private def withKomiValues(path: String, values: List[String]): APIGatewayProxyRequestEvent =
    request(path).withMultiValueQueryStringParameters(Map("komi" -> values.asJava).asJava)

  private def newGameWithKomiValues(values: List[String]): APIGatewayProxyResponseEvent =
    storeHandler.handleRequest(withKomiValues(NewGamePath, values), null)

  /** A new game on the in-memory store with both players: (game id, black token, white token). */
  private def startedGame(): (String, String, String) = startedGameOfSize(3)

  private def startedGameOfSize(size: Int): (String, String, String) =
    startedGameWithKomiValues(size, None)

  private def startedGameWithKomi(komi: String): (String, String, String) =
    startedGameWithKomiValues(3, Some(List(komi)))

  private def startedGameWithKomiValues(
      size: Int,
      komiValues: Option[List[String]]
  ): (String, String, String) =
    val path = "/new/" + size.toString
    val created = komiValues.fold(call(path))(values =>
      storeHandler.handleRequest(withKomiValues(path, values), null)
    )
    val gameId = decode[GameCreatedResponse](created.getBody).toTry.success.value.id
    val black = decode[PlayerRegisteredResponse](call(registerPath(gameId, "@")).getBody)
      .toTry.success.value.authToken
    val white = decode[PlayerRegisteredResponse](call(registerPath(gameId, "O")).getBody)
      .toTry.success.value.authToken
    (gameId, black, white)

  private def setPath(gameId: String, position: (Int, Int, Int)): String =
    val (x, y, z) = position
    "/set/" + gameId + "/" + x.toString + "/" + y.toString + "/" + z.toString

  /** Plays the positions in order, black first, alternating. */
  private def playPositions(
      gameId: String,
      black: String,
      white: String,
      positions: List[GamePositions.Position]
  ): Unit =
    for (token, position) <- GamePositions.withTokens(black, white, positions) do
      Assertions.assertEquals(200, callWithToken(setPath(gameId, position), token).getStatusCode)

  @Test def testHealthReturns200(): Unit =
    val resp = handler.handleRequest(request("/health"), null)
    Assertions.assertEquals(200, resp.getStatusCode)
    Assertions.assertEquals("1", resp.getBody)

  @Test def testHealthHasJsonContentType(): Unit =
    val resp = handler.handleRequest(request("/health"), null)
    Assertions.assertEquals("application/json", resp.getHeaders.get("Content-Type"))

  @Test def testStatusNotFoundWithoutDynamoDB(): Unit =
    val resp = handler.handleRequest(request(StatusPath), null)
    Assertions.assertEquals(404, resp.getStatusCode)

  @Test def testOpenGamesWithoutDynamoDB(): Unit =
    val resp = handler.handleRequest(request(OpenGamesPath), null)
    Assertions.assertEquals(200, resp.getStatusCode)
    Assertions.assertEquals(parse(NoOpenGamesJson), parse(resp.getBody))

  @Test def testWritesWithoutDynamoDBReturn503(): Unit =
    Assertions.assertEquals(503, handler.handleRequest(request(NewGamePath), null).getStatusCode)
    Assertions.assertEquals(
      503,
      handler.handleRequest(withAuth(passPath(GameId), "Bearer T"), null).getStatusCode
    )

  @Test def testUnknownPathReturns404(): Unit =
    val resp = handler.handleRequest(request("/unknown"), null)
    Assertions.assertEquals(404, resp.getStatusCode)

  @Test def testNullPathReturns404(): Unit =
    val resp = handler.handleRequest(request(null), null)
    Assertions.assertEquals(404, resp.getStatusCode)

  @Test def testMalformedPathReturns404(): Unit =
    Assertions.assertEquals(404, call("/status/%zz").getStatusCode)

  @Test def testErrorBodyIsJson(): Unit =
    val body = call("/unknown\"path").getBody
    Assertions.assertTrue(decode[io.circe.Json](body).isRight, body)

  @Test def testStatusOfStoredGame(): Unit =
    store.createGame(GameId, go3d.Game.start(3).success.value).success.value
    val resp = call(StatusPath)
    Assertions.assertEquals(200, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("\"game\""))

  @Test def testStatusOfMissingGameInStoreReturns404(): Unit =
    Assertions.assertEquals(404, call(StatusPath).getStatusCode)

  @Test def testOpenGamesFromStore(): Unit =
    store.createGame(GameId, go3d.Game.start(3).success.value).success.value
    store.registerPlayer(GameId, Black, "hash").success.value
    val resp = call(OpenGamesPath)
    Assertions.assertEquals(200, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains(GameId))
    Assertions.assertEquals(List(OpenGame(GameId, 0.5)), openGames().games.toList)

  @Test def testStatusStoreFailureReturns500(): Unit =
    val resp = failingHandler.handleRequest(request(StatusPath), null)
    Assertions.assertEquals(500, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("internal error"))

  @Test def testOpenGamesStoreFailureReturns500(): Unit =
    Assertions.assertEquals(
      500,
      failingHandler.handleRequest(request(OpenGamesPath), null).getStatusCode
    )

  private def openGames(): OpenGamesResponse =
    decode[OpenGamesResponse](call(OpenGamesPath).getBody).toTry.success.value

  /** A new game with only Black registered; `komiValues` are the values of the `komi` query, if
    * the request has one.
    */
  private def openGame(komiValues: Option[List[String]]): String =
    val created = komiValues.fold(call(NewGamePath))(newGameWithKomiValues)
    val gameId = decode[GameCreatedResponse](created.getBody).toTry.success.value.id
    Assertions.assertEquals(200, call(registerPath(gameId, "@")).getStatusCode)
    gameId

  @Test def testOpenGamesCarryTheKomiOfEachGame(): Unit =
    val withoutKomi = openGame(None)
    val large = openGame(Some(List("6.5")))
    val negative = openGame(Some(List("-0.5")))
    Assertions.assertEquals(
      Set(OpenGame(withoutKomi, 0.5), OpenGame(large, 6.5), OpenGame(negative, -0.5)),
      openGames().games.toSet
    )

  @Test def testOpenGamesListIdsAndGamesInTheSameOrder(): Unit =
    for komi <- List("0.5", "6.5", "-0.5", "1.5") do openGame(Some(List(komi)))
    val body = call(OpenGamesPath).getBody
    val json = parse(body).toTry.success.value.hcursor
    val ids = json.downField("ids").as[List[String]].toTry.success.value
    val gameIds = json.downField("games").as[List[Json]].toTry.success.value
      .map(_.hcursor.downField("id").as[String].toTry.success.value)
    Assertions.assertEquals(4, ids.length, body)
    Assertions.assertEquals(ids, gameIds, body)

  @Test def testOpenGamesLeaveOutGamesWithBothOrNoPlayers(): Unit =
    val open = openGame(Some(List("6.5")))
    val (full, _, _) = startedGameWithKomi("6.5")
    val noPlayers = decode[GameCreatedResponse](call(NewGamePath).getBody).toTry.success.value.id
    val response = openGames()
    Assertions.assertEquals(List(OpenGame(open, 6.5)), response.games.toList)
    Assertions.assertFalse(response.ids.contains(full))
    Assertions.assertFalse(response.ids.contains(noPlayers))

  @Test def testNoOpenGamesListEmptyArrays(): Unit =
    call(NewGamePath)
    Assertions.assertEquals(parse(NoOpenGamesJson), parse(call(OpenGamesPath).getBody))

  @Test def testOpenGamesLeaveOutAGameThatIsGoneBeforeTheRead(): Unit =
    val gone = openGame(Some(List("6.5")))
    val kept = openGame(None)
    store.removeGame(gone)
    Assertions.assertTrue(store.openGames().success.value.contains(gone))
    Assertions.assertEquals(List(OpenGame(kept, 0.5)), openGames().games.toList)
    Assertions.assertEquals(List(kept), openGames().ids.toList)

  @Test def testOpenGamesFailWith500IfAnOpenGameCannotBeRead(): Unit =
    val unreadable = new InMemoryGameStore:
      override def getGame(gameId: String): Try[Option[StoredGame]] =
        Failure(IllegalStateException("store unavailable"))
    unreadable.createGame(GameId, go3d.Game.start(3).success.value).success.value
    unreadable.registerPlayer(GameId, Black, "hash").success.value
    val unreadableHandler =
      new LambdaHandler(Some(GameService(unreadable, InMemoryGameArchive(false))))
    val resp = unreadableHandler.handleRequest(request(OpenGamesPath), null)
    Assertions.assertEquals(500, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("internal error"))

  @Test def testNewGameReturnsValidId(): Unit =
    val resp = call("/new/5")
    Assertions.assertEquals(200, resp.getStatusCode)
    val created = decode[GameCreatedResponse](resp.getBody).toTry.success.value
    Assertions.assertEquals(5, created.size)
    Assertions.assertTrue(go3d.server.IdGenerator.isValidId(created.id))

  private def statusOfGame(gameId: String): StatusResponse =
    decode[StatusResponse](call(statusPath(gameId)).getBody).toTry.success.value

  @Test def testNewGameWithoutKomiHasDefaultKomi(): Unit =
    val (gameId, _, _) = startedGame()
    Assertions.assertEquals(0.5, statusOfGame(gameId).game.komi)

  @Test def testNewGameWithKomiHasThatKomi(): Unit =
    for komi <- List("6.5", "-0.5", "26.5", "6.50") do
      val gameId = decode[GameCreatedResponse](newGameWithKomiValues(List(komi)).getBody)
        .toTry.success.value.id
      Assertions.assertEquals(komi.toDouble, statusOfGame(gameId).game.komi)

  @Test def testKomiIsInRegisterResponseAndStatusJson(): Unit =
    val gameId = decode[GameCreatedResponse](newGameWithKomiValues(List("6.5")).getBody)
      .toTry.success.value.id
    val registered = call(registerPath(gameId, "@")).getBody
    Assertions.assertEquals(
      6.5,
      decode[PlayerRegisteredResponse](registered).toTry.success.value.game.komi
    )
    Assertions.assertTrue(call(statusPath(gameId)).getBody.contains("\"komi\":6.5"))

  @Test def testUnrelatedQueryParametersAreIgnored(): Unit =
    val event = request(NewGamePath)
      .withMultiValueQueryStringParameters(Map("other" -> List("x").asJava).asJava)
    val resp = storeHandler.handleRequest(event, null)
    Assertions.assertEquals(200, resp.getStatusCode)
    val gameId = decode[GameCreatedResponse](resp.getBody).toTry.success.value.id
    Assertions.assertEquals(0.5, statusOfGame(gameId).game.komi)

  @Test def testNewGameWithBadKomiReturns400AndCreatesNoGame(): Unit =
    val emptyStore = InMemoryGameStore()
    val emptyStoreHandler =
      new LambdaHandler(Some(GameService(emptyStore, InMemoryGameArchive(false))))
    for text <- BadKomiTexts do
      val resp = emptyStoreHandler.handleRequest(withKomiValues(NewGamePath, List(text)), null)
      Assertions.assertEquals(400, resp.getStatusCode, "[" + text + "]")
      Assertions.assertTrue(resp.getBody.contains("BadKomi"), resp.getBody)
    Assertions.assertEquals(0, emptyStore.gameCount)

  @Test def testBadKomiErrorDoesNotEchoInput(): Unit =
    val resp = newGameWithKomiValues(List("secret-input-123"))
    Assertions.assertEquals(400, resp.getStatusCode)
    Assertions.assertFalse(resp.getBody.contains("secret-input-123"), resp.getBody)
    Assertions.assertTrue(decode[io.circe.Json](resp.getBody).isRight, resp.getBody)

  @Test def testNewGameWithBareKomiParameterReturns400(): Unit =
    Assertions.assertEquals(400, newGameWithKomiValues(List("")).getStatusCode)
    Assertions.assertEquals(400, newGameWithKomiValues(List()).getStatusCode)

  @Test def testNewGameWithRepeatedKomiParameterReturns400(): Unit =
    Assertions.assertEquals(400, newGameWithKomiValues(List("6.5", "6.5")).getStatusCode)
    Assertions.assertEquals(400, newGameWithKomiValues(List("6.5", "7.5")).getStatusCode)

  @Test def testNewGameWithKomiBeyondBoardPointsReturns400(): Unit =
    Assertions.assertEquals(200, newGameWithKomiValues(List("26.5")).getStatusCode)
    Assertions.assertEquals(400, newGameWithKomiValues(List("27.5")).getStatusCode)

  @Test def testNewGameWithBadSizeAndKomiReturns400(): Unit =
    val resp = storeHandler.handleRequest(withKomiValues("/new/4", List("7")), null)
    Assertions.assertEquals(400, resp.getStatusCode)

  @Test def testKomiSurvivesMovesAndReplay(): Unit =
    val (gameId, black, white) = startedGameWithKomi("6.5")
    Assertions.assertEquals(200, callWithToken(setCenterPath(gameId), black).getStatusCode)
    val set = callWithToken(setPath(gameId, (1, 1, 1)), white)
    Assertions.assertEquals(6.5, decode[StatusResponse](set.getBody).toTry.success.value.game.komi)
    val passed = callWithToken(passPath(gameId), black)
    Assertions.assertEquals(
      6.5,
      decode[StatusResponse](passed.getBody).toTry.success.value.game.komi
    )
    for count <- 0 to 3 do
      val replayed = decode[StatusResponse](call(replayPath(gameId, count)).getBody)
      Assertions.assertEquals(6.5, replayed.toTry.success.value.game.komi)

  /** Plays the capture-free game that black leads by one point on a board of size 3 and ends it,
    * and checks that only the end of the game names the winner.
    */
  private def assertWinnerOnlyWhenOver(komiValues: Option[List[String]], winner: Color): Unit =
    val (gameId, black, white) = startedGameWithKomiValues(3, komiValues)
    val setResponses = GamePositions.withTokens(black, white, GamePositions.BlackLeadsByOne)
      .map((token, position) => callWithToken(setPath(gameId, position), token))
    val passes = List(white, black).map(token => callWithToken(passPath(gameId), token))
    val responses = setResponses ++ passes
    Assertions.assertTrue(responses.forall(_.getStatusCode == 200))
    StatusAssertions.assertWinnerOnlyWhenOver(
      responses.map(_.getBody),
      List(call(statusPath(gameId)).getBody, callWithToken(statusPath(gameId), black).getBody),
      count => call(replayPath(gameId, count)).getBody,
      winner
    )

  @Test def testWinnerWithoutKomiIsBlack(): Unit =
    assertWinnerOnlyWhenOver(None, Black)

  @Test def testWinnerWithKomiHalfIsBlack(): Unit =
    assertWinnerOnlyWhenOver(Some(List("0.5")), Black)

  @Test def testWinnerWithLargeKomiIsWhite(): Unit =
    assertWinnerOnlyWhenOver(Some(List("6.5")), White)

  @Test def testWinnerWithNegativeKomiIsBlack(): Unit =
    assertWinnerOnlyWhenOver(Some(List("-0.5")), Black)

  @Test def testStatusOfGameInProgressHasNoWinner(): Unit =
    val (gameId, black, _) = startedGameWithKomi("6.5")
    StatusAssertions.assertNotOver(call(statusPath(gameId)).getBody)
    StatusAssertions.assertNotOver(callWithToken(statusPath(gameId), black).getBody)

  @Test def testNewGameWithBadSizeReturns400(): Unit =
    Assertions.assertEquals(400, call("/new/4").getStatusCode)

  @Test def testRegisterNonexistentGameReturns404(): Unit =
    Assertions.assertEquals(404, call("/register/NOGAME/@").getStatusCode)

  @Test def testRegisterDuplicateColorReturns400(): Unit =
    val (gameId, _, _) = startedGame()
    Assertions.assertEquals(400, call(registerPath(gameId, "@")).getStatusCode)

  @Test def testRegisterWithEncodedColor(): Unit =
    val gameId = decode[GameCreatedResponse](call(NewGamePath).getBody).toTry.success.value.id
    val resp = call(registerPath(gameId, "%40"))
    Assertions.assertEquals(200, resp.getStatusCode)
    val registered = decode[PlayerRegisteredResponse](resp.getBody).toTry.success.value
    Assertions.assertEquals(Black, registered.color)

  @Test def testStatusWithTokenShowsReadyPlayer(): Unit =
    val (gameId, black, _) = startedGame()
    val resp = callWithToken(statusPath(gameId), black)
    val status = decode[StatusResponse](resp.getBody).toTry.success.value
    Assertions.assertTrue(status.ready)
    Assertions.assertEquals(Some(Black), status.playerColor)

  @Test def testStatusWithInvalidTokenReturns401(): Unit =
    val (gameId, _, _) = startedGame()
    val resp = callWithToken(statusPath(gameId), "WRONG")
    Assertions.assertEquals(401, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("unauthorized"))

  @Test def testSetStoresMove(): Unit =
    val (gameId, black, _) = startedGame()
    Assertions.assertEquals(200, callWithToken(setCenterPath(gameId), black).getStatusCode)
    val status = decode[StatusResponse](call(statusPath(gameId)).getBody).toTry.success.value
    Assertions.assertEquals(Black, status.game.at(2, 2, 2))

  @Test def testSetWithoutTokenReturns401(): Unit =
    val (gameId, _, _) = startedGame()
    Assertions.assertEquals(401, call(setCenterPath(gameId)).getStatusCode)

  @Test def testSetWithWrongAuthMethodReturns401(): Unit =
    val (gameId, black, _) = startedGame()
    val resp =
      storeHandler.handleRequest(withAuth(setCenterPath(gameId), "Basic " + black), null)
    Assertions.assertEquals(401, resp.getStatusCode)

  @Test def testSetOnWrongTurnReturns400(): Unit =
    val (gameId, _, white) = startedGame()
    Assertions.assertEquals(400, callWithToken(setCenterPath(gameId), white).getStatusCode)

  @Test def testSetOutsideBoardReturns400(): Unit =
    val (gameId, black, _) = startedGame()
    Assertions.assertEquals(400, callWithToken("/set/" + gameId + "/4/1/1", black).getStatusCode)

  @Test def testSetWithWrongTokenReturns401(): Unit =
    val (gameId, _, _) = startedGame()
    val resp = callWithToken(setCenterPath(gameId), "WRONG")
    Assertions.assertEquals(401, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("unauthorized"))

  @Test def testSetOnOccupiedPositionReturns400(): Unit =
    val (gameId, black, white) = startedGame()
    Assertions.assertEquals(200, callWithToken(setCenterPath(gameId), black).getStatusCode)
    val resp = callWithToken(setCenterPath(gameId), white)
    Assertions.assertEquals(400, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("PositionOccupied"))

  @Test def testSuicideReturns400(): Unit =
    val (gameId, black, white) = startedGame()
    playPositions(gameId, black, white, GamePositions.BeforeSuicide)
    val resp = callWithToken(setPath(gameId, (1, 1, 1)), black)
    Assertions.assertEquals(400, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("Suicide"))

  @Test def testKoReturns400(): Unit =
    val (gameId, black, white) = startedGameOfSize(5)
    playPositions(gameId, black, white, GamePositions.BeforeKo)
    val resp = callWithToken(setPath(gameId, (2, 2, 3)), black)
    Assertions.assertEquals(400, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("Ko"))

  @Test def testTwoPassesEndGameAndLaterMovesReturn410(): Unit =
    val (gameId, black, white) = startedGame()
    Assertions.assertEquals(200, callWithToken(passPath(gameId), black).getStatusCode)
    val resp = callWithToken(passPath(gameId), white)
    Assertions.assertTrue(decode[StatusResponse](resp.getBody).toTry.success.value.over)
    Assertions.assertEquals(410, callWithToken(passPath(gameId), black).getStatusCode)

  @Test def testConcurrentMoveReturns409(): Unit =
    val (gameId, black, _) = startedGame()
    val stored = store.getGame(gameId).success.value
    val racing = InterleavingStore(
      store,
      () =>
        stored.foreach(s => store.updateGame(gameId, s.version, s.game).success.value)
    )
    val racingHandler = new LambdaHandler(Some(GameService(racing, InMemoryGameArchive(false))))
    val resp = racingHandler.handleRequest(
      withAuth(setCenterPath(gameId), "Bearer " + black),
      null
    )
    Assertions.assertEquals(409, resp.getStatusCode)

  @Test def testRegisterEmptyColorReturns400(): Unit =
    val gameId = decode[GameCreatedResponse](call(NewGamePath).getBody).toTry.success.value.id
    Assertions.assertEquals(400, call(registerPath(gameId, "%20")).getStatusCode)

  @Test def testStatusAtMoveReplaysGame(): Unit =
    val (gameId, black, _) = startedGame()
    Assertions.assertEquals(200, callWithToken(setCenterPath(gameId), black).getStatusCode)
    val before = call(replayPath(gameId, 0))
    Assertions.assertEquals(200, before.getStatusCode)
    val empty = decode[StatusResponse](before.getBody).toTry.success.value
    Assertions.assertEquals(go3d.Empty, empty.game.at(2, 2, 2))
    val after = decode[StatusResponse](call(replayPath(gameId, 1)).getBody).toTry.success.value
    Assertions.assertEquals(Black, after.game.at(2, 2, 2))

  @Test def testStatusAtMoveOfMissingGameReturns404(): Unit =
    Assertions.assertEquals(404, call(replayPath(GameId, 1)).getStatusCode)
    Assertions.assertEquals(
      404,
      handler.handleRequest(request(replayPath(GameId, 1)), null).getStatusCode
    )

  @Test def testArchivedFinishedGameRedirects(): Unit =
    val (gameId, black, white) = startedGame()
    Assertions.assertEquals(200, callWithToken(passPath(gameId), black).getStatusCode)
    Assertions.assertEquals(200, callWithToken(passPath(gameId), white).getStatusCode)
    val resp = call(archivedPath(gameId))
    Assertions.assertEquals(302, resp.getStatusCode)
    Assertions.assertTrue(resp.getHeaders.get("Location").contains(gameId))

  @Test def testArchivedRunningGameReturns404(): Unit =
    val (gameId, _, _) = startedGame()
    Assertions.assertEquals(404, call(archivedPath(gameId)).getStatusCode)
    Assertions.assertEquals(
      404,
      handler.handleRequest(request(archivedPath(gameId)), null).getStatusCode
    )

  @Test def testArchivedWithFailingArchiveReturns500(): Unit =
    val failing = new LambdaHandler(Some(GameService(store, InMemoryGameArchive(true))))
    Assertions.assertEquals(
      500,
      failing.handleRequest(request(archivedPath(GameId)), null).getStatusCode
    )

  @Test def testStatusMapping(): Unit =
    Assertions.assertEquals(500, LambdaHandler.statusOf(IllegalStateException("x")))
    Assertions.assertEquals(400, LambdaHandler.statusOf(go3d.IllegalMove("x")))
    Assertions.assertEquals(404, LambdaHandler.statusOf(new NoSuchElementException("x")))
