package go3d.server.http4s

import cats.effect.{IO, Resource}
import com.comcast.ip4s.{ipv4, Port}
import org.http4s.{EntityEncoder, HttpRoutes}
import org.http4s.circe.jsonEncoderOf
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router

case class GoHttpService(port: Int):

  implicit def healthEncoder: EntityEncoder[IO, Int] = jsonEncoderOf[IO, Int]

  private def getHealth: Int = 1

  private val goService = HttpRoutes.of[IO] {
    case GET -> Root / "new" / IntVar(boardSize) => StartNewGame(boardSize).response
    case request@GET -> Root / "register" / gameId / color =>
      RegisterPlayer(gameId, color(0), request).response
    case request@GET -> Root / "status" / gameId => GetStatus(gameId, request).response
    case request@GET -> Root / "status" / gameId / "d" => GetStatus(gameId, request).response
    case request@GET -> Root / "set" / gameId / IntVar(x) / IntVar(y) / IntVar(z) =>
      DoSet(gameId, request, x, y, z).response
    case request@GET -> Root / "set" / gameId / IntVar(x) / IntVar(y) / IntVar(z) / "d" =>
      DoSet(gameId, request, x, y, z).response
    case request@GET -> Root / "pass" / gameId => DoPass(gameId, request).response
    case request@GET -> Root / "pass" / gameId / "d" => DoPass(gameId, request).response
    case GET -> Root / "openGames" => ListOpenGames().response
    case GET -> Root / "health" => IO(getHealth).flatMap(Ok(_))
  }

  private val httpApp = Router("/" -> goService).orNotFound

  def server: Resource[IO, org.http4s.server.Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(port).getOrElse(throw new Exception(s"invalid port $port")))
      .withHttpApp(httpApp)
      .build

