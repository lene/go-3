package go3d.engine

import go3d.Game

import java.util.concurrent.atomic.LongAdder
import scala.util.Try

/** `evaluator`, with the time spent in it added up: the network's share of the search time. */
final class TimedEvaluator(evaluator: Evaluator) extends Evaluator:
  private val elapsed = LongAdder()

  def evaluate(game: Game): Try[Evaluation] =
    val started = System.nanoTime
    val evaluation = evaluator.evaluate(game)
    elapsed.add(System.nanoTime - started)
    evaluation

  /** The nanoseconds spent in `evaluator` so far. */
  def nanos: Long = elapsed.sum
