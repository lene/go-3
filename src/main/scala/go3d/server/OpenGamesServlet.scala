package go3d.server

import javax.servlet.http.HttpServletResponse

/**
  Usage example for this endpoint:
  To get an essentially random game ID waiting for a white player to connect:
  $ curl -s http://$HOST/openGames | jq -r .ids[0]
  To join a game without knowing the game ID waiting for a player:
  $ bot-client --server $HOST --game-id $(curl -s http://localhost:6030/openGames | jq -r .ids[0]) \
      --color w --strategy random
**/
class OpenGamesServlet extends BaseServlet:

  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    try
      Thread.sleep(100) // very basic DoS protection
      response.setStatus(HttpServletResponse.SC_OK)
      GameListResponse(Players.openGames())
    catch
      case _ => ErrorResponse("unknown error")

  def maxRequestLength: Int = 0

import cats.effect.IO
import org.http4s.Response
import org.http4s.dsl.io._
class OpenGamesHandler extends BaseHandler:
  def handle: GoResponse =
    Thread.sleep(100) // very basic DoS protection
    GameListResponse(Players.openGames())
