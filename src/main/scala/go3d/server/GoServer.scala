package go3d.server

import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.LazyLogging
import java.security.SecureRandom
import org.rogach.scallop._

import go3d.{Black, Game, Move}
import go3d.server.http4s.GoHttpService

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
      val shutdown = GoHttpService(port).server.allocated.unsafeRunSync()._2
      while true do
        Thread.sleep(1000)
        if false then shutdown.unsafeRunSync()