package go3d.server

import go3d.{Color, Game}
import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler
import com.typesafe.scalalogging.LazyLogging

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.circe.syntax.EncoderOps

object GoServer extends LazyLogging:

  private val DefaultPort = 6030 // "Go3D"
  private val newRoute = "/new/*"
  private val registerRoute = "/register/*"
  private val statusRoute = "/status/*"
  private val setRoute = "/set/*"
  private val passRoute = "/pass/*"
  private val openGamesRoute = "/openGames"
  private val healthRoute = "/health"

  def createServer(port: Int): Server =
    val server = new Server(port)
    val handler = new ServletHandler()
    server.setHandler(handler)
    handler.addServletWithMapping(classOf[NewGameServlet], newRoute)
    handler.addServletWithMapping(classOf[RegisterPlayerServlet], registerRoute)
    handler.addServletWithMapping(classOf[StatusServlet], statusRoute)
    handler.addServletWithMapping(classOf[SetServlet], setRoute)
    handler.addServletWithMapping(classOf[PassServlet], passRoute)
    handler.addServletWithMapping(classOf[OpenGamesServlet], openGamesRoute)
    handler.addServletWithMapping(classOf[HealthServlet], healthRoute)
    server

  def serverPort(server: Server): Int =
    server.getConnectors()(0).asInstanceOf[NetworkConnector].getLocalPort

  def loadGames(baseDir: String): Unit = Games.loadGames(baseDir)

  def run(port: Int = DefaultPort): Unit =
    val goServer = createServer(port)
    goServer.start()
    logger.info(s"Server started on ${serverPort(goServer)} with routes:")
    logger.info(
      s"$newRoute, $registerRoute, $statusRoute, $setRoute, $passRoute, $openGamesRoute, $healthRoute"
    )
    goServer.join()
