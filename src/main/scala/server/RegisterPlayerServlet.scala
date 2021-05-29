package go3d.server

import java.util.Collections
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class RegisterPlayerServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output: GoResponse = ErrorResponse("i have no idea what happened")
    try
      response.setStatus(HttpServletResponse.SC_OK)
      val headerNames = Collections.list(request.getHeaderNames).toArray
      val allHeaders = for (name <- headerNames)
        yield (name.toString, request.getHeader(name.toString))
      val queryString = request.getQueryString
      val pathInfo = request.getPathInfo
      val (gameId, color) = getGameId(pathInfo)
      val game = Games(gameId)
      val token = generateAuthToken(gameId, color)
      val debug = RequestDebugInfo(
        allHeaders.toList.toMap,
        if (queryString != null && queryString.nonEmpty) queryString else "/",
        if (pathInfo != null && pathInfo.nonEmpty) pathInfo else "/"
      )
      output = PlayerRegisteredResponse(game, color, token, debug)
    catch case e: ServerException =>
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      output = ErrorResponse(e.message.toString)
    finally
      val json = Jsonify.toJson(output)
      println(json)
      response.getWriter.println(json)

  private def getGameId(pathInfo: String): (String, go3d.Color) =
    if pathInfo == null || pathInfo.isEmpty then throw MalformedRequest(pathInfo)
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then throw MalformedRequest(pathInfo)
    val gameId = parts(0)
    if !(Games contains gameId) then throw NonexistentGame(gameId, Games.keys.toList)
    return (gameId, go3d.Black)

  private def generateAuthToken(gameId: String, color: go3d.Color): String =
    return gameId+color.toString