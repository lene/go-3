package go3d.server

import go3d.Pass
import io.circe.syntax.EncoderOps

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
      return StatusResponse(newGame, newGame.possibleMoves(color), false, requestInfo)
    catch
      case e: AuthorizationError =>
        return errorResponse(response, e.toString, HttpServletResponse.SC_UNAUTHORIZED)
      case e @ (_: go3d.BadBoardSize | _: NotReadyToSet) =>
        return errorResponse(response, e.toString, HttpServletResponse.SC_BAD_REQUEST)
      case e =>
        return errorResponse(response, e.toString, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
