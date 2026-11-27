package go3d.engine

import go3d.Game

import scala.annotation.tailrec
import scala.util.{Failure, Random, Success, Try}

/** A game of self-play: the game as it ended, the policy to learn at each of its moves
  * ([[SelfPlayMove.policy]]), and the time spent in the search and, of that, in the network.
  */
final case class SelfPlayGame(
    game: Game,
    policies: Vector[IndexedSeq[Double]],
    searchNanos: Long,
    networkNanos: Long
)

object SelfPlayGame:
  /** The share of the board's points that are opening moves, see [[openingMoves]]. */
  private val OpeningDivisor = 8

  /** The moves at the start of a game of `size` that are drawn in proportion to the root visits,
    * so that games differ: size³ / 8, rounded down (15 on size 5). The later moves are the most
    * visited ones.
    */
  def openingMoves(size: Int): Int = Encoding.pointCount(size) / OpeningDivisor

  /** `evaluator` plays itself from `start` until the game is over, with `simulations` per move.
    * The same `seed` gives the same game when `evaluator` gives the same evaluations.
    */
  def play(evaluator: Evaluator, start: Game, simulations: Int, seed: Long): Try[SelfPlayGame] =
    val timed = TimedEvaluator(evaluator)
    playOut(Search(timed, simulations), Random(seed), Progress(start, Vector(), 0L)).map {
      played => SelfPlayGame(played.game, played.policies, played.searchNanos, timed.nanos)
    }

  private final case class Progress(
      game: Game,
      policies: Vector[IndexedSeq[Double]],
      searchNanos: Long
  )

  @tailrec
  private def playOut(search: Search, random: Random, progress: Progress): Try[Progress] =
    val game = progress.game
    if game.isOver then Success(progress)
    else
      val started = System.nanoTime
      val opening = game.moves.length < openingMoves(game.size)
      val chosen = search.selfPlayMove(game, random, sample = opening, noise = true)
      val nanos = System.nanoTime - started
      chosen.flatMap(choice => game.makeMove(choice.move).map(_ -> choice.policy)) match
        case Success((next, policy)) =>
          val policies = progress.policies :+ policy
          playOut(search, random, Progress(next, policies, progress.searchNanos + nanos))
        case Failure(error) => Failure(error)
