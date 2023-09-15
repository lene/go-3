package go3d.server

import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.Logger
import go3d.Game

class StatusServlet extends BaseServlet:
  def logger: Logger = Logger[StatusServlet]

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val game = Games(gameId)
    try
      response.setStatus(HttpServletResponse.SC_OK)
      statusForRequest(requestInfo, gameId, game)
    catch
      case _: AuthorizationError => errorResponse(game)

  private def statusForRequest(requestInfo: RequestInfo, gameId: String, game: Game) =
    requestInfo.getPlayer match
      case Some(p) =>
        val ready = game.isTurn(p.color) && Games.isReady(gameId)
        StatusResponse(game, game.possibleMoves(p.color), ready, game.isOver, requestInfo.debugInfo)
      case None => StatusResponse(game, List(), false, game.isOver, requestInfo.debugInfo)

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength
