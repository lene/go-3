package go3d.server.lambda

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import org.junit.jupiter.api.{Assertions, Test}

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
    val resp = handler.handleRequest(request("/status/ABCDEF"), null)
    Assertions.assertEquals(404, resp.getStatusCode)

  @Test def testOpenGamesWithoutDynamoDB(): Unit =
    val resp = handler.handleRequest(request("/openGames"), null)
    Assertions.assertEquals(200, resp.getStatusCode)
    Assertions.assertTrue(resp.getBody.contains("ids"))

  @Test def testUnknownPathReturns404(): Unit =
    val resp = handler.handleRequest(request("/unknown"), null)
    Assertions.assertEquals(404, resp.getStatusCode)

  @Test def testNullPathReturns404(): Unit =
    val resp = handler.handleRequest(request(null), null)
    Assertions.assertEquals(404, resp.getStatusCode)
