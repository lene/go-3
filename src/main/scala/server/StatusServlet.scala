package go3d.server
  
import javax.servlet.http.HttpServletResponse

class StatusServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val game = Games(gameId)
    try
      response.setStatus(HttpServletResponse.SC_OK)
      requestInfo.getPlayer match
        case Some(p) =>
          val ready = game.isTurn(p.color) && Players(gameId).size == 2
          StatusResponse(game, game.possibleMoves(p.color), ready, requestInfo)
        case None => StatusResponse(game, List(), false, requestInfo)
    catch
      case e: AuthorizationMissing => StatusResponse(game, List(), false, requestInfo)

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength
