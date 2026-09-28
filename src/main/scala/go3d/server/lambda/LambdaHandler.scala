package go3d.server.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.typesafe.scalalogging.LazyLogging
import go3d.{BadBoardSize, BadColor, Color, GameOver, GoException, IllegalMove}
import go3d.server.{
  AuthorizationError, AuthorizationMethodWrong, AuthorizationMissing, DuplicateColor, GoResponse,
  IdGenerator, NonexistentGame, NotReadyToSet, OpenGamesResponse, ServerException,
  encodeGoResponse
}
import go3d.server.service.{
  ConcurrentModification, DynamoDBGameStore, GameService, S3GameArchive, UnconfiguredArchive
}
import io.circe.Json
import io.circe.syntax._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/** The server has no game store configured, so it cannot accept writes. */
class ServiceUnavailable extends ServerException("game store is not configured")

/**
 * AWS Lambda handler for the game API, on top of [[GameService]]. The routes and responses are
 * those of the http4s server (`http4s/GoHttpService.scala`):
 *
 *   GET /health                    → 200 1
 *   GET /openGames                 → OpenGamesResponse
 *   GET /new/{size}                → GameCreatedResponse
 *   GET /register/{gameId}/{color} → PlayerRegisteredResponse with the bearer token
 *   GET /status/{gameId}           → StatusResponse; with a bearer token also moves and `ready`
 *   GET /status/{gameId}/{n}       → StatusResponse of the game after its first n moves
 *   GET /archived/{gameId}         → 302 to a pre-signed S3 URL of the finished game's archive
 *   GET /set/{gameId}/{x}/{y}/{z}  → StatusResponse (bearer token required)
 *   GET /pass/{gameId}             → StatusResponse (bearer token required)
 *
 * The token is sent as `Authentication: Bearer <token>`. Errors map to the http4s statuses (see
 * [[LambdaHandler.statusOf]]); a move that lost a race with another request returns 409 and the
 * client should refetch the status.
 *
 * @param service None when DynamoDB is not configured: reads then find no games and writes
 *                return 503
 */
class LambdaHandler(service: Option[GameService])
  extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent]
  with LazyLogging:

  /** Used by the Lambda runtime: DynamoDB and S3 as configured in the environment. */
  def this() = this(LambdaHandler.serviceFromEnv())

  def handleRequest(
    event: APIGatewayProxyRequestEvent,
    context: Context
  ): APIGatewayProxyResponseEvent =
    val path = Option(event.getPath).getOrElse("/")
    logger.info("Lambda request: " + Option(event.getHttpMethod).getOrElse("") + " " + path)
    val token = bearerToken(event)
    segments(path) match
      case List("health") => jsonResponse(200, "1")
      case List("openGames") =>
        respond(service.fold[Try[GoResponse]](Success(OpenGamesResponse(Array.empty)))(
          _.openGames().map(OpenGamesResponse(_))
        ))
      case List("new", IntSegment(size)) => respond(writeService.flatMap(_.newGame(size)))
      case List("register", GameIdSegment(gameId), ColorSegment(color)) =>
        respond(writeService.flatMap(_.register(gameId, color)))
      case List("status", GameIdSegment(gameId)) =>
        respond(readService(gameId).flatMap(s => token.flatMap(t => s.status(gameId, t))))
      case List("status", GameIdSegment(gameId), IntSegment(moveCount)) =>
        respond(readService(gameId).flatMap(_.statusAt(gameId, moveCount)))
      case List("archived", GameIdSegment(gameId)) =>
        readService(gameId).flatMap(_.archivedUrl(gameId)) match
          case Success(Some(url)) => redirect(url)
          case Success(None) => errorResponse(404, "no archive of game " + gameId)
          case Failure(e) => respond(Failure(e))
      case List("set", GameIdSegment(gameId), IntSegment(x), IntSegment(y), IntSegment(z)) =>
        respond(requiredToken(token).flatMap(t => writeService.flatMap(_.set(gameId, t, x, y, z))))
      case List("pass", GameIdSegment(gameId)) =>
        respond(requiredToken(token).flatMap(t => writeService.flatMap(_.pass(gameId, t))))
      case _ => errorResponse(404, "Not found: " + path)

  private def writeService: Try[GameService] =
    service.fold[Try[GameService]](Failure(ServiceUnavailable()))(Success(_))

  /** Without a store there are no games, so reading one finds nothing. */
  private def readService(gameId: String): Try[GameService] =
    service.fold[Try[GameService]](Failure(NonexistentGame(gameId, List())))(Success(_))

  /**
   * The bearer token of the request: None without an `Authentication` header, a failure when the
   * header is not `Bearer <token>`.
   */
  private def bearerToken(event: APIGatewayProxyRequestEvent): Try[Option[String]] =
    val headers = Option(event.getHeaders).fold(Map.empty[String, String])(_.asScala.toMap)
    headers.collectFirst { case (name, value) if name.equalsIgnoreCase("Authentication") => value }
      .fold[Try[Option[String]]](Success(None)) { value =>
        value.trim.split("\\s+").toList match
          case List(method, token) if method.equalsIgnoreCase("Bearer") => Success(Some(token))
          case parts => Failure(AuthorizationMethodWrong(parts.headOption.getOrElse("")))
      }

  private def requiredToken(token: Try[Option[String]]): Try[String] =
    token.flatMap(_.fold[Try[String]](Failure(AuthorizationMissing(Map())))(Success(_)))

  private def respond(result: Try[GoResponse]): APIGatewayProxyResponseEvent =
    result match
      case Success(response) => jsonResponse(200, response.asJson.noSpaces)
      case Failure(e) =>
        val status = LambdaHandler.statusOf(e)
        if status >= 500 then logger.error("request failed", e)
        // authorization failures and unexpected errors do not echo details back to the client
        val message = e match
          case _: AuthorizationError => "unauthorized"
          case _: ServerException | _: GoException | _: NoSuchElementException =>
            e.getClass.getSimpleName + ": " + e.getMessage
          case _ => "internal error"
        errorResponse(status, message)

  private def errorResponse(statusCode: Int, message: String): APIGatewayProxyResponseEvent =
    jsonResponse(statusCode, Json.obj("error" -> Json.fromString(message)).noSpaces)

  private def redirect(url: String): APIGatewayProxyResponseEvent =
    val resp = new APIGatewayProxyResponseEvent()
    resp.setStatusCode(302)
    resp.setBody("")
    resp.setHeaders(Map("Location" -> url).asJava)
    resp

  private def jsonResponse(statusCode: Int, body: String): APIGatewayProxyResponseEvent =
    val resp = new APIGatewayProxyResponseEvent()
    resp.setStatusCode(statusCode)
    resp.setBody(body)
    resp.setHeaders(Map("Content-Type" -> "application/json").asJava)
    resp

  /** The URL-decoded path segments; none for a malformed path, which then matches no route. */
  private def segments(path: String): List[String] =
    Try(path.split('/').toList.filter(_.nonEmpty).map(URLDecoder.decode(_, StandardCharsets.UTF_8)))
      .getOrElse(List())

  private object IntSegment:
    def unapply(segment: String): Option[Int] = segment.toIntOption

  private object GameIdSegment:
    def unapply(segment: String): Option[String] = Some(segment).filter(IdGenerator.isValidId)

  private object ColorSegment:
    def unapply(segment: String): Option[Color] = segment.headOption.flatMap(Color(_).toOption)

object LambdaHandler:
  /** The service on the DynamoDB tables and S3 bucket in the environment, if DynamoDB is set up. */
  def serviceFromEnv(): Option[GameService] =
    DynamoDBGameStore.fromEnv().map(store =>
      GameService(store, S3GameArchive.fromEnv().getOrElse(UnconfiguredArchive))
    )

  /** The HTTP status for a failed request, as the http4s server's `BaseHandler` maps it. */
  def statusOf(e: Throwable): Int =
    e match
      case _: BadBoardSize | _: BadColor | _: DuplicateColor | _: NotReadyToSet => 400
      case _: NoSuchElementException | _: NonexistentGame => 404
      case _: AuthorizationError => 401
      case _: ConcurrentModification => 409
      case _: ServiceUnavailable => 503
      case _: ServerException => 500
      case _: IllegalMove => 400
      case _: GameOver => 410
      case _ => 500
