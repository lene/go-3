package go3d.server

import go3d.GameOver

import java.util.Collections
import javax.servlet.http.HttpServletRequest

val NullRequestInfo = RequestInfo(Map(), "", "", false)

object RequestInfo:
  def apply(request: HttpServletRequest, maxLength: Int): RequestInfo =
    val headerNames = Collections.list(request.getHeaderNames).toArray
    val headers = for (name <- headerNames) yield (name.toString, request.getHeader(name.toString))
    fromRaw(
      headers.toList.toMap, request.getQueryString,
      request.getPathInfo, maxLength
    )

  import cats.effect.IO
  import org.http4s.Request
  def apply(request: Request[IO], maxLength: Int=100): RequestInfo =
    val headers = request.headers.headers.map(h => (h.name.toString, h.value)).toMap
    fromRaw(
      headers, request.uri.query.toString,
      request.pathInfo.toString.split("/").tail.tail.mkString("/"), maxLength
    )

  private def fromRaw(
    headers: Map[String, String], queryString: String, rawPathInfo: String, maxLength: Int
  ): RequestInfo =
    val (pathInfo, debug) = parsePathInfo(rawPathInfo)
    if pathInfo != null && pathInfo.length > maxLength
    then throw RequestTooLong(maxLength, pathInfo.length)
    RequestInfo(
      headers,
      if (queryString != null && queryString.nonEmpty) queryString else "/",
      if (pathInfo != null && pathInfo.nonEmpty) pathInfo else "/",
      debug
    )

  private def parsePathInfo(pathInfo: String): (String, Boolean) =
    if pathInfo != null && pathInfo.endsWith("/d") then (pathInfo.dropRight(2), true)
    else (pathInfo, false)

case class RequestInfo(headers: Map[String, String], query: String, path: String, debug: Boolean)
  extends GoResponse:
  def getGameId: String =
    if path == null || path.isEmpty then throw MalformedRequest(path)
    val parts = path.stripPrefix("/").split('/')
    if parts.isEmpty then throw MalformedRequest(path)
    val gameId = parts(0)
    if !(Games contains gameId) then throw NonexistentGame(gameId, Games.activeGameIds.toList)
    gameId

  def getPlayer: Option[Player] =
    val players = Players.get(getGameId)
    players.flatMap({
      try _.find(_._2.token == getToken).map(pair => pair._2)
      catch case _: AuthorizationError => _ => None
    })
  def mustGetPlayer: Player =
    val players = Players.get(getGameId)
    if players.isEmpty then throw GameOver(Games(getGameId))
    players.get.find(_._2.token == getToken).map(pair => pair._2) match
      case Some(value) => value
      case None => throw PlayerNotFoundByToken(getGameId, getToken)

  private def getToken: String =
    if !headers.contains("Authentication") then throw AuthorizationMissing(headers)
    val authorizationParts = headers("Authentication").split("\\s+")
    if authorizationParts(0) != "Bearer" then throw AuthorizationMethodWrong(authorizationParts(0))
    authorizationParts(1)

  private def isAuthorized: Boolean =
    try getToken.nonEmpty
    catch case _: AuthorizationError => false

  def debugInfo: RequestInfo = if debug && isAuthorized then this else NullRequestInfo
