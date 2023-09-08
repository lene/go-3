package go3d.server

import com.typesafe.scalalogging.Logger

import javax.servlet.http.HttpServletResponse

class GetColorServlet extends BaseServlet:
  def logger: Logger = Logger[GetColorServlet]

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val game = Games(gameId)
    try
      response.setStatus(HttpServletResponse.SC_OK)
      requestInfo.getPlayer match
        case Some(p) =>
          val ready = game.isTurn(p.color) && Players(gameId).size == 2
          StatusResponse(game, game.possibleMoves(p.color), ready, game.isOver, requestInfo.debugInfo)
        case None => errorResponse(game)
    catch
      case e: AuthorizationMissing => errorResponse(game)

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength
