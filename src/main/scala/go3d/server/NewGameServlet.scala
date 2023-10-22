package go3d.server

import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.LazyLogging

class NewGameServlet extends BaseServlet with LazyLogging:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
      val boardSize = getBoardSize(requestInfo.path)
      val gameId = Games.register(boardSize)
      response.setStatus(HttpServletResponse.SC_OK)
      logger.info(s"New game $gameId, size $boardSize".replaceAll("[\r\n]"," "))
      GameCreatedResponse(gameId, boardSize)

  def maxRequestLength: Int = "/".length + 2

  private def getBoardSize(pathInfo: String): Int =
    if pathInfo == null || pathInfo.isEmpty then throw IllegalArgumentException("Missing path info")
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then throw IllegalArgumentException("Missing board size")
    parts(0).toInt

class NewGameHandler(val boardSize: Int) extends BaseHandler with LazyLogging:
  def handle: GoResponse =
    val gameId = Games.register(boardSize)
    logger.info(s"New game $gameId, size $boardSize".replaceAll("[\r\n]", " "))
    GameCreatedResponse(gameId, boardSize)
