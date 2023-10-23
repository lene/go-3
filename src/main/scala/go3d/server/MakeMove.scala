package go3d.server

import go3d.GameOver

import com.typesafe.scalalogging.LazyLogging
import cats.effect.IO
import org.http4s.Request

abstract case class MakeMove(gameId: String, request: Request[IO])
  extends BaseHandler with MakeMoveTrait with LazyLogging:
  def handle: GoResponse =
    val requestInfo = RequestInfo(request)
    val color = requestInfo.mustGetPlayer.color
    val game = Games(gameId)
    if game.isOver then throw GameOver(game)
    if !game.isTurn(color) then throw NotReadyToSet(gameId, color)
    val newGame = game.makeMove(makeMove(requestInfo.path, color))
    Games.add(gameId, newGame)
    logger.info(s"${requestInfo.path}, $color".replaceAll("[\r\n]"," "))
    StatusResponse(
      newGame, newGame.possibleMoves(color), false, newGame.isOver, requestInfo.debugInfo
    )

