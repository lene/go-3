package go3d.server

import go3d.{Color, Game}

import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

object GoServer:
  val registerRoute = "/register/*"
  val newRoute = "/new/*"
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
