package go3d.server.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.typesafe.scalalogging.LazyLogging
import go3d.server.{NullRequestInfo, OpenGamesResponse, StatusResponse}
import go3d.server.aws.{DynamoDBGamesRepository, DynamoDBPlayersRepository}
import go3d.server.given  // Circe encoders from Jsonify.scala
import io.circe.syntax._

import scala.jdk.CollectionConverters._

/**
 * AWS Lambda handler for read-only game endpoints.
 *
 * Routes (Phase 5 — read-only):
 *   GET /health          → 200 "1"
 *   GET /status/{gameId} → 200 StatusResponse (from DynamoDB) or 404
 *   GET /openGames       → 200 OpenGamesResponse (from DynamoDB)
 *
 * When DynamoDB is not configured (no AWS_REGION env var), all reads return
 * empty/not-found responses rather than failing.
 */
class LambdaHandler
  extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent]
  with LazyLogging:

  private val StatusPath = "/status/(.+)".r

  def handleRequest(
    event: APIGatewayProxyRequestEvent,
    context: Context
  ): APIGatewayProxyResponseEvent =
    val path = Option(event.getPath).getOrElse("/")
    logger.info(s"Lambda request: ${event.getHttpMethod} $path")
    path match
      case "/health" =>
        jsonResponse(200, "1")
      case StatusPath(gameId) =>
        DynamoDBGamesRepository.get(gameId) match
          case Some(saveGame) =>
            val game = saveGame.game
            val status = StatusResponse(game, List(), false, game.isOver, None, NullRequestInfo)
            jsonResponse(200, status.asJson.noSpaces)
          case None =>
            jsonResponse(404, s"""{"error":"Game $gameId not found"}""")
      case "/openGames" =>
        val ids = DynamoDBPlayersRepository.openGames()
        jsonResponse(200, OpenGamesResponse(ids).asJson.noSpaces)
      case _ =>
        jsonResponse(404, s"""{"error":"Not found: $path"}""")

  private def jsonResponse(statusCode: Int, body: String): APIGatewayProxyResponseEvent =
    val resp = new APIGatewayProxyResponseEvent()
    resp.setStatusCode(statusCode)
    resp.setBody(body)
    resp.setHeaders(Map("Content-Type" -> "application/json").asJava)
    resp
