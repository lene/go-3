package go3d.server

import io.circe.syntax.EncoderOps

import javax.servlet.http.HttpServletResponse

class NewGameServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    try
      val boardSize = getBoardSize(requestInfo.path)
      val gameId = registerGame(boardSize)
      return GameCreatedResponse(gameId, boardSize)
    catch case e: go3d.BadBoardSize =>
      return errorResponse(response, e.toString, HttpServletResponse.SC_BAD_REQUEST)

  private def getBoardSize(pathInfo: String): Int =
    if pathInfo == null || pathInfo.isEmpty then return go3d.DefaultBoardSize
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then return go3d.DefaultBoardSize
    return parts(0).toInt
