package go3d.server

import go3d.GoException

import javax.servlet.http.HttpServletResponse

class StatusServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val game = Games(gameId)
    try
      requestInfo.getPlayer match
        case Some(p) =>
          StatusResponse(game, game.possibleMoves(p.color), game.isTurn(p.color), requestInfo)
        case None => StatusResponse(game, List(), false, requestInfo)
    catch
      case e: AuthorizationMissing => StatusResponse(game, List(), false, requestInfo)
      case e: GoException => error(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
