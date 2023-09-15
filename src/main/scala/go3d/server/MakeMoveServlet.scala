package go3d.server

import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.LazyLogging
import go3d.GameOver

abstract class MakeMoveServlet extends BaseServlet with MakeMove with LazyLogging:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val color = requestInfo.mustGetPlayer.color
    val game = Games(gameId)
    if game.isOver then throw GameOver(game)
    if !game.isTurn(color) then throw NotReadyToSet(gameId, color)
    val newGame = game.makeMove(makeMove(requestInfo.path, color))
    Games.add(gameId, newGame)
    response.setStatus(HttpServletResponse.SC_OK)
    logger.info(s"${requestInfo.path}, $color".replaceAll("[\r\n]"," "))
    StatusResponse(newGame, newGame.possibleMoves(color), false, newGame.isOver, requestInfo.debugInfo)

