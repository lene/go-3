package go3d.server

import go3d.{Black, Position, Move}
import io.circe.syntax.EncoderOps

import java.util.Collections
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class SetServlet extends HttpServlet:
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
      val token = getToken(headersMap)
      val player = playerFromToken(gameId, token)
      val color = player.color
      val game = Games(gameId)
      val ready = if game.moves.isEmpty then color == Black else color != game.moves.last.color
      if !ready then throw NotReadyToSet(gameId, token)
      val position = getPosition(pathInfo)
      val newGame = game.makeMove(Move(position, color))
      Games = Games + (gameId -> newGame)
      output = StatusResponse(newGame, newGame.possibleMoves(color), !ready, debug).asJson.noSpaces
    catch
      case e @ (_: PlayerNotFoundByToken | _: AuthorizationMissing | _: AuthorizationMethodWrong) =>
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        output = ErrorResponse(e.toString).asJson.noSpaces
      case e @ (_: go3d.BadBoardSize | _: NotReadyToSet) =>
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

  private def getPosition(pathInfo: String): Position =
    if pathInfo == null || pathInfo.isEmpty then throw MalformedRequest(pathInfo)
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.length < 4 then throw MalformedRequest(pathInfo)
    val (x, y, z) = (parts(1).toInt, parts(2).toInt, parts(3).toInt)
    return Position(x, y, z)