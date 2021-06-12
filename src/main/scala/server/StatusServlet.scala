package go3d.server

import io.circe.syntax.EncoderOps

import javax.servlet.http.HttpServletResponse

class StatusServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val game = Games(gameId)
    try
      requestInfo.getPlayer match
        case Some(p) =>
          val color = p.color
          return StatusResponse(game, game.possibleMoves(color), game.isTurn(color), requestInfo)
        case None => return StatusResponse(game, List(), false, requestInfo)
    catch
      case e: AuthorizationMissing =>
        return StatusResponse(game, List(), false, requestInfo)
      case e: go3d.BadBoardSize =>
        return errorResponse(response, e.toString, HttpServletResponse.SC_BAD_REQUEST)
      case e =>
        return errorResponse(response, e.toString, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
