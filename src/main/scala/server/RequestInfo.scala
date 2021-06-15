package go3d.server

import java.util.Collections
import javax.servlet.http.HttpServletRequest

object RequestInfo:
  def apply(request: HttpServletRequest, maxLength: Int): RequestInfo =
    val headerNames = Collections.list(request.getHeaderNames).toArray
    val headers = for (name <- headerNames) yield (name.toString, request.getHeader(name.toString))
    val queryString = request.getQueryString
    val pathInfo = request.getPathInfo
    if pathInfo != null && pathInfo.length > maxLength
      then throw RequestTooLong(maxLength, pathInfo.length)
    return RequestInfo(
      headers.toList.toMap,
      if (queryString != null && queryString.nonEmpty) queryString else "/",
      if (pathInfo != null && pathInfo.nonEmpty) pathInfo else "/"
    )

case class RequestInfo(headers: Map[String, String], query: String, path: String)
  extends GoResponse:
  def getGameId: String =
    if path == null || path.isEmpty then throw MalformedRequest(path)
    val parts = path.stripPrefix("/").split('/')
    if parts.isEmpty then throw MalformedRequest(path)
    val gameId = parts(0)
    if !(Games contains gameId) then throw NonexistentGame(gameId, Games.keys.toList)
    return gameId

  def getToken: String =
    if !headers.contains("Authentication") then throw AuthorizationMissing(headers)
    val authorizationParts = headers("Authentication").split("\\s+")
    if authorizationParts(0) != "Basic" then throw AuthorizationMethodWrong(authorizationParts(0))
    return authorizationParts(1)

  def getPlayer: Option[Player] =
    val players = Players(getGameId)
    try
      for (_, player) <- players do if player.token == getToken then return Some(player)
    catch case e: AuthorizationError => return None
    return None

  def mustGetPlayer: Player =
    val players = Players(getGameId)
    for (_, player) <- players do if player.token == getToken then return player
    throw PlayerNotFoundByToken(getGameId, getToken)