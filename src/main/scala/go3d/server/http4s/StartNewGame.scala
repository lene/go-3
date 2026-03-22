package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.server.GameCreatedResponse
import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.RateLimiter
import org.http4s.Request

import scala.util.Try

case class StartNewGame(boardSize: Int, request: Request[IO]) extends BaseHandler with LazyLogging:
  def handle: Try[GoResponse] =
    for
      _ <- RateLimiter.check(clientIp(request))
      gameId <- Games.register(boardSize)
    yield
      logger.info(s"New game $gameId, size $boardSize".replaceAll("[\r\n]", " "))
      GameCreatedResponse(gameId, boardSize)
