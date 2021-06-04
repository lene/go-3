package go3d.server

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.circe.syntax.EncoderOps

class NewGameServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    try
      val boardSize = getBoardSize(request.getPathInfo)
      val gameId = registerGame(boardSize)
      response.setStatus(HttpServletResponse.SC_OK)
      output = GameCreatedResponse(gameId, boardSize).asJson.noSpaces
    catch case e: go3d.BadBoardSize =>
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
      output = ErrorResponse(e.message.toString).asJson.noSpaces
    finally
      response.getWriter.println(output)

  private def getBoardSize(pathInfo: String): Int =
    if pathInfo == null || pathInfo.isEmpty then return go3d.DefaultBoardSize
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then return go3d.DefaultBoardSize
    return parts(0).toInt
