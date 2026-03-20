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

import scala.util.{Failure, Success, Try}

abstract case class MakeMove(gameId: String, request: Request[IO])
  extends BaseHandler with MakeMoveTrait with LazyLogging:
  def handle: Try[GoResponse] =
    for
      requestInfo <- RequestInfo(request)
      player <- requestInfo.mustGetPlayer
      newGame <- Games.update(gameId) { game =>
        if game.isOver then Failure(GameOver(game))
        else if !game.isTurn(player.color) then Failure(NotReadyToSet(gameId, player.color))
        else game.makeMove(makeMove(requestInfo.path, player.color))
      }
    yield
      logger.info(s"${requestInfo.path}, ${player.color}".replaceAll("[\r\n]"," "))
      StatusResponse(
        newGame, newGame.possibleMoves(player.color), false, newGame.isOver, Some(player.color),
        requestInfo.debugInfo
      )
