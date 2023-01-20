package go3d.server

import java.util.Collections
import javax.servlet.http.HttpServletRequest

val NullRequestInfo = RequestInfo(Map(), "", "", false)

object RequestInfo:
  def apply(request: HttpServletRequest, maxLength: Int): RequestInfo =
    val headerNames = Collections.list(request.getHeaderNames).toArray
    val headers = for (name <- headerNames) yield (name.toString, request.getHeader(name.toString))
    val queryString = request.getQueryString
    val (pathInfo, debug) = parsePathInfo(request.getPathInfo)
    if pathInfo != null && pathInfo.length > maxLength
      then throw RequestTooLong(maxLength, pathInfo.length)
    RequestInfo(
      headers.toList.toMap,
      if (queryString != null && queryString.nonEmpty) queryString else "/",
      if (pathInfo != null && pathInfo.nonEmpty) pathInfo else "/",
      debug
    )

  def parsePathInfo(pathInfo: String): (String, Boolean) =
    if pathInfo != null && pathInfo.endsWith("/d") then (pathInfo.dropRight(2), true)
    else (pathInfo, false)

case class RequestInfo(headers: Map[String, String], query: String, path: String, debug: Boolean)
  extends GoResponse:
  def getGameId: String =
    if path == null || path.isEmpty then throw MalformedRequest(path)
    val parts = path.stripPrefix("/").split('/')
    if parts.isEmpty then throw MalformedRequest(path)
    val gameId = parts(0)
    if !(Games contains gameId) then throw NonexistentGame(gameId, Games.keys.toList)
    gameId

  def getToken: String =
    if !headers.contains("Authentication") then throw AuthorizationMissing(headers)
    val authorizationParts = headers("Authentication").split("\\s+")
    if authorizationParts(0) != "Bearer" then throw AuthorizationMethodWrong(authorizationParts(0))
    authorizationParts(1)

  def getPlayer: Option[Player] =
    val players = Players(getGameId)
    try
      players.find(_._2.token == getToken).map(pair => pair._2)
    catch case _: AuthorizationError => None

  def mustGetPlayer: Player =
    val players = Players(getGameId)
    players.find(_._2.token == getToken).map(pair => pair._2) match
      case Some(value) => value
      case None => throw PlayerNotFoundByToken(getGameId, getToken)

  def debugInfo: RequestInfo = if debug then this else NullRequestInfo
