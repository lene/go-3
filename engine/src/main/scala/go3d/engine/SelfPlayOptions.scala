package go3d.engine

import org.rogach.scallop.*

import scala.util.Try

/** What a self-play run does: `games` games of generation `generation` of size `size` against
  * itself, with `simulations` per move, from the model store `modelStore` on `device`. The
  * records go to `<out>/<size>/<generation>/`; `seed` and the game number make each game.
  * `parallel` games are played at the same time.
  */
final case class SelfPlayOptions(
    size: Int,
    generation: Int,
    games: Int,
    simulations: Int,
    seed: Long,
    parallel: Int,
    modelStore: String,
    out: String,
    device: Device
)

object SelfPlayOptions:
  /** The board sizes of self-play: 7 and 9 follow once the size-5 loop works. */
  val Sizes: List[Int] = List(5)

  val DefaultSimulations: Int = 200
  val DefaultSeed: Long = 1L
  val DefaultModelStore: String = "models"
  val DefaultOut: String = "selfplay"
  val DefaultDevice: Device = Device.Cuda

  val Usage: String =
    """Usage: sbt "engine/runMain go3d.engine.SelfPlay --size 5 --generation G --games N [options]"
      |  --size 5             board size (only 5 for now)
      |  --generation G       the model generation that plays itself
      |  --games N            games to play, from 1
      |  --simulations N      search simulations per move (default 200)
      |  --seed N             seed that reproduces a run on the CPU with --parallel 1 (default 1)
      |  --parallel N         games played at the same time, sharing the model (default 1)
      |  --model-store DIR    directory of the model generations (default models)
      |  --out DIR            records go to DIR/<size>/<generation>/ (default selfplay)
      |  --device cuda|cpu    where the network runs; cuda falls back to the CPU with a warning
      |                       when the CUDA provider cannot load (default cuda)""".stripMargin

  /** Whether `args` ask for the usage, as scallop's `--help` does. */
  def asksForHelp(args: Seq[String]): Boolean =
    Try(Arguments(args)).failed.toOption.exists {
      case _: exceptions.Help => true
      case _ => false
    }

  /** The options in `args`, or the reason they are not valid. */
  def parse(args: Seq[String]): Either[String, SelfPlayOptions] =
    Try(Arguments(args)).toEither.left.map(_.getMessage).flatMap { arguments =>
      for
        size <-
          check(arguments.size(), Sizes.contains, s"size must be one of ${Sizes.mkString(", ")}")
        generation <- check(arguments.generation(), _ >= 0, "generation must be 0 or more")
        games <- check(arguments.games(), _ >= 1, "games must be 1 or more")
        simulations <- check(arguments.simulations(), _ >= 1, "simulations must be 1 or more")
        parallel <- check(arguments.parallel(), _ >= 1, "parallel must be 1 or more")
        device <- Device.named(arguments.device()).toRight(
          s"device ${arguments.device()} is not one of cuda and cpu"
        )
      yield SelfPlayOptions(
        size,
        generation,
        games,
        simulations,
        arguments.seed(),
        parallel,
        arguments.modelStore(),
        arguments.out(),
        device
      )
    }

  private def check(value: Int, valid: Int => Boolean, rule: String): Either[String, Int] =
    Either.cond(valid(value), value, s"$rule, not $value")

  /** The command line as scallop reads it; an error is thrown, for [[parse]] to return it. */
  private class Arguments(arguments: Seq[String]) extends ScallopConf(arguments):
    val size = opt[Int](required = true)
    val generation = opt[Int](required = true)
    val games = opt[Int](required = true)
    val simulations = opt[Int](default = Some(DefaultSimulations))
    val seed = opt[Long](default = Some(DefaultSeed))
    val parallel = opt[Int](default = Some(1))
    val modelStore = opt[String](default = Some(DefaultModelStore))
    val out = opt[String](default = Some(DefaultOut))
    val device = opt[String](default = Some(DefaultDevice.toString.toLowerCase))
    verify()

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def onError(e: Throwable): Unit = throw e
