package go3d

import go3d.probe.Report.decimal

import scala.annotation.tailrec
import scala.util.Random

/** Times what self-play calls on the rules engine: [[Game.possibleMoves]], [[Game.makeMove]],
  * [[Game.score]] and [[Game.isOver]] on mid-game and late-game positions, and a random playout
  * from the start, per move. Each measurement first runs as long as it is measured, to warm up the
  * JIT. The positions and the playout come from random games with a fixed seed.
  *
  * {{{
  * sbt "Test/runMain go3d.EngineBenchmark 5 7 9"
  * }}}
  */
object EngineBenchmark:

  private val DefaultSizes = List(5, 7, 9)
  private val MillisPerMeasurement = 2000L
  private val Seed = 1L
  // the positions taken from a random game, by the share of the move limit played to reach them
  private val Stages = List("mid-game" -> 0.5, "late game" -> 0.9)

  def main(args: Array[String]): Unit =
    val sizes = if args.isEmpty then DefaultSizes else args.toList.map(_.toInt)
    println(report(sizes, MillisPerMeasurement))

  /** The table of the time per call in microseconds, each measured for `millis`. */
  def report(sizes: List[Int], millis: Long): String =
    val header = List("| Size | Operation | Position | µs per call |", "|---|---|---|---|")
    (header ++ sizes.flatMap(rows(_, millis))).mkString("\n")

  private def rows(size: Int, millis: Long): List[String] =
    val limit = size * size * size
    val playout = randomGame(size, limit)
    val length = playout.moves.length
    val perMove = decimal(perCall(millis, length)(_ => { val _ = randomGame(size, limit) }), 1)
    val playoutRow = s"| $size | random playout, per move | $length moves | $perMove |"
    playoutRow :: Stages.flatMap((stage, share) => positionRows(size, stage, share, millis))

  private def positionRows(size: Int, stage: String, share: Double, millis: Long): List[String] =
    val game = randomGame(size, (share * size * size * size).toInt)
    val color = game.moveColor
    val moves = game.possibleMoves(color).map(Move(_, color)).toArray
    val operations = List[(String, Int => Unit)](
      "possibleMoves" -> (_ => { val _ = game.possibleMoves(color) }),
      "makeMove" -> (call => { val _ = game.makeMove(moves(call % moves.length)) }),
      "score" -> (_ => { val _ = game.score }),
      "isOver" -> (_ => { val _ = game.isOver })
    )
    operations.map { (name, operation) =>
      val time = decimal(perCall(millis, 1)(operation), 1)
      s"| $size | $name | $stage, ${game.moves.length} moves | $time |"
    }

  /** The mean time in microseconds per unit of `operation`, which does `units` units in a call and
    * gets the number of the call. It runs for `millis` to warm up, then for `millis` measured.
    */
  private def perCall(millis: Long, units: Int)(operation: Int => Unit): Double =
    @tailrec
    def loop(until: Long, calls: Int): Int =
      if System.nanoTime >= until then calls
      else
        operation(calls)
        loop(until, calls + 1)
    val _ = loop(System.nanoTime + millis * 1_000_000L, 0)
    val started = System.nanoTime
    val calls = loop(started + millis * 1_000_000L, 0)
    (System.nanoTime - started) / 1000.0 / calls / units

  /** A game of random legal moves with the fixed seed, a pass when there is none, until it has
    * `moves` moves or is over.
    */
  private def randomGame(size: Int, moves: Int): Game =
    val random = Random(Seed)
    @tailrec
    def play(game: Game): Game =
      if game.moves.length >= moves || game.isOver then game
      else
        val color = game.moveColor
        val possible = game.possibleMoves(color)
        val move: Move | Pass =
          if possible.isEmpty then Pass(color)
          else Move(possible(random.nextInt(possible.length)), color)
        play(game.makeMove(move).fold(e => sys.error(e.getMessage), identity))
    play(Game.start(size).fold(e => sys.error(e.getMessage), identity))
