package go3d.server

import go3d.{Color, Game, Position}

import java.util.Collections
import javax.servlet.http.HttpServletRequest

class GoResponse
case class ErrorResponse(err: String) extends GoResponse
case class GameCreatedResponse(id: String, size: Int) extends GoResponse
object RequestDebugInfo:
  def apply(request: HttpServletRequest): RequestDebugInfo =
    val headerNames = Collections.list(request.getHeaderNames).toArray
    val headers = for (name <- headerNames) yield (name.toString, request.getHeader(name.toString))
    val queryString = request.getQueryString
    val pathInfo = request.getPathInfo
    return RequestDebugInfo(
      headers.toList.toMap,
      if (queryString != null && queryString.nonEmpty) queryString else "/",
      if (pathInfo != null && pathInfo.nonEmpty) pathInfo else "/"
    )
case class RequestDebugInfo(headers: Map[String, String], query: String, pathInfo: String)
  extends GoResponse

case class PlayerRegisteredResponse(game: Game, color: Color, authToken: String, ready: Boolean,
                                    debug: RequestDebugInfo) extends GoResponse
case class StatusResponse(game: Game, moves: List[Position], ready: Boolean, debug: RequestDebugInfo) extends GoResponse
