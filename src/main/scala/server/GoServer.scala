package go3d.server

import go3d.{Color, Game}
import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.circe.syntax.EncoderOps

object GoServer:

  val newRoute = "/new/*"
  val registerRoute = "/register/*"
  val statusRoute = "/status/*"
  val setRoute = "/set/*"
  val passRoute = "/pass/*"

  def createServer(port: Int): Server =
    val server = new Server(port)
    val handler = new ServletHandler()
    server.setHandler(handler)
    handler.addServletWithMapping(classOf[NewGameServlet], newRoute)
    handler.addServletWithMapping(classOf[RegisterPlayerServlet], registerRoute)
    handler.addServletWithMapping(classOf[StatusServlet], statusRoute)
    handler.addServletWithMapping(classOf[SetServlet], setRoute)
    handler.addServletWithMapping(classOf[PassServlet], passRoute)
    return server

  def serverPort(server: Server) =
    server.getConnectors()(0).asInstanceOf[NetworkConnector].getLocalPort

  def loadGames(baseDir: String): Unit =
    Io.init(baseDir)
    for saveFile <- Io.getListOfFiles(".json") do
      try
        restoreGame(readGame(saveFile))
      catch case e: ReadSaveGameError => println(e.message)
    println(Games)

  def run(port: Int = 3333): Unit =
    val goServer = createServer(port)
    goServer.start()
    println(s"Server started on ${serverPort(goServer)} with routes: $newRoute, $registerRoute, $statusRoute")
    goServer.join()

def errorResponse(response: HttpServletResponse, msg: String, statusCode: Int): String =
  response.setStatus(statusCode)
  ErrorResponse(msg).asJson.noSpaces
