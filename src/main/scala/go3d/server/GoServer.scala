package go3d.server

import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.LazyLogging
import go3d.Black
import go3d.Color
import go3d.Game
import go3d.Move
import go3d.server.http4s.GoHttpService
import org.rogach.scallop._

import java.security.SecureRandom
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object GoServer extends LazyLogging:

  private val DefaultPort = 6030 // "Go3D"

  private def loadGames(baseDir: String): Unit = Games.loadGames(baseDir)

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
      val inactiveGameTimeoutMinutes: ScallopOption[Int] = opt[Int](
        default = Some(120), descr = "Minutes of inactivity before an active game is expired"
      )
      conflicts(benchmark, List(port, saveDir, inactiveGameTimeoutMinutes))
      dependsOnAll(printStepSize, List(benchmark))
      verify()

    def randomGame(size: Int, print_step_size: Int): Try[Unit] =
      val random = new SecureRandom()
      val totalMoves = size*size*size
      val startTime = System.nanoTime()

      @tailrec def play(game: Game, color: Color, startTimeForMoves: Long): Try[Game] =
        val possible = game.possibleMoves(color)
        if possible.isEmpty || game.moves.length > totalMoves then Success(game)
        else game.makeMove(Move(possible(random.nextInt(possible.length)), color)) match
          case Failure(e) => Failure(e)
          case Success(next) =>
            if next.moves.length % print_step_size == 0 || next.moves.length == totalMoves then
              val stepMs = (System.nanoTime()-startTimeForMoves)/1000000
              logger.info(s"${next.moves.length}/$totalMoves (${stepMs/print_step_size}ms/move)")
              play(next, !color, System.nanoTime())
            else play(next, !color, startTimeForMoves)

      Game.start(size).flatMap(play(_, Black, startTime)).map { game =>
        val totalSeconds = (System.nanoTime()-startTime)/1000000000.0
        logger.info(s"overall: ${totalSeconds}s, ${totalSeconds*1000.0/totalMoves}ms/move")
        logger.info(game.toString)
        logger.info(game.score.toString)
      }

    val conf = Conf(args.toList)
    if conf.benchmark.isSupplied then
      randomGame(conf.benchmark(), conf.printStepSize()).failed.foreach { e =>
        logger.error(s"benchmark failed: ${e.getMessage}")
        System.exit(1)
      }
    else
      val port = conf.port()
      val saveDir = conf.saveDir()
      val timeoutMs = conf.inactiveGameTimeoutMinutes() * 60 * 1000L
      logger.info(s"Starting server on port $port, saving games to $saveDir")
      logger.info(s"Inactive game timeout: ${conf.inactiveGameTimeoutMinutes()} minutes")
      GoServer.loadGames(saveDir)
      val shutdown = GoHttpService(port).server.allocated.unsafeRunSync()._2
      val cleanupIntervalMs = 5 * 60 * 1000L
      var lastCleanup = System.currentTimeMillis()
      while true do
        Thread.sleep(1000)
        val now = System.currentTimeMillis()
        if now - lastCleanup >= cleanupIntervalMs then
          Games.expireStaleGames(timeoutMs)
          lastCleanup = now
        if false then shutdown.unsafeRunSync()