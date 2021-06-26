package go3d.server

import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.LazyLogging

class NewGameServlet extends BaseServlet with LazyLogging:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
      val boardSize = getBoardSize(requestInfo.path)
      val gameId = registerGame(boardSize)
      response.setStatus(HttpServletResponse.SC_OK)
      logger.info(s"$gameId, $boardSize")
      GameCreatedResponse(gameId, boardSize)

  def maxRequestLength: Int = "/".length + 2

  private def getBoardSize(pathInfo: String): Int =
    if pathInfo == null || pathInfo.isEmpty then return go3d.DefaultBoardSize
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then go3d.DefaultBoardSize else parts(0).toInt
