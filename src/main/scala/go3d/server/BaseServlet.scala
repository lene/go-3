package go3d.server

import go3d.GoException
import io.circe.syntax.EncoderOps

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.util.{Failure, Success, Try}

abstract class BaseServlet extends HttpServlet with ServletOutput:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    try
      val requestInfo = RequestInfo(request, maxRequestLength)
      output = generateOutput(requestInfo, response).asJson.noSpaces
      response.getWriter.println(output)
    catch
      case e: RequestTooLong => error(response, e, HttpServletResponse.SC_REQUEST_URI_TOO_LONG)
      case e: go3d.BadBoardSize => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: go3d.BadColor => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: DuplicateColor => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: NotReadyToSet => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: NonexistentGame => error(response, e, HttpServletResponse.SC_NOT_FOUND)
      case e: AuthorizationError => error(response, e, HttpServletResponse.SC_UNAUTHORIZED)
      case e: ServerException => error(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      case e: go3d.IllegalMove => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: go3d.GameOver => error(response, e, HttpServletResponse.SC_GONE)
      case e: GoException => error(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)

def error(response: HttpServletResponse, e: GoException, statusCode: Int): Unit =
  response.setStatus(statusCode)
  response.getWriter.println(s"${e.getClass.getSimpleName}: $e")

import cats.effect.IO
import org.http4s.{Response, Status}
import org.http4s.dsl.io._
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.headers.Authorization
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
