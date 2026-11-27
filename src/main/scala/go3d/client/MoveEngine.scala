package go3d.client

import go3d.Game
import go3d.Move
import go3d.Pass

import java.util.ServiceLoader
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Try}

/** Chooses the move of the player to move in a game, as a strategy that is not a [[SetStrategy]]
  * heuristic does, such as a model generation of the AlphaZero engine.
  */
trait MoveEngine:
  def move(game: Game): Try[Move | Pass]

/** The options of `BotClient` that a [[MoveEngine]] reads: the directory of the model store and
  * the search simulations per move.
  */
final case class MoveEngineSettings(modelStore: String, simulations: Int)

/** Creates the [[MoveEngine]] of a strategy such as `model:3`. Providers are found through
  * [[java.util.ServiceLoader]] by their [[prefix]], so the root project, which never depends on
  * the engine subproject, plays an engine only when the engine is on the classpath.
  */
trait MoveEngineProvider:
  /** The part of the strategy before [[MoveEngine.Separator]], e.g. `model`. */
  def prefix: String

  /** The engine for `game`, from the part of the strategy after [[MoveEngine.Separator]]. */
  def create(argument: String, settings: MoveEngineSettings, game: Game): Try[MoveEngine]

/** A move engine that cannot be found or created, with a message for the user. */
class MoveEngineError(message: String) extends RuntimeException(message):
  /** The error with `message` that `cause`, e.g. a failure of the inference library, led to. */
  def this(message: String, cause: Throwable) =
    this(message)
    val _ = initCause(cause)

object MoveEngine:
  /** Separates the provider prefix of an engine strategy from its argument, as in `model:3`. */
  val Separator: Char = ':'

  /** Whether `strategy` names an engine rather than a [[SetStrategy]] heuristic. */
  def isEngineStrategy(strategy: String): Boolean = strategy.contains(Separator)

  /** The engine of `strategy` for `game`, from the provider of its prefix. */
  def create(strategy: String, settings: MoveEngineSettings, game: Game): Try[MoveEngine] =
    val (prefix, argument) = strategy.span(_ != Separator)
    Try(ServiceLoader.load(classOf[MoveEngineProvider]).iterator.asScala.toList)
      .flatMap(_.find(_.prefix == prefix).fold[Try[MoveEngine]](Failure(notFound(prefix)))(
        _.create(argument.drop(1), settings, game)
      ))

  private def notFound(prefix: String): MoveEngineError =
    val run = "sbt \"engine/runMain go3d.client.BotClient ...\""
    MoveEngineError(s"no move engine for strategy '$prefix': run BotClient with the engine, $run")
