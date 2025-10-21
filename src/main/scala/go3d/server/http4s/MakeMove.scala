package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.GameOver
import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.NotReadyToSet
import go3d.server.RequestInfo
import go3d.server.StatusResponse
import org.http4s.Request

abstract case class MakeMove(gameId: String, request: Request[IO])
  extends BaseHandler with MakeMoveTrait with LazyLogging:
  def handle: GoResponse =
    val requestInfo = RequestInfo(request)
    val color = requestInfo.mustGetPlayer.color

    // Atomic update using optimistic locking (CAS retry loop)
    // Pre-flight checks need to be inside the update to be thread-safe
    val newGame = Games.update(gameId) { game =>
      if game.isOver then throw GameOver(game)
      if !game.isTurn(color) then throw NotReadyToSet(gameId, color)
      game.makeMove(makeMove(requestInfo.path, color))
    }

    logger.info(s"${requestInfo.path}, $color".replaceAll("[\r\n]"," "))
    StatusResponse(
      newGame, newGame.possibleMoves(color), false, newGame.isOver, Some(color),
      requestInfo.debugInfo
    )

