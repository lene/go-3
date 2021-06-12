package go3d.server

import go3d.{Color, Black, White}

import java.util.Collections
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.circe.syntax.EncoderOps

class RegisterPlayerServlet extends HttpServlet:

  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    response.setStatus(HttpServletResponse.SC_OK)
    val requestInfo = RequestInfo(request)
    try
      val gameId = requestInfo.getGameId
      val color = getColor(requestInfo)
      val token = generateAuthToken(gameId, color)
      val player = registerPlayer(color, gameId, token)
      val ready = (color == Black) && Players(gameId).contains(White)
      output = PlayerRegisteredResponse(
        Games(gameId), color, token, ready, requestInfo
      ).asJson.noSpaces
      Io.saveGame(gameId)
    catch case e: ServerException =>
      output = errorResponse(response, e.toString, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    finally
      response.getWriter.println(output)

  // TODO generate a secret token
  private def generateAuthToken(gameId: String, color: go3d.Color): String =
    return gameId+color.toString

  private def getColor(requestInfo: RequestInfo): go3d.Color =
    val parts = requestInfo.path.stripPrefix("/").split('/')
    if parts.length < 2 then throw MalformedRequest(requestInfo.path)
    val color = Color(parts(1)(0))
    val gameId = requestInfo.getGameId
    if Players.contains(gameId) && Players(gameId).contains(color) then
      throw DuplicateColor(gameId, color)
    return color
