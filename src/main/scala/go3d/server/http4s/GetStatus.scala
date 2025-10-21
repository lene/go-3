package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.RequestInfo
import go3d.server.StatusResponse
import org.http4s.Request

case class GetStatus(gameId: String, request: Request[IO]) extends BaseHandler with LazyLogging:
  def handle: GoResponse =
    val requestInfo = RequestInfo(request)
    val game = Games(gameId)
    requestInfo.getPlayer match
      case Some(p) =>
        val ready = game.isTurn(p.color) && Games.isReady(gameId)
        StatusResponse(
          game, game.possibleMoves(p.color), ready, game.isOver, Some(p.color),
          requestInfo.debugInfo
        )
      case None =>
        StatusResponse(game, List(), false, game.isOver, None, requestInfo.debugInfo)
