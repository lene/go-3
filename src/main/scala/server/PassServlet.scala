package go3d.server

import go3d.Pass
import io.circe.syntax.EncoderOps

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PassServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    response.setStatus(HttpServletResponse.SC_OK)
    val requestInfo = RequestInfo(request)
    try
      val gameId = requestInfo.getGameId
      val color = requestInfo.mustGetPlayer.color
      val game = Games(gameId)
      if !game.isTurn(color) then throw NotReadyToSet(gameId, color)
      val newGame = game.makeMove(Pass(color))
      Games = Games + (gameId -> newGame)
      output = StatusResponse(
        newGame, newGame.possibleMoves(color), false, requestInfo
      ).asJson.noSpaces
      Io.saveGame(gameId)
    catch
      case e: AuthorizationError =>
        output = errorResponse(response, e.toString, HttpServletResponse.SC_UNAUTHORIZED)
      case e @ (_: go3d.BadBoardSize | _: NotReadyToSet) =>
        output = errorResponse(response, e.toString, HttpServletResponse.SC_BAD_REQUEST)
      case e =>
        output = errorResponse(response, e.toString, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    finally
      response.getWriter.println(output)
