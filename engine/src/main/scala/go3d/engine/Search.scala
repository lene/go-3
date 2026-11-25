package go3d.engine

import go3d.Game
import go3d.Move
import go3d.Pass
import go3d.client.{MoveEngine, MoveEngineError}

import scala.annotation.tailrec
import scala.util.{Failure, Random, Success, Try}

/** A PUCT tree search on the Scala `Game` with a fixed number of simulations per move.
  *
  * The priors of a node are the softmax of the network's logits over its legal actions
  * (`Game.possibleMoves` and the pass). A node of a game that is over takes its value from
  * `Game.winner` at the game's komi. Values are for the player to move and are negated one ply
  * up. A simulation follows the child that maximises `Q + c · P · √N / (1 + n)`: `Q` is the mean
  * value of the child (0 while unvisited), `P` its prior, `n` its visits and `N` the visits of
  * the node, whose own evaluation counts as one. The move played is the most visited child of
  * the root. There is no root noise and no temperature in play ([[move]]), only in self-play
  * ([[selfPlayMove]]); there is no tree reuse and no batching.
  */
final class Search(evaluator: Evaluator, simulations: Int, exploration: Double)
    extends MoveEngine:

  def move(game: Game): Try[Move | Pass] =
    if game.isOver then Failure(gameOver)
    else newNode(game).flatMap(search(_, simulations)).map(mostVisited)

  /** The move of self-play, with the root visit distribution as the policy to learn. Before the
    * simulations, the root priors are mixed with Dirichlet noise ([[Search.NoiseWeight]],
    * α = [[Search.NoiseConcentration]] / the number of legal actions), so that games differ. With
    * `sample` the move is drawn from `random` in proportion to the root visits (temperature 1),
    * else it is the most visited one. The search needs at least one simulation.
    */
  def selfPlayMove(game: Game, random: Random, sample: Boolean): Try[SelfPlayMove] =
    if game.isOver then Failure(gameOver)
    else
      newNode(game).map(withNoise(_, random)).flatMap(search(_, simulations)).map { root =>
        val move = if sample then sampled(root, random) else mostVisited(root)
        SelfPlayMove(move, visitPolicy(root, game.size))
      }

  private def gameOver: MoveEngineError =
    MoveEngineError("the game is over, there is no move to choose")

  /** `root` with the priors of its edges mixed with a Dirichlet sample. */
  private def withNoise(root: Node, random: Random): Node =
    val count = root.edges.length
    val noise = Dirichlet.sample(Search.NoiseConcentration / count, count, random)
    val weight = Search.NoiseWeight
    root.copy(edges = root.edges.zip(noise).map { (edge, eta) =>
      edge.copy(prior = (1 - weight) * edge.prior + weight * eta)
    })

  /** The move of an edge of `root` drawn in proportion to the visits of the edges. */
  private def sampled(root: Node, random: Random): Move | Pass =
    @tailrec
    def pick(index: Int, rest: Int): Move | Pass =
      val edge = root.edges(index)
      if rest < edge.visits || index == root.edges.length - 1 then edge.move
      else pick(index + 1, rest - edge.visits)
    val total = root.edges.map(_.visits).sum
    if total == 0 then mostVisited(root) else pick(0, random.nextInt(total))

  /** The share of the root visits of every action of a game of `size`: 0 for an illegal action,
    * which has no edge.
    */
  private def visitPolicy(root: Node, size: Int): IndexedSeq[Double] =
    val total = root.edges.map(_.visits).sum.toDouble
    val visits = root.edges.map(edge => Encoding.action(edge.move, size) -> edge.visits).toMap
    IndexedSeq.tabulate(Encoding.actionCount(size))(action => visits.getOrElse(action, 0) / total)

  /** The move of the most visited edge of `root`; a tie goes to the higher mean value. */
  private def mostVisited(root: Node): Move | Pass =
    import scala.math.Ordering.Double.TotalOrdering
    root.edges.maxBy(edge => (edge.visits, edge.meanValue)).move

  @tailrec
  private def search(root: Node, remaining: Int): Try[Node] =
    if remaining <= 0 then Success(root)
    else
      simulate(root) match
        case Success((next, _)) => search(next, remaining - 1)
        case Failure(error) => Failure(error)

  /** One simulation from `node`: the node with the simulation added, and its value for the
    * player to move at `node`.
    */
  private def simulate(node: Node): Try[(Node, Double)] =
    if node.isTerminal then Success((node.copy(visits = node.visits + 1), node.value))
    else
      val index = select(node)
      val edge = node.edges(index)
      val reached = edge.child.fold(
        node.game.makeMove(edge.move).flatMap(newNode).map(child => (child, child.value))
      )(simulate)
      reached.map { (child, childValue) =>
        val value = -childValue
        val visited =
          edge.copy(visits = edge.visits + 1, valueSum = edge.valueSum + value, child = Some(child))
        (node.copy(edges = node.edges.updated(index, visited), visits = node.visits + 1), value)
      }

  private def select(node: Node): Int =
    val exploring = exploration * math.sqrt(node.visits.toDouble)
    node.edges.indices.maxBy { index =>
      val edge = node.edges(index)
      edge.meanValue + exploring * edge.prior / (1 + edge.visits)
    }

  /** The node of `game`, counted as visited once by its evaluation. */
  private def newNode(game: Game): Try[Node] =
    game.winner match
      case Some(winner) =>
        Success(Node(game, if winner == game.moveColor then 1.0 else -1.0, Vector(), 1))
      case None =>
        evaluator.evaluate(game).map(evaluation =>
          Node(game, evaluation.value, edges(game, evaluation.logits), 1)
        )

  /** An edge for every legal action of `game`, with the softmax of its logit as prior. */
  private def edges(game: Game, logits: IndexedSeq[Float]): Vector[Edge] =
    val color = game.moveColor
    val actions: Vector[Move | Pass] =
      game.possibleMoves(color).toVector.map(Move(_, color)) :+ Pass(color)
    val actionLogits = actions.map(action => logits(Encoding.action(action, game.size)).toDouble)
    val maxLogit = actionLogits.max
    val weights = actionLogits.map(logit => math.exp(logit - maxLogit))
    val total = weights.sum
    actions.zip(weights).map((action, weight) => Edge(action, weight / total, 0, 0.0, None))

object Search:
  /** The exploration constant `c` of PUCT. */
  val Exploration: Double = 1.5

  /** The weight ε of the Dirichlet noise in the root priors of self-play. */
  val NoiseWeight: Double = 0.25

  /** α times the number of legal actions for the Dirichlet noise of self-play: α = 10 / n. */
  val NoiseConcentration: Double = 10.0

  def apply(evaluator: Evaluator, simulations: Int): Search =
    new Search(evaluator, simulations, Exploration)

/** A move of self-play and the policy to learn there: the share of the root visits of each of the
  * size³ + 1 actions, in the action order of [[Encoding]].
  */
final case class SelfPlayMove(move: Move | Pass, policy: IndexedSeq[Double])

/** A position of the search tree. `value` is its evaluation for the player to move: the
  * network's, or the result of a game that is over. A node of a game that is over has no edges.
  */
private final case class Node(game: Game, value: Double, edges: Vector[Edge], visits: Int):
  def isTerminal: Boolean = edges.isEmpty

/** A move from a node: its prior, its visits and the sum of their values for the player who
  * makes the move, and the node it leads to once visited.
  */
private final case class Edge(
    move: Move | Pass,
    prior: Double,
    visits: Int,
    valueSum: Double,
    child: Option[Node]
):
  def meanValue: Double = if visits == 0 then 0.0 else valueSum / visits
