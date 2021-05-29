package go3d.server

import com.google.gson.Gson
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class NewGameServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output: GoResponse = ErrorResponse("i have no idea what happened")
    try
      val gameId = IdGenerator.getId
      val boardSize = getBoardSize(request.getPathInfo)
      val game = go3d.newGame(boardSize)
      Games = Games + (gameId -> game)
      response.setStatus(HttpServletResponse.SC_OK)
      output = GameCreatedResponse(gameId, boardSize)
    catch case e: go3d.BadBoardSize =>
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
      output = ErrorResponse(e.message.toString)
    finally
      val json = Jsonify.toJson(output)
      println(json)
      response.getWriter.println(json)

  private def getBoardSize(pathInfo: String): Int =
    if pathInfo == null || pathInfo.isEmpty then return go3d.DefaultBoardSize
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then return go3d.DefaultBoardSize
    return parts(0).toInt
