package go3d.server

import go3d.Color

import java.util.Collections
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.circe.syntax.EncoderOps

class RegisterPlayerServlet extends HttpServlet:

  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    try
      response.setStatus(HttpServletResponse.SC_OK)
      val headerNames = Collections.list(request.getHeaderNames).toArray
      val headers = for (name <- headerNames) yield (name.toString, request.getHeader(name.toString))
      val queryString = request.getQueryString
      val pathInfo = request.getPathInfo
      val (gameId, color) = getGameId(pathInfo)
      val token = generateAuthToken(gameId, color)
      val player = registerPlayer(color, gameId, token)
      val debug = RequestDebugInfo(
        headers.toList.toMap,
        if (queryString != null && queryString.nonEmpty) queryString else "/",
        if (pathInfo != null && pathInfo.nonEmpty) pathInfo else "/"
      )
      output = PlayerRegisteredResponse(Games(gameId), color, token, debug).asJson.noSpaces
      Io.saveGame(gameId)
    catch case e: ServerException =>
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      output = ErrorResponse(e.message.toString).asJson.noSpaces
    finally
      println(output)
      response.getWriter.println(output)

  private def getGameId(pathInfo: String): (String, go3d.Color) =
    if pathInfo == null || pathInfo.isEmpty then throw MalformedRequest(pathInfo)
    val parts = pathInfo.stripPrefix("/").split('/')
    if parts.isEmpty then throw MalformedRequest(pathInfo)
    val gameId = parts(0)
    if !(Games contains gameId) then throw NonexistentGame(gameId, Games.keys.toList)
    val color = Color(parts(1)(0))
    if Players.contains(gameId) && Players(gameId).contains(color) then
      throw DuplicateColor(gameId, color)
    return (gameId, color)

  private def generateAuthToken(gameId: String, color: go3d.Color): String =
    return gameId+color.toString