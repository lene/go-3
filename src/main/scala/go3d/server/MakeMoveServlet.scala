package go3d.server

import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.LazyLogging

abstract class MakeMoveServlet extends BaseServlet with MakeMove with LazyLogging:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val color = requestInfo.mustGetPlayer.color
    val game = Games(gameId)
    if !game.isTurn(color) then throw NotReadyToSet(gameId, color)
    val newGame = game.makeMove(makeMove(requestInfo.path, color))
    Games = Games + (gameId -> newGame)
    Io.saveGame(gameId)
    response.setStatus(HttpServletResponse.SC_OK)
    logger.info(s"${requestInfo.path}, $color")
    StatusResponse(newGame, newGame.possibleMoves(color), false, requestInfo.debugInfo)

