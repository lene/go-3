package go3d.server.http4s

import cats.effect.IO
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.dsl.io._
import org.http4s.{Response, Status}

import scala.util.{Failure, Success, Try}

import go3d.server.{
  AuthorizationError, DuplicateColor, GoResponse, NonexistentGame, NotReadyToSet, ServerException,
  encodeGoResponse
}

trait HandleTrait:
  def handle: GoResponse

abstract class BaseHandler extends HandleTrait:
  def response: IO[Response[IO]] =
    Try(handle) match
      case Success(body) => Ok(body)
      case Failure(e: go3d.BadBoardSize) => BadRequest(e.getMessage)
      case Failure(e: go3d.BadColor) => BadRequest(e.getMessage)
      case Failure(e: DuplicateColor) => BadRequest(e.getMessage)
      case Failure(e: NotReadyToSet) => BadRequest(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case Failure(e: NoSuchElementException) => NotFound(e.getMessage)
      case Failure(e: NonexistentGame) => NotFound(e.getMessage)
      case Failure(e: AuthorizationError) => IO(Response[IO](status = Status.Unauthorized))
      case Failure(e: ServerException) => InternalServerError(e.getMessage)
      case Failure(e: go3d.IllegalMove) => BadRequest(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case Failure(e: go3d.GameOver) => Gone(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      case Failure(e) => InternalServerError(s"${e.getClass.getSimpleName}: ${e.getMessage}")
