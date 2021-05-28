package server

import go3d._
import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler
import ujson._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class RegisterPlayerServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)
    val output = ujson.Arr(
      ujson.Obj(
        "hello" -> "world",
      ), true
    )
    response.getWriter.println(ujson.write(output))

class NewGameServlet extends HttpServlet:
  override protected def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit =
    def gameId = IdGenerator.getId

    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)
    val output = ujson.Arr(
      ujson.Obj("id" -> gameId),
      //          ujson.Obj("game" -> newGame(5).goban.stones),
    )
    response.getWriter.println(ujson.write(output))

object GoServer:
  val registerRoute = "/register"
  val newRoute = "/new"
  val handler = new ServletHandler()

  def createServer(port: Int) = new Server(port)
  def serverPort(server: Server) =
    server.getConnectors()(0).asInstanceOf[NetworkConnector].getLocalPort

  def run(port: Int = 3333): Unit =
    val goServer = createServer(port)
    goServer.setHandler(handler)
    handler.addServletWithMapping(classOf[RegisterPlayerServlet], registerRoute)
    handler.addServletWithMapping(classOf[NewGameServlet], newRoute)
    goServer.start()
    println(s"Server started on ${serverPort(goServer)} with routes: $newRoute, $registerRoute")
    goServer.join()
