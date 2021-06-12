package go3d.server

import go3d.{GoException, Pass}

import javax.servlet.http.HttpServletResponse

class PassServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    try
      val gameId = requestInfo.getGameId
      val color = requestInfo.mustGetPlayer.color
      val game = Games(gameId)
      if !game.isTurn(color) then throw NotReadyToSet(gameId, color)
      val newGame = game.makeMove(Pass(color))
      Games = Games + (gameId -> newGame)
      Io.saveGame(gameId)
      StatusResponse(newGame, newGame.possibleMoves(color), false, requestInfo)
    catch
      case e: NotReadyToSet => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: AuthorizationError => error(response, e, HttpServletResponse.SC_UNAUTHORIZED)
      case e: GoException => error(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
