package go3d.server.http4s

import com.typesafe.scalalogging.LazyLogging
import go3d.server.GameCreatedResponse
import go3d.server.Games
import go3d.server.GoResponse

import scala.util.Try

case class StartNewGame(boardSize: Int) extends BaseHandler with LazyLogging:
  def handle: Try[GoResponse] =
    Games.register(boardSize).map { gameId =>
      logger.info(s"New game $gameId, size $boardSize".replaceAll("[\r\n]", " "))
      GameCreatedResponse(gameId, boardSize)
    }
