package go3d.server

import go3d.GoException

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.circe.syntax.EncoderOps

abstract class BaseServlet extends HttpServlet with ServletOutput:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    try
      val requestInfo = RequestInfo(request, maxRequestLength)
      output = generateOutput(requestInfo, response).asJson.noSpaces
    catch
      case e: RequestTooLong => error(response, e, HttpServletResponse.SC_REQUEST_URI_TOO_LONG)
      case e: go3d.BadBoardSize => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: go3d.BadColor => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: DuplicateColor => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: NotReadyToSet => error(response, e, HttpServletResponse.SC_BAD_REQUEST)
      case e: NonexistentGame => error(response, e, HttpServletResponse.SC_NOT_FOUND)
      case e: AuthorizationError => error(response, e, HttpServletResponse.SC_UNAUTHORIZED)
      case e: ServerException => error(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      case e: GoException => error(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR)

    response.getWriter.println(output)

def error(response: HttpServletResponse, e: GoException, statusCode: Int): ErrorResponse =
  response.setStatus(statusCode)
  ErrorResponse(s"${e.getClass.getSimpleName}: $e")
