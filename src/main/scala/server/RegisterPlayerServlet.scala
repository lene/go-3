package go3d.server

import java.util.Collections
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class RegisterPlayerServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ujson.Arr(ujson.Obj("default" -> "i have no idea what happened"))
    try
      response.setStatus(HttpServletResponse.SC_OK)
      val headerNames = Collections.list(request.getHeaderNames).toArray
      val allHeaders = for (name <- headerNames) yield (name.toString, request.getHeader(name.toString))
      val queryString = request.getQueryString
      val pathInfo = request.getPathInfo
      val (gameId, color) = getGameId(pathInfo)
      val game = Games(gameId)
      val token = generateAuthToken(gameId, color)
      output = ujson.Arr(
        ujson.Obj("headers" -> allHeaders.toList.toMap.mkString(", ")),
        ujson.Obj("query" -> (if (queryString != null && queryString.nonEmpty) queryString else "/")),
        ujson.Obj("pathInfo" -> (if (pathInfo != null && pathInfo.nonEmpty) pathInfo else "/")),
        ujson.Obj("game" -> Jsonify.toJson(game)),
        ujson.Obj("color" -> color.toString),
        ujson.Obj("authToken" -> token)
      )
    catch case e: ServerException =>
      response.setStatus(HttpServletResponse.SC_OK)
      val msg = e.message.toString
      output = ujson.Arr(ujson.Obj("error" -> msg))
    finally
      println(ujson.write(output))
      response.getWriter.println(ujson.write(output))

  private def getGameId(pathInfo: String): (String, go3d.Color) =
    if pathInfo == null || pathInfo.isEmpty then throw MalformedRequest(pathInfo)
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then throw MalformedRequest(pathInfo)
    val gameId = parts(0)
    if !(Games contains gameId) then throw NonexistentGame(gameId, Games.keys.toList)
    return (gameId, go3d.Color.Black)

  private def generateAuthToken(gameId: String, color: go3d.Color): String =
    return gameId+color.toString