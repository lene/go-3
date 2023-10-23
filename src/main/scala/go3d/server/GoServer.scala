package go3d.server

import go3d.{Black, Game, Move}
import org.eclipse.jetty.server.{NetworkConnector, Server}
import org.eclipse.jetty.servlet.ServletHandler
import com.typesafe.scalalogging.LazyLogging
import org.rogach.scallop._

import java.security.SecureRandom
import scala.annotation.tailrec

// http4s
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import cats.syntax.all._
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.server.Router

object GoServer extends LazyLogging:

  private val DefaultPort = 6030 // "Go3D"
  private val newRoute = "/new/*"
  private val registerRoute = "/register/*"
  private val statusRoute = "/status/*"
  private val setRoute = "/set/*"
  private val passRoute = "/pass/*"
  private val openGamesRoute = "/openGames"
  private val healthRoute = "/health"

  import org.http4s.circe.jsonEncoderOf
  implicit def healthEncoder: EntityEncoder[IO, Int] = jsonEncoderOf[IO, Int]
  private def getHealth: Int = 1

  private val goService = HttpRoutes.of[IO] {
    case GET -> Root / "new" / IntVar(boardSize) => NewGameHandler(boardSize).response
    case request @ GET -> Root / "register" / gameId / color =>
      RegisterPlayerHandler(gameId, color(0), request).response
    case request @ GET -> Root / "status" / gameId => StatusHandler(gameId, request).response
    case request @ GET -> Root / "status" / gameId / "d" => StatusHandler(gameId, request).response
    case request @ GET -> Root / "set" / gameId / IntVar(x) / IntVar(y) / IntVar(z) =>
      SetHandler(gameId, request, x, y, z).response
    case request @ GET -> Root / "set" / gameId / IntVar(x) / IntVar(y) / IntVar(z) / "d" =>
      SetHandler(gameId, request, x, y, z).response
    case request @ GET -> Root / "pass" / gameId => PassHandler(gameId, request).response
    case request @ GET -> Root / "pass" / gameId / "d" => PassHandler(gameId, request).response
    case GET -> Root / "openGames" => OpenGamesHandler().response
    case GET -> Root / "health" => IO(getHealth).flatMap(Ok(_))
  }

  private val httpApp = Router("/" -> goService).orNotFound
  def server(port: Int): Resource[IO, org.http4s.server.Server] =
    EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(Port.fromInt(port).getOrElse(throw new Exception(s"invalid port $port")))
    .withHttpApp(httpApp)
    .build

  def createServer(port: Int): Server =
    val jetty = new Server(port)
    val handler = new ServletHandler()
    jetty.setHandler(handler)
    handler.addServletWithMapping(classOf[NewGameServlet], newRoute)
    handler.addServletWithMapping(classOf[RegisterPlayerServlet], registerRoute)
    handler.addServletWithMapping(classOf[StatusServlet], statusRoute)
    handler.addServletWithMapping(classOf[SetServlet], setRoute)
    handler.addServletWithMapping(classOf[PassServlet], passRoute)
    handler.addServletWithMapping(classOf[OpenGamesServlet], openGamesRoute)
    handler.addServletWithMapping(classOf[HealthServlet], healthRoute)
    jetty

  private def serverPort(server: Server): Int =
    server.getConnectors()(0).asInstanceOf[NetworkConnector].getLocalPort

  private def loadGames(baseDir: String): Unit = Games.loadGames(baseDir)

  private def run(port: Int = DefaultPort): Unit =
    val goServer = createServer(port)
    goServer.start()
    logger.info(s"Server started on ${serverPort(goServer)} with routes:")
    logger.info(
      s"$newRoute, $registerRoute, $statusRoute, $setRoute, $passRoute, $openGamesRoute, $healthRoute"
    )
    goServer.join()

  def main(args: Array[String]): Unit =

    val DefaultSaveDir = "saves"

    class Conf(args: Seq[String]) extends ScallopConf(args):
      val benchmark: ScallopOption[Int] = opt[Int](descr = "Benchmark game of given size")
      val printStepSize: ScallopOption[Int] = opt[Int](
        default = Some(100), descr = "Print information every N steps"
      )
      val port: ScallopOption[Int] = opt[Int](
        default = Some(DefaultPort), descr = "Port to listen on"
      )
      val saveDir: ScallopOption[String] = opt[String](
        default = Some(DefaultSaveDir), descr = "Directory to save games to"
      )
      conflicts(benchmark, List(port, saveDir))
      dependsOnAll(printStepSize, List(benchmark))
      verify()

    def randomGame(size: Int, print_step_size: Int): Unit =
      val random = new SecureRandom()
      var game = Game.start(size)
      var color = Black
      val startTime = System.nanoTime()
      var startTimeForMoves = startTime
      while game.possibleMoves(color).nonEmpty && game.moves.length <= size*size*size do
        val move = Move(game.possibleMoves(color)(random.nextInt(game.possibleMoves(color).length)), color)
        game = game.makeMove(move)
        if game.moves.length % print_step_size == 0 || game.moves.length == size*size*size then
          val stepMs = (System.nanoTime()-startTimeForMoves)/1000000
          logger.info(s"${game.moves.length}/${size*size*size} (${stepMs/print_step_size}ms/move)")
          startTimeForMoves = System.nanoTime()
        color = !color
      val totalSeconds = (System.nanoTime()-startTime)/1000000000.0
      logger.info(s"overall: ${totalSeconds}s, ${totalSeconds*1000.0/(size*size*size)}ms/move")
      logger.info(game.toString)
      logger.info(game.score.toString)

    val conf = Conf(args.toList)
    if conf.benchmark.isSupplied then randomGame(conf.benchmark(), conf.printStepSize())
    else
      val port = conf.port()
      val saveDir = conf.saveDir()
      logger.info(s"Starting server on port $port, saving games to $saveDir")
      GoServer.loadGames(saveDir)
      server(port+1).allocated.unsafeRunSync()
      GoServer.run(port)

