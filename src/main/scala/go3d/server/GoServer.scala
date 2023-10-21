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

  case class Tweet(id: Int, message: String)
  import io.circe.generic.auto._
  import org.http4s.circe._
  implicit def tweetEncoder: EntityEncoder[IO, Tweet] = jsonEncoderOf[IO, Tweet]
  implicit def tweetsEncoder: EntityEncoder[IO, Seq[Tweet]] = jsonEncoderOf[IO, Seq[Tweet]]
  private def getTweet(tweetId: Int): IO[Tweet] = IO(Tweet(tweetId, "hello world"))
  private def getPopularTweets: IO[Seq[Tweet]] = IO(Seq(Tweet(1, "hello world")))
  private val tweetService = HttpRoutes.of[IO] {
    case GET -> Root / "tweets" / "popular" => getPopularTweets.flatMap(Ok(_))
    case GET -> Root / "tweets" / IntVar(tweetId) => getTweet(tweetId).flatMap(Ok(_))
  }

  implicit def healthEncoder: EntityEncoder[IO, Int] = jsonEncoderOf[IO, Int]
  private def getHealth: Int = 1
  import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
  private val goService = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      IO(getHealth).
        flatMap(Ok(_))
    case GET -> Root / "openGames" =>
      IO(OpenGamesHandler.handle()).
        flatMap(Ok(_)).
        orElse(InternalServerError("unknown error"))
  }

  private val services = tweetService <+> goService
  private val httpApp = Router("/" -> goService, "/api" -> services).orNotFound
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

    val DefaultSaveDir = "saves"

    class Conf(args: Seq[String]) extends ScallopConf(args):
      val benchmark = opt[Int](descr = "Benchmark game of given size")
      val printStepSize = opt[Int](default = Some(100), descr = "Print information every N steps")
      val port = opt[Int](default = Some(DefaultPort), descr = "Port to listen on")
      val saveDir = opt[String](
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

