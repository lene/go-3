package go3d.server.http4s

import cats.effect.IO
import cats.effect.Resource
import com.comcast.ip4s.Port
import com.comcast.ip4s.ipv4
import com.typesafe.scalalogging.LazyLogging
import go3d.Color
import go3d.server.IdGenerator
import org.http4s.EntityEncoder
import org.http4s.HttpRoutes
import org.http4s.circe.jsonEncoderOf
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object GameId:
  def unapply(str: String): Option[String] = Some(str).filter(IdGenerator.isValidId)

object ColorVar:
  def unapply(str: String): Option[Color] = Color(str.head).toOption

case class GoHttpService(port: Int) extends LazyLogging:

  given LoggerFactory[IO] = Slf4jFactory.create[IO]
  implicit def intEncoder: EntityEncoder[IO, Int] = jsonEncoderOf[Int]

  private val goService = HttpRoutes.of[IO] {
    case request@GET -> Root / "new" / IntVar(boardSize) =>
      StartNewGame(boardSize, request).response
    case request@GET -> Root / "register" / GameId(gameId) / ColorVar(color) =>
      logger.info("received request"); RegisterPlayer(gameId, color, request).response
    case request@GET -> Root / "status" / GameId(gameId) =>
      GetStatus(gameId, request).response
    case request@GET -> Root / "status" / GameId(gameId) / "d" =>
      GetStatus(gameId, request).response
    case request@GET -> Root / "set" / GameId(gameId) / IntVar(x) / IntVar(y) / IntVar(z) =>
      DoSet(gameId, request, x, y, z).response
    case request@GET -> Root / "set" / GameId(gameId) / IntVar(x) / IntVar(y) / IntVar(z) / "d" =>
      DoSet(gameId, request, x, y, z).response
    case request@GET -> Root / "pass" / GameId(gameId) =>
      DoPass(gameId, request).response
    case request@GET -> Root / "pass" / GameId(gameId) / "d" =>
      DoPass(gameId, request).response
    case GET -> Root / "openGames" => ListOpenGames().response
    case GET -> Root / "health" => Ok(1)
  }

  private[http4s] val httpApp = Router("/" -> goService).orNotFound

  def server: Resource[IO, org.http4s.server.Server] =
    Port.fromInt(port) match
      case None => Resource.eval(IO.raiseError(IllegalArgumentException(s"invalid port $port")))
      case Some(p) =>
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(p)
          .withHttpApp(httpApp)
          .build

