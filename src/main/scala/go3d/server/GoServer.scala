package go3d.server

import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.LazyLogging
import go3d.Black
import go3d.Game
import go3d.Move
import go3d.server.http4s.GoHttpService
import org.rogach.scallop._

import java.security.SecureRandom

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

    def randomGame(size: Int, print_step_size: Int): Unit =
      val random = new SecureRandom()
      var game = Game.start(size).get
      var color = Black
      val startTime = System.nanoTime()
      var startTimeForMoves = startTime
      while game.possibleMoves(color).nonEmpty && game.moves.length <= size*size*size do
        val move = Move(game.possibleMoves(color)(random.nextInt(game.possibleMoves(color).length)), color)
        game = game.makeMove(move).get
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