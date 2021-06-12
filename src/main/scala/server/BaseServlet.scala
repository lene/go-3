package go3d.server

import go3d.GoException

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.circe.syntax.EncoderOps

abstract class BaseServlet extends HttpServlet with ServletOutput:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    var output = ErrorResponse("i have no idea what happened").asJson.noSpaces
    response.setStatus(HttpServletResponse.SC_OK)
    val requestInfo = RequestInfo(request)

    output = generateOutput(requestInfo, response).asJson.noSpaces

    response.getWriter.println(output)

def error(response: HttpServletResponse, e: GoException, statusCode: Int): ErrorResponse =
  response.setStatus(statusCode)
  ErrorResponse(s"${e.getClass.getSimpleName}: $e")
