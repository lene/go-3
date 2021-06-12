package go3d.server

import go3d.Black

import io.circe.syntax.EncoderOps

import java.util.Collections
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class StatusServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    response.setStatus(HttpServletResponse.SC_OK)
    val requestInfo = RequestInfo(request)
    val gameId = requestInfo.getGameId
    val game = Games(gameId)
    try
      requestInfo.getPlayer match
        case Some(p) =>
          val color = p.color
          output = StatusResponse(
            game, game.possibleMoves(color), game.isTurn(color), requestInfo
          ).asJson.noSpaces
        case None => output = StatusResponse(game, List(), false, requestInfo).asJson.noSpaces
    catch
      case e: AuthorizationMissing =>
        output = StatusResponse(game, List(), false, requestInfo).asJson.noSpaces
      case e: go3d.BadBoardSize =>
        output = errorResponse(response, e.toString, HttpServletResponse.SC_BAD_REQUEST)
      case e =>
        output = errorResponse(response, e.toString, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    finally
      response.getWriter.println(output)
