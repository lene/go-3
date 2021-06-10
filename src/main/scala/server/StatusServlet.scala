package go3d.server

import go3d.Black

import io.circe.syntax.EncoderOps

import java.util.Collections
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class StatusServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    try
      response.setStatus(HttpServletResponse.SC_OK)
      val headerNames = Collections.list(request.getHeaderNames).toArray
      val headers = for (name <- headerNames) yield (name.toString, request.getHeader(name.toString))
      val headersMap = headers.toMap
      val queryString = request.getQueryString
      val pathInfo = request.getPathInfo
      val debug = RequestDebugInfo(request)
      val gameId = getGameId(pathInfo)
      val game = Games(gameId)
      try
        val token = getToken(headersMap)
        val player = playerFromToken(gameId, token)
        player match {
          case Some(p) =>
            val color = p.color
            val ready = if game.moves.isEmpty then color == Black else color != game.moves.last.color
            output = StatusResponse(game, game.possibleMoves(color), ready, debug).asJson.noSpaces
          case None => output = StatusResponse(game, List(), false, debug).asJson.noSpaces
        }
      catch
        case e: AuthorizationMissing =>
          output = StatusResponse(game, List(), false, debug).asJson.noSpaces
    catch
      case e: go3d.BadBoardSize =>
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
        output = ErrorResponse(e.toString).asJson.noSpaces
      case e =>
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        output = ErrorResponse(e.toString).asJson.noSpaces
    finally
      response.getWriter.println(output)

  private def getToken(headers: Map[String, String]): String =
    if !headers.contains("Authentication") then throw AuthorizationMissing(headers)
    val authorizationParts = headers("Authentication").split("\\s+")
    if authorizationParts(0) != "Basic" then throw AuthorizationMethodWrong(authorizationParts(0))
    return authorizationParts(1)

  private def playerFromToken(gameId: String, token: String): Option[Player] =
    val players = Players(gameId)
    for (_, player) <- players do if player.token == token then return Some(player)
    return None