package go3d.server

import go3d.{Color, Black, White}

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
      val gameId = getGameId(pathInfo)
      val color = getColor(pathInfo)
      val token = generateAuthToken(gameId, color)
      val player = registerPlayer(color, gameId, token)
      val ready = (color == Black) && Players(gameId).contains(White)
      val debug = RequestDebugInfo(
        headers.toList.toMap,
        if (queryString != null && queryString.nonEmpty) queryString else "/",
        if (pathInfo != null && pathInfo.nonEmpty) pathInfo else "/"
      )
      output = PlayerRegisteredResponse(Games(gameId), color, token, ready, debug).asJson.noSpaces
      Io.saveGame(gameId)
    catch case e: ServerException =>
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      output = ErrorResponse(e.message.toString).asJson.noSpaces
    finally
      response.getWriter.println(output)
  
  private def generateAuthToken(gameId: String, color: go3d.Color): String =
    return gameId+color.toString