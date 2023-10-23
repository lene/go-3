package go3d.server

import javax.servlet.http.HttpServletResponse
import com.typesafe.scalalogging.{LazyLogging, Logger}
import go3d.{Color, Game}

class StatusServlet extends BaseServlet:
  def generateOutput(requestInfo: RequestInfo, response: HttpServletResponse): GoResponse =
    val gameId = requestInfo.getGameId
    try
      response.setStatus(HttpServletResponse.SC_OK)
      statusForRequest(requestInfo, gameId)
    catch
      case _: AuthorizationError => errorResponse(Games(gameId))

  def maxRequestLength: Int = "/".length + IdGenerator.IdLength

import cats.effect.IO
import org.http4s.Request
class StatusHandler(val gameId: String, val request: Request[IO]) extends BaseHandler with LazyLogging:
  def handle: GoResponse =
    val r = RequestInfo(request)
    logger.warn(s"StatusHandler.handle: requestInfo=$r")
    statusForRequest(r, gameId)

def statusForRequest(requestInfo: RequestInfo, gameId: String) =
  val logger = Logger[StatusHandler]
  val game = Games(gameId)
  requestInfo.getPlayer match
    case Some(p) =>
      logger.warn(s"StatusHandler.statusForRequest: player=$p")
      val ready = game.isTurn(p.color) && Games.isReady(gameId)
      StatusResponse(game, game.possibleMoves(p.color), ready, game.isOver, requestInfo.debugInfo)
    case None =>
      logger.warn(s"StatusHandler.statusForRequest: no player")
      StatusResponse(game, List(), false, game.isOver, requestInfo.debugInfo)
