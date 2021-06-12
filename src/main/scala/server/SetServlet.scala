package go3d.server

import go3d.{Color, GoException, Move, Position}

import javax.servlet.http.HttpServletResponse

class SetServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    try
      val gameId = requestInfo.getGameId
      val color = requestInfo.mustGetPlayer.color
      val game = Games(gameId)
      if !game.isTurn(color) then throw NotReadyToSet(gameId, color)
      val newGame = game.makeMove(getMove(requestInfo.path, color))
      Games = Games + (gameId -> newGame)
      Io.saveGame(gameId)
      StatusResponse(newGame, newGame.possibleMoves(color), false, requestInfo)
    catch
      case e: NotReadyToSet => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: AuthorizationError => error(response, e, HttpServletResponse.SC_UNAUTHORIZED)
      case e: GoException => error(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)

  private def getMove(pathInfo: String, color: Color): Move =
    if pathInfo == null || pathInfo.isEmpty then throw MalformedRequest(pathInfo)
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.length < 4 then throw MalformedRequest(pathInfo)
    val (x, y, z) = (parts(1).toInt, parts(2).toInt, parts(3).toInt)
    return Move(x, y, z, color)
