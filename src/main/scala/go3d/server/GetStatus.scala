package go3d.server

import com.typesafe.scalalogging.LazyLogging
import cats.effect.IO
import org.http4s.Request

case class GetStatus(gameId: String, request: Request[IO]) extends BaseHandler with LazyLogging:
  def handle: GoResponse =
    val requestInfo = RequestInfo(request)
    val game = Games(gameId)
    requestInfo.getPlayer match
      case Some(p) =>
        val ready = game.isTurn(p.color) && Games.isReady(gameId)
        StatusResponse(game, game.possibleMoves(p.color), ready, game.isOver, requestInfo.debugInfo)
      case None => StatusResponse(game, List(), false, game.isOver, requestInfo.debugInfo)
