package go3d.server

import javax.servlet.http.HttpServletResponse

class NewGameServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    try
      val boardSize = getBoardSize(requestInfo.path)
      val gameId = registerGame(boardSize)
      GameCreatedResponse(gameId, boardSize)
    catch case e: go3d.BadBoardSize => error(response, e, HttpServletResponse.SC_BAD_REQUEST)

  private def getBoardSize(pathInfo: String): Int =
    if pathInfo == null || pathInfo.isEmpty then return go3d.DefaultBoardSize
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then go3d.DefaultBoardSize else parts(0).toInt
