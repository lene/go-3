package go3d.server

import go3d.{Black, Game, Move}
import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler
import com.typesafe.scalalogging.LazyLogging

import java.security.SecureRandom
import scala.annotation.tailrec

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

  def main(args: Array[String]): Unit =

    type OptionMap = Map[String, Int | String]
    val DefaultSaveDir = "saves"

    def randomGame(size: Int): Unit =
      val random = new SecureRandom()
      val Step = 10
      var game = Game.start(size)
      var color = Black
      val t0 = System.nanoTime()
      var tStep0 = t0
      while game.possibleMoves(color).nonEmpty && game.moves.length <= size*size*size do
        val move = Move(game.possibleMoves(color)(random.nextInt(game.possibleMoves(color).length)), color)
        game = game.makeMove(move)
        if game.moves.length % Step == 0 || game.moves.length == size*size*size then
          val stepMs = (System.nanoTime()-tStep0)/1000000
          logger.info(s"${game.moves.length}/${size*size*size} (${stepMs/Step}ms/move)")
          tStep0 = System.nanoTime()
        color = !color
      val totalMs = (System.nanoTime()-t0)/1000000
      logger.info(s"overall: ${totalMs/1000.0}s, ${totalMs/(size*size*size)}ms/move")
      logger.info(game.toString)
      logger.info(game.score.toString)

    @tailrec
    def nextOption(map: OptionMap, list: List[String]): OptionMap =
      list match
        case Nil => map
        case "--benchmark" :: value :: tail =>
          nextOption(map ++ Map("benchmark_size" -> value.toInt), tail)
        case "--port" :: value :: tail =>
          nextOption(map ++ Map("port" -> value.toInt), tail)
        case "--save-dir" :: value :: tail =>
          nextOption(map ++ Map("save_dir" -> value), tail)
        case option :: tail =>
          if option.matches("\\d+") then
            nextOption(map ++ Map("benchmark_size" -> option.toInt), tail)
          else
            logger.error(s"Unknown option ${option.filter(_ >= ' ')}")
            System.exit(1)
            map

    val options = nextOption(Map(), args.toList)
    if options.contains("benchmark_size") then
      randomGame(options("benchmark_size").asInstanceOf[Int])
    else
      val port = options.getOrElse("port", DefaultPort).asInstanceOf[Int]
      val saveDir = options.getOrElse("save_dir", DefaultSaveDir).asInstanceOf[String]
      logger.info(s"Starting server on port $port, saving games to $saveDir")
      GoServer.loadGames(saveDir)
      GoServer.run(port)
