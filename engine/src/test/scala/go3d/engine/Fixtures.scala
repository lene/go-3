package go3d.engine

import go3d.{Black, Game, Move, Pass, White}
import org.scalatest.TryValues.*

import java.nio.file.{Path, Paths}
import scala.util.{Success, Try}

/** The model store with the committed fixture: generation 0 of size 5, 1 block of 4 channels,
  * made with `export-generation --size 5 --generation 0 --blocks 1 --channels 4`.
  */
def fixtureStore: Path = Paths.get(classOf[TestOnnxModel].getResource("/models").toURI)

def fixtureModelPath: Path = OnnxModel.path(fixtureStore, 5, 0)

/** The seed of the run that wrote the committed fixture record: the one game of size 5 that
  * generation 0, played by a uniform [[FakeEvaluator]], plays with 6 simulations per move, a game
  * with a capture and passes that two passes end. The Python tests in `training/` read the
  * record, so it pins the record format that training reads.
  */
val fixtureRecordSeed: Long = 9L

/** The records of the committed fixture: `<size>/<generation>/<game id>.json`. */
def fixtureRecords: Path = Paths.get(classOf[TestOnnxModel].getResource("/selfplay").toURI)

/** The worked example of `docs/model-contract.md`: size 5, Black at (1,1,1), White at (3,3,3). */
def workedExample: Game =
  Game.start(5).flatMap(_.makeMove(Move(1, 1, 1, Black))).flatMap(
    _.makeMove(Move(3, 3, 3, White))
  ).success.value

/** The game after `moves` from an empty board of `size` at `komi`. */
def played(size: Int, komi: Double, moves: (Move | Pass)*): Game =
  moves.foldLeft(Game.start(size, komi))((game, move) => game.flatMap(_.makeMove(move)))
    .success.value

/** The actions of the moves that the player to move in `game` may make, the pass included. */
def legalActions(game: Game): Set[Int] =
  val color = game.moveColor
  val moves: List[Move | Pass] = game.possibleMoves(color).map(Move(_, color)) :+ Pass(color)
  moves.map(Encoding.action(_, game.size)).toSet

/** Gives every action the logit of `logits`, 0 for the others, and the same value everywhere. */
class FakeEvaluator(logits: Map[Int, Float], value: Double) extends Evaluator:
  def evaluate(game: Game): Try[Evaluation] =
    val actions = Encoding.actionCount(game.size)
    Success(Evaluation(IndexedSeq.tabulate(actions)(logits.getOrElse(_, 0f)), value))
