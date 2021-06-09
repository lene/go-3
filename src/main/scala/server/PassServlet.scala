package go3d.server

import go3d.{Black, Move, Pass, Position}
import io.circe.syntax.EncoderOps

import java.util.Collections
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PassServlet extends HttpServlet:
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
      println(gameId)
      val token = getToken(headersMap)
      val player = playerFromToken(gameId, token)
      println(player)
      val color = player.color
      val game = Games(gameId)
      val ready = if game.moves.isEmpty then color == Black else color != game.moves.last.color
      if !ready then throw NotReadyToSet(gameId, token)
      val newGame = game.makeMove(Pass(color))
      output = StatusResponse(newGame, newGame.possibleMoves(color), !ready, debug).asJson.noSpaces
    catch
      case e @ (_: go3d.BadBoardSize | _: PlayerNotFoundByToken | _: NotReadyToSet) =>
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

  private def playerFromToken(gameId: String, token: String): Player =
    val players = Players(gameId)
    for (_, player) <- players do if player.token == token then return player
    throw PlayerNotFoundByToken(gameId, token)
