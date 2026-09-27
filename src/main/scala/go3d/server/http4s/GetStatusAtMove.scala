package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.RequestInfo
import go3d.server.StatusResponse
import org.http4s.Request

import scala.util.Try

case class GetStatusAtMove(gameId: String, moveCount: Int, request: Request[IO])
    extends BaseHandler with LazyLogging:
  def handle: Try[GoResponse] =
    for
      requestInfo  <- RequestInfo(request)
      replayedGame <- {
        val game  = Games(gameId)
        val count = moveCount.max(0).min(game.moves.length)
        game.atMove(count)
      }
    yield StatusResponse(
      game        = replayedGame,
      moves       = List(),
      ready       = false,
      over        = replayedGame.isOver,
      playerColor = None,
      debug       = requestInfo.debugInfo
    )
