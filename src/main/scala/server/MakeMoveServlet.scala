package go3d.server

import javax.servlet.http.HttpServletResponse

abstract class MakeMoveServlet extends BaseServlet with MakeMove:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val color = requestInfo.mustGetPlayer.color
    val game = Games(gameId)
    if !game.isTurn(color) then throw NotReadyToSet(gameId, color)
    val newGame = game.makeMove(makeMove(requestInfo.path, color))
    Games = Games + (gameId -> newGame)
    Io.saveGame(gameId)
    response.setStatus(HttpServletResponse.SC_OK)
    StatusResponse(newGame, newGame.possibleMoves(color), false, requestInfo)

