package go3d.server.http4s

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import go3d.server.{Games, GoResponse, PlayerRegisteredResponse, Players, RequestInfo, IdGenerator}
import go3d.{Black, Color, White}
import org.http4s.Request

case class RegisterPlayer(gameId: String, colorChar: Char, request: Request[IO])
  extends BaseHandler with LazyLogging:
  def handle: GoResponse =
    val color = Color(colorChar)
    val token = generateAuthToken(gameId, color)
    Games.registerPlayer(gameId, color, token)
    val ready = (color == Black) && Players(gameId).contains(White)
    logger.info(s"$gameId, $color, $token".replaceAll("[\r\n]", " "))
    PlayerRegisteredResponse(Games(gameId), color, token, ready, RequestInfo(request).debugInfo)

def generateAuthToken(gameId: String, color: Color): String = IdGenerator.getBase62(10)
