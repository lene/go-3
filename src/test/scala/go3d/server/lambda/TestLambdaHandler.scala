package go3d.server.lambda

import com.amazonaws.services.lambda.runtime.events.{
  APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent
}
import go3d.Black
import go3d.server.{
  GameCreatedResponse, PlayerRegisteredResponse, StatusResponse, decodeGameCreatedResponse,
  decodePlayerRegisteredResponse, decodeStatusResponse
}
import go3d.server.service.{
  FailingGameStore, GameService, InMemoryGameArchive, InMemoryGameStore, InterleavingStore
}
import io.circe.parser.decode
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import scala.jdk.CollectionConverters._

private val GameId = "ABCDEF"
private val NewGamePath = "/new/3"

private def statusPath(gameId: String): String = "/status/" + gameId
private def registerPath(gameId: String, color: String): String =
  "/register/" + gameId + "/" + color
private def setCenterPath(gameId: String): String = "/set/" + gameId + "/2/2/2"
private def passPath(gameId: String): String = "/pass/" + gameId

private val StatusPath = statusPath(GameId)
private val OpenGamesPath = "/openGames"

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

  /** A new game on the in-memory store with both players: (game id, black token, white token). */
  private def startedGame(): (String, String, String) =
    val gameId = decode[GameCreatedResponse](call(NewGamePath).getBody).toTry.success.value.id
    val black = decode[PlayerRegisteredResponse](call(registerPath(gameId, "@")).getBody)
      .toTry.success.value.authToken
    val white = decode[PlayerRegisteredResponse](call(registerPath(gameId, "O")).getBody)
      .toTry.success.value.authToken
    (gameId, black, white)

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
    Assertions.assertTrue(resp.getBody.contains("ids"))

  @Test def testWritesWithoutDynamoDBReturn503(): Unit =
    Assertions.assertEquals(503, handler.handleRequest(request(NewGamePath), null).getStatusCode)
    Assertions.assertEquals(
      503, handler.handleRequest(withAuth(passPath(GameId), "Bearer T"), null).getStatusCode
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

  @Test def testStatusStoreFailureReturns500(): Unit =
    val resp = failingHandler.handleRequest(request(StatusPath), null)
    Assertions.assertEquals(500, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("internal error"))

  @Test def testOpenGamesStoreFailureReturns500(): Unit =
    Assertions.assertEquals(
      500, failingHandler.handleRequest(request(OpenGamesPath), null).getStatusCode
    )

  @Test def testNewGameReturnsValidId(): Unit =
    val resp = call("/new/5")
    Assertions.assertEquals(200, resp.getStatusCode)
    val created = decode[GameCreatedResponse](resp.getBody).toTry.success.value
    Assertions.assertEquals(5, created.size)
    Assertions.assertTrue(go3d.server.IdGenerator.isValidId(created.id))

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

  @Test def testTwoPassesEndGameAndLaterMovesReturn410(): Unit =
    val (gameId, black, white) = startedGame()
    Assertions.assertEquals(200, callWithToken(passPath(gameId), black).getStatusCode)
    val resp = callWithToken(passPath(gameId), white)
    Assertions.assertTrue(decode[StatusResponse](resp.getBody).toTry.success.value.over)
    Assertions.assertEquals(410, callWithToken(passPath(gameId), black).getStatusCode)

  @Test def testConcurrentMoveReturns409(): Unit =
    val (gameId, black, _) = startedGame()
    val stored = store.getGame(gameId).success.value
    val racing = InterleavingStore(store, () =>
      stored.foreach(s => store.updateGame(gameId, s.version, s.game).success.value)
    )
    val racingHandler = new LambdaHandler(Some(GameService(racing, InMemoryGameArchive(false))))
    val resp = racingHandler.handleRequest(
      withAuth(setCenterPath(gameId), "Bearer " + black), null
    )
    Assertions.assertEquals(409, resp.getStatusCode)

  @Test def testStatusMapping(): Unit =
    Assertions.assertEquals(500, LambdaHandler.statusOf(IllegalStateException("x")))
    Assertions.assertEquals(404, LambdaHandler.statusOf(NoSuchElementException("x")))
