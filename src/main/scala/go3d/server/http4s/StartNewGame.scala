package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.Goban
import go3d.server.GameCreatedResponse
import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.KomiQuery
import go3d.server.RateLimiter
import org.http4s.Request

import scala.util.Try

case class StartNewGame(boardSize: Int, request: Request[IO]) extends BaseHandler with LazyLogging:
  def handle: Try[GoResponse] =
    for
      _ <- RateLimiter.check(clientIp(request))
      _ <- Goban.checkSize(boardSize)
      komi <- KomiQuery.fromValues(request.multiParams.get(KomiQuery.Parameter), boardSize)
      gameId <- Games.register(boardSize, komi)
    yield
      logger.info(s"New game $gameId, size $boardSize, komi $komi".replaceAll("[\r\n]", " "))
      GameCreatedResponse(gameId, boardSize)
