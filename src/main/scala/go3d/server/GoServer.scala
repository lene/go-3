package go3d.server

import go3d.{Color, Game}
import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.circe.syntax.EncoderOps

object GoServer:

  val DefaultPort = 6030 // "Go3D"
  val newRoute = "/new/*"
  val registerRoute = "/register/*"
  val statusRoute = "/status/*"
  val setRoute = "/set/*"
  val passRoute = "/pass/*"
  val openGamesRoute = "/openGames"
  val healthRoute = "/health"

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
    return server

  def serverPort(server: Server): Int =
    server.getConnectors()(0).asInstanceOf[NetworkConnector].getLocalPort

  def loadGames(baseDir: String): Unit =
    Io.init(baseDir)
    for saveFile <- Io.getListOfFiles(".json") do
      try
        restoreGame(readGame(saveFile))
      catch
        case e: ReadSaveGameError => println(s"${saveFile.getName}: ${e.message}")
        case e: JsonDecodeError => println(s"${saveFile.getName}: ${e.message}")
    println(Games)

  def run(port: Int = DefaultPort): Unit =
    val goServer = createServer(port)
    goServer.start()
    println(s"Server started on ${serverPort(goServer)} with routes:")
    println(s"$newRoute, $registerRoute, $statusRoute, $setRoute, $passRoute, $openGamesRoute, $healthRoute")
    goServer.join()
