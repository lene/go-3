package go3d.server.http4s

import cats.effect.IO
import go3d.server.AuthorizationError
import go3d.server.DuplicateColor
import go3d.server.GoResponse
import go3d.server.NonexistentGame
import go3d.server.NotReadyToSet
import go3d.server.RateLimitExceeded
import go3d.server.ServerException
import go3d.server.encodeGoResponse
import org.http4s.Response
import org.http4s.Status
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.dsl.io._

import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait HandleTrait:
  def handle: Try[GoResponse]

abstract class BaseHandler extends HandleTrait:
  def response: IO[Response[IO]] =
    handle match
      case Success(body) => Ok(body)
      case Failure(e: go3d.BadBoardSize) => BadRequest(e.getMessage)
      case Failure(e: go3d.BadColor) => BadRequest(e.getMessage)
      case Failure(e: DuplicateColor) => BadRequest(e.getMessage)
      case Failure(e: NotReadyToSet) => BadRequest(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case Failure(e: NoSuchElementException) => NotFound(e.getMessage)
      case Failure(e: NonexistentGame) => NotFound(e.getMessage)
      case Failure(_: AuthorizationError) => IO(Response[IO](status = Status.Unauthorized))
      case Failure(_: RateLimitExceeded) => IO(Response[IO](status = Status.TooManyRequests))
      case Failure(e: ServerException) => InternalServerError(e.getMessage)
      case Failure(e: go3d.IllegalMove) => BadRequest(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case Failure(e: go3d.GameOver) => Gone(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case Failure(e) => InternalServerError(s"${e.getClass.getSimpleName}: ${e.getMessage}")
