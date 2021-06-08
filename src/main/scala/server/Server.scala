package go3d.server

import go3d.{Color, Game}
import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.io.Source
import io.circe.parser._

object GoServer:
  val newRoute = "/new/*"
  val registerRoute = "/register/*"
  val statusRoute = "/status/*"
  val setRoute = "/set/*"
  val handler = new ServletHandler()

  def createServer(port: Int) = new Server(port)
  def serverPort(server: Server) =
    server.getConnectors()(0).asInstanceOf[NetworkConnector].getLocalPort

  def loadGames(baseDir: String): Unit =
    Io.init(baseDir)
    for saveFile <- Io.getListOfFiles(".json") do
      val source = Source.fromFile(saveFile)
      val fileContents = source.getLines.mkString
      source.close()
      val result = decode[SaveGame](fileContents)
      if result.isLeft then println(result.left.getOrElse(null).getMessage)
      else restoreGame(result.getOrElse(null))
    println(Games)


  def run(port: Int = 3333): Unit =
    val goServer = createServer(port)
    goServer.setHandler(handler)
    handler.addServletWithMapping(classOf[NewGameServlet], newRoute)
    handler.addServletWithMapping(classOf[RegisterPlayerServlet], registerRoute)
    handler.addServletWithMapping(classOf[StatusServlet], statusRoute)
    handler.addServletWithMapping(classOf[SetServlet], setRoute)
    goServer.start()
    println(s"Server started on ${serverPort(goServer)} with routes: $newRoute, $registerRoute, $statusRoute")
    goServer.join()

