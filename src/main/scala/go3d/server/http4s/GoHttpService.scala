package go3d.server.http4s

import cats.effect.{IO, Resource}
import com.comcast.ip4s.{Port, ipv4}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.{EntityEncoder, HttpRoutes}
import org.http4s.circe.jsonEncoderOf
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router

import scala.util.Try
import go3d.Color
import go3d.server.IdGenerator

object GameId:
  def unapply(str: String): Option[String] = Some(str).filter(IdGenerator.isValidId)

object ColorVar:
  def unapply(str: String): Option[Color] = Try(Color(str.head)).toOption

case class GoHttpService(port: Int) extends LazyLogging:

  implicit def intEncoder: EntityEncoder[IO, Int] = jsonEncoderOf[IO, Int]

  private val goService = HttpRoutes.of[IO] {
    case GET -> Root / "new" / IntVar(boardSize) =>
      StartNewGame(boardSize).response
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
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(port).getOrElse(throw IllegalArgumentException(s"invalid port $port")))
      .withHttpApp(httpApp)
      .build

