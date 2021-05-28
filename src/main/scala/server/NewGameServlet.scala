package go3d.server

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class NewGameServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ujson.Arr(ujson.Obj("default" -> "i have no idea what happened"))
    try
      val gameId = IdGenerator.getId
      val boardSize = getBoardSize(request.getPathInfo)
      val game = go3d.newGame(boardSize)
      Games = Games + (gameId -> game)
      response.setStatus(HttpServletResponse.SC_OK)
      output = ujson.Arr(ujson.Obj("id" -> gameId), ujson.Obj("size" -> boardSize))
    catch case e: go3d.BadBoardSize =>
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
      val msg = e.message.toString
      output = ujson.Arr(ujson.Obj("error" -> msg))
    finally
      println(ujson.write(output))
      response.getWriter.println(ujson.write(output))

  private def getBoardSize(pathInfo: String): Int =
    if pathInfo == null || pathInfo.isEmpty then return go3d.DefaultBoardSize
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then return go3d.DefaultBoardSize
    try
      return parts(0).toInt
    catch case e: Exception => return go3d.DefaultBoardSize
