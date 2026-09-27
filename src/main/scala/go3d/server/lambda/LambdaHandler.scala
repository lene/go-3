package go3d.server.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.typesafe.scalalogging.LazyLogging
import go3d.server.{NullRequestInfo, OpenGamesResponse, StatusResponse}
import go3d.server.service.{DynamoDBGameStore, GameStore, StoredGame}
import go3d.server.given  // Circe encoders from Jsonify.scala
import io.circe.syntax._

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * AWS Lambda handler for read-only game endpoints.
 *
 * Routes (Phase 5 — read-only):
 *   GET /health          → 200 "1"
 *   GET /status/{gameId} → 200 StatusResponse, or 404 if the game does not exist
 *   GET /openGames       → 200 OpenGamesResponse
 * A failing store read returns 500, so it shows in the API Gateway and Lambda error metrics.
 *
 * @param store the games; None when DynamoDB is not configured (no AWS_REGION), in which case
 *              every game is not found and there are no open games
 */
class LambdaHandler(store: Option[GameStore])
  extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent]
  with LazyLogging:

  /** Used by the Lambda runtime: the DynamoDB tables configured in the environment. */
  def this() = this(DynamoDBGameStore.fromEnv())

  private val StatusPath = "/status/(.+)".r

  def handleRequest(
    event: APIGatewayProxyRequestEvent,
    context: Context
  ): APIGatewayProxyResponseEvent =
    val path = Option(event.getPath).getOrElse("/")
    logger.info("Lambda request: " + Option(event.getHttpMethod).getOrElse("") + " " + path)
    path match
      case "/health" =>
        jsonResponse(200, "1")
      case StatusPath(gameId) =>
        store.fold[Try[Option[StoredGame]]](Success(None))(_.getGame(gameId)) match
          case Success(Some(stored)) =>
            val game = stored.game
            val status = StatusResponse(game, List(), false, game.isOver, None, NullRequestInfo)
            jsonResponse(200, status.asJson.noSpaces)
          case Success(None) =>
            jsonResponse(404, "{\"error\":\"Game " + gameId + " not found\"}")
          case Failure(e) => serverError("reading game " + gameId, e)
      case "/openGames" =>
        store.fold[Try[Array[String]]](Success(Array.empty))(_.openGames()) match
          case Success(ids) => jsonResponse(200, OpenGamesResponse(ids).asJson.noSpaces)
          case Failure(e) => serverError("reading open games", e)
      case _ =>
        jsonResponse(404, "{\"error\":\"Not found: " + path + "\"}")

  private def serverError(operation: String, e: Throwable): APIGatewayProxyResponseEvent =
    logger.error(operation + " failed", e)
    jsonResponse(500, "{\"error\":\"internal error\"}")

  private def jsonResponse(statusCode: Int, body: String): APIGatewayProxyResponseEvent =
    val resp = new APIGatewayProxyResponseEvent()
    resp.setStatusCode(statusCode)
    resp.setBody(body)
    resp.setHeaders(Map("Content-Type" -> "application/json").asJava)
    resp
