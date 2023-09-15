package go3d.server

import go3d.{Black, Color, White}

import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.LazyLogging

class RegisterPlayerServlet extends BaseServlet with LazyLogging:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    val color = getColor(requestInfo)
    val token = generateAuthToken(gameId, color)
    Games.registerPlayer(gameId, color, token)
    val ready = (color == Black) && Players(gameId).contains(White)
    response.setStatus(HttpServletResponse.SC_OK)
    logger.info(s"$gameId, $color, $token".replaceAll("[\r\n]"," "))
    PlayerRegisteredResponse(Games(gameId), color, token, ready, requestInfo.debugInfo)

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength + 2 + 2

  private def generateAuthToken(gameId: String, color: Color): String = IdGenerator.getBase62(10)

  private def getColor(requestInfo: RequestInfo): Color =
    val parts = requestInfo.path.stripPrefix("/").split('/')
    if parts.length < 2 then throw MalformedRequest(requestInfo.path)
    val color = Color(parts(1)(0))
    val gameId = requestInfo.getGameId
    if Players.isDuplicate(gameId, color) then throw DuplicateColor(gameId, color)
    color
