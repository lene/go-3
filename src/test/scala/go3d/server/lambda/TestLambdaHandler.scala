package go3d.server.lambda

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import go3d.Black
import go3d.server.service.{FailingGameStore, InMemoryGameStore}
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

private val GameId = "ABCDEF"
private val StatusPath = "/status/" + GameId
private val OpenGamesPath = "/openGames"

// The AWS Lambda API allows a null Context and LambdaHandler does not use it;
// testNullPathReturns404 passes a null path on purpose.
@SuppressWarnings(Array("org.wartremover.warts.Null"))
class TestLambdaHandler:

  private val handler = new LambdaHandler()

  private def request(path: String, method: String = "GET"): APIGatewayProxyRequestEvent =
    new APIGatewayProxyRequestEvent().withPath(path).withHttpMethod(method)

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

  @Test def testUnknownPathReturns404(): Unit =
    val resp = handler.handleRequest(request("/unknown"), null)
    Assertions.assertEquals(404, resp.getStatusCode)

  @Test def testNullPathReturns404(): Unit =
    val resp = handler.handleRequest(request(null), null)
    Assertions.assertEquals(404, resp.getStatusCode)

  @Test def testStatusOfStoredGame(): Unit =
    val store = InMemoryGameStore()
    store.createGame(GameId, go3d.Game.start(3).success.value).success.value
    val resp = new LambdaHandler(Some(store)).handleRequest(request(StatusPath), null)
    Assertions.assertEquals(200, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("\"game\""))

  @Test def testStatusOfMissingGameInStoreReturns404(): Unit =
    val resp = new LambdaHandler(Some(InMemoryGameStore()))
      .handleRequest(request(StatusPath), null)
    Assertions.assertEquals(404, resp.getStatusCode)

  @Test def testOpenGamesFromStore(): Unit =
    val store = InMemoryGameStore()
    store.createGame(GameId, go3d.Game.start(3).success.value).success.value
    store.registerPlayer(GameId, Black, "hash").success.value
    val resp = new LambdaHandler(Some(store)).handleRequest(request(OpenGamesPath), null)
    Assertions.assertEquals(200, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains(GameId))

  @Test def testStatusStoreFailureReturns500(): Unit =
    val resp = new LambdaHandler(Some(FailingGameStore()))
      .handleRequest(request(StatusPath), null)
    Assertions.assertEquals(500, resp.getStatusCode)

  @Test def testOpenGamesStoreFailureReturns500(): Unit =
    val resp = new LambdaHandler(Some(FailingGameStore()))
      .handleRequest(request(OpenGamesPath), null)
    Assertions.assertEquals(500, resp.getStatusCode)
