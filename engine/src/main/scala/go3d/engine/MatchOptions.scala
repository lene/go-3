package go3d.engine

import go3d.engine.Cli.check
import org.rogach.scallop.*

import scala.util.Try

/** What a promotion match does: `games` games between the candidate for generation `generation`
  * of size `size` and the promoted generation before it, with `simulations` per move, from the
  * model store `modelStore` on `device`. The candidate is promoted when it wins more than
  * `threshold` games. `seed` and the game number make each game; `parallel` games are played at
  * the same time.
  */
final case class MatchOptions(
    size: Int,
    generation: Int,
    games: Int,
    simulations: Int,
    threshold: Int,
    seed: Long,
    parallel: Int,
    modelStore: String,
    device: Device
):
  /** The promoted generation that the candidate plays. */
  def opponent: Int = generation - 1

object MatchOptions:
  val DefaultGames: Int = 100

  /** The candidate must win more than this many games: the gating value of AlphaZero Go. */
  val DefaultThreshold: Int = 55

  val Usage: String =
    """Usage: sbt "engine/runMain go3d.engine.Match --size 5 --generation G [options]"
      |  --size 5             board size (only 5 for now)
      |  --generation G       the candidate in <store>/<size>/candidates/G/ plays generation G-1
      |  --games N            games, an even number: each side is Black in half (default 100)
      |  --simulations N      search simulations per move (default 200)
      |  --threshold N        the candidate is promoted with more than N wins (default 55)
      |  --seed N             seed that reproduces a match on the CPU (default 1)
      |  --parallel N         games played at the same time, sharing the models (default 1)
      |  --model-store DIR    directory of the model generations (default models)
      |  --device cuda|cpu    where the networks run; cuda falls back to the CPU with a warning
      |                       when the CUDA provider cannot load (default cuda)""".stripMargin

  /** Whether `args` ask for the usage, as scallop's `--help` does. */
  def asksForHelp(args: Seq[String]): Boolean = Cli.asksForHelp(Arguments(args))

  /** The options in `args`, or the reason they are not valid. */
  def parse(args: Seq[String]): Either[String, MatchOptions] =
    Try(Arguments(args)).toEither.left.map(_.getMessage).flatMap { arguments =>
      val sizes = SelfPlayOptions.Sizes
      for
        size <-
          check(arguments.size(), sizes.contains, s"size must be one of ${sizes.mkString(", ")}")
        generation <- check(arguments.generation(), _ >= 1, "generation must be 1 or more")
        games <- check(
          arguments.games(),
          games => games >= 2 && games % 2 == 0,
          "games must be an even number from 2"
        )
        simulations <- check(arguments.simulations(), _ >= 1, "simulations must be 1 or more")
        threshold <- check(
          arguments.threshold(),
          threshold => threshold >= 0 && threshold < games,
          s"threshold must be from 0 to ${games - 1}, below the games"
        )
        parallel <- check(arguments.parallel(), _ >= 1, "parallel must be 1 or more")
        device <- Device.named(arguments.device()).toRight(
          s"device ${arguments.device()} is not one of cuda and cpu"
        )
      yield MatchOptions(
        size,
        generation,
        games,
        simulations,
        threshold,
        arguments.seed(),
        parallel,
        arguments.modelStore(),
        device
      )
    }

  /** The command line as scallop reads it; an error is thrown, for [[parse]] to return it. */
  private class Arguments(arguments: Seq[String]) extends ScallopConf(arguments):
    val size = opt[Int](required = true)
    val generation = opt[Int](required = true)
    val games = opt[Int](default = Some(DefaultGames))
    val simulations = opt[Int](default = Some(SelfPlayOptions.DefaultSimulations))
    val threshold = opt[Int](default = Some(DefaultThreshold))
    val seed = opt[Long](default = Some(SelfPlayOptions.DefaultSeed))
    val parallel = opt[Int](default = Some(1))
    val modelStore = opt[String](default = Some(SelfPlayOptions.DefaultModelStore))
    val device = opt[String](default = Some(SelfPlayOptions.DefaultDevice.name))
    verify()

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def onError(e: Throwable): Unit = throw e
