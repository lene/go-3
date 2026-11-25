package go3d.engine

import go3d.Game

import scala.util.Try

/** The network's answer for one position: a logit for every action, in the action order of
  * [[Encoding]], and the expected result for the player to move, in [-1, 1].
  */
final case class Evaluation(logits: IndexedSeq[Float], value: Double)

/** Evaluates positions for the search: the network of a model, or a fake one in tests. */
trait Evaluator:
  def evaluate(game: Game): Try[Evaluation]
