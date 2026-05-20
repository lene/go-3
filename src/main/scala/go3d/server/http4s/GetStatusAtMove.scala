package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.NullRequestInfo
import go3d.server.StatusResponse
import org.http4s.Request

import scala.util.Try

case class GetStatusAtMove(gameId: String, moveCount: Int, request: Request[IO])
    extends BaseHandler with LazyLogging:
  def handle: Try[GoResponse] =
    Try {
      val game = Games(gameId)
      val count = moveCount.max(0).min(game.moves.length)
      val replayedGame = game.atMove(count).get
      StatusResponse(
        game        = replayedGame,
        moves       = List(),
        ready       = false,
        over        = replayedGame.isOver,
        playerColor = None,
        debug       = NullRequestInfo
      )
    }
