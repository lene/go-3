package go3d.server

import go3d.{Black, Color, White}

import javax.servlet.http.HttpServletResponse

class RegisterPlayerServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    try
      val gameId = requestInfo.getGameId
      val color = getColor(requestInfo)
      val token = generateAuthToken(gameId, color)
      val player = registerPlayer(color, gameId, token)
      val ready = (color == Black) && Players(gameId).contains(White)
      Io.saveGame(gameId)
      PlayerRegisteredResponse(Games(gameId), color, token, ready, requestInfo)
    catch
      case e: DuplicateColor => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: ServerException => error(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)

  private def generateAuthToken(gameId: String, color: go3d.Color): String =
    return IdGenerator.getBase62(10)

  private def getColor(requestInfo: RequestInfo): go3d.Color =
    val parts = requestInfo.path.stripPrefix("/").split('/')
    if parts.length < 2 then throw MalformedRequest(requestInfo.path)
    val color = Color(parts(1)(0))
    val gameId = requestInfo.getGameId
    if Players.contains(gameId) && Players(gameId).contains(color) then
      throw DuplicateColor(gameId, color)
    return color
