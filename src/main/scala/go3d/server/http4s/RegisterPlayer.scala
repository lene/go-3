package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.Black
import go3d.Color
import go3d.White
import go3d.server.Games
import go3d.server.GoResponse
import go3d.server.IdGenerator
import go3d.server.PlayerRegisteredResponse
import go3d.server.Players
import go3d.server.RequestInfo
import go3d.server.SecurityUtils
import go3d.server.Tokens
import org.http4s.Request

import scala.util.Try

case class RegisterPlayer(gameId: String, color: Color, request: Request[IO])
  extends BaseHandler with LazyLogging:
  def handle: Try[GoResponse] =
    val token = IdGenerator.generateAuthToken(gameId, color)
    for
      _ <- Games.registerPlayer(gameId, color)
      requestInfo <- RequestInfo(request)
    yield
      Tokens.register(token, gameId, color)
      val ready = (color == Black) && Players(gameId).contains(White)
      logger.info(s"Player registered: gameId=$gameId, color=$color, tokenHash=${SecurityUtils.tokenHashPrefix(token)}")
      PlayerRegisteredResponse(Games(gameId), color, token, ready, requestInfo.debugInfo)
