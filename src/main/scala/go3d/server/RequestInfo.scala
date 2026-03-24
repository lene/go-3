package go3d.server

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.GameOver
import org.http4s.Request

import scala.util.{Failure, Success, Try}

val NullRequestInfo = RequestInfo(Map(), "", "", false)

object RequestInfo:

  def apply(request: Request[IO], maxLength: Int=100): Try[RequestInfo] =
    val headers = request.headers.headers.map(h => (h.name.toString, h.value)).toMap
    fromRaw(
      headers, request.uri.query.toString,
      request.pathInfo.toString.split("/").tail.tail.mkString("/"), maxLength
    )

  private def fromRaw(
    headers: Map[String, String], queryString: String, rawPathInfo: String, maxLength: Int
  ): Try[RequestInfo] =
    val (pathInfo, debug) = parsePathInfo(rawPathInfo)
    if Option(pathInfo).exists(_.length > maxLength)
    then Failure(RequestTooLong(maxLength, pathInfo.length))
    else Success(RequestInfo(
      headers,
      Option(queryString).filter(_.nonEmpty).getOrElse("/"),
      Option(pathInfo).filter(_.nonEmpty).getOrElse("/"),
      debug
    ))

  private def parsePathInfo(pathInfo: String): (String, Boolean) =
    Option(pathInfo).filter(_.endsWith("/d"))
      .fold((pathInfo, false))(p => (p.dropRight(2), true))

case class RequestInfo(headers: Map[String, String], query: String, path: String, debug: Boolean)
  extends GoResponse with LazyLogging:
  def getGameId: Try[String] =
    if path.isEmpty then Failure(MalformedRequest(path))
    else
      val parts = path.stripPrefix("/").split('/')
      if parts.isEmpty then Failure(MalformedRequest(path))
      else
        val gameId = parts(0)
        if !Games.contains(gameId) then Failure(NonexistentGame(gameId, Games.activeGameIds.toList))
        else Success(gameId)

  def getPlayer: Option[Player] =
    (for
      token <- getToken
      gameId <- getGameId
      players <- Players.get(gameId).toRight(GameOver(Games(gameId))).toTry
      color <- Tokens.getColor(token, gameId).toRight(PlayerNotFoundByToken(gameId, token)).toTry
      player <- players.get(color).toRight(PlayerNotFoundByToken(gameId, token)).toTry
    yield player).toOption

  def mustGetPlayer: Try[Player] =
    for
      token <- getToken
      gameId <- getGameId
      players <- Players.get(gameId).toRight(GameOver(Games(gameId))).toTry
      color <- Tokens.getColor(token, gameId).toRight(PlayerNotFoundByToken(gameId, token)).toTry
      player <- players.get(color).toRight(PlayerNotFoundByToken(gameId, token)).toTry
    yield player

  def getToken: Try[String] =
    if !headers.contains("Authentication") then Failure(AuthorizationMissing(headers))
    else
      val authenticationParts = headers("Authentication").split("\\s+")
      if authenticationParts(0) != "Bearer" then Failure(AuthorizationMethodWrong(authenticationParts(0)))
      else Success(authenticationParts(1))

  private def isAuthorized: Boolean = getToken.isSuccess

  def debugInfo: RequestInfo = if debug && isAuthorized then this else NullRequestInfo
