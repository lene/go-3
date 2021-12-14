package go3d.server

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class HealthServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    response.getWriter.print(1)
