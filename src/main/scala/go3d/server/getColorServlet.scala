package go3d.server

import com.typesafe.scalalogging.Logger

import javax.servlet.http.HttpServletResponse

class getColorServlet extends BaseServlet:
  def logger = Logger[getColorServlet]

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val game = Games(gameId)
    try
      response.setStatus(HttpServletResponse.SC_OK)
      requestInfo.getPlayer match
        case Some(p) =>
          val ready = game.isTurn(p.color) && Players(gameId).size == 2
          StatusResponse(game, game.possibleMoves(p.color), ready, requestInfo.debugInfo)
        case None => StatusResponse(game, List(), false, NullRequestInfo)
    catch
      case e: AuthorizationMissing => StatusResponse(game, List(), false, NullRequestInfo)

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength
