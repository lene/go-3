package go3d.engine

import cats.syntax.all.*
import go3d.{Black, Game}

import java.nio.file.{Files, Path, Paths}
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success, Try, Using}

/** Self-play: a model generation plays itself, and each game is written as a record for
  * training (`docs/self-play-records.md`).
  *
  * {{{
  * sbt "engine/runMain go3d.engine.SelfPlay --size 5 --generation 0 --games 4 --simulations 50"
  * }}}
  *
  * Every game starts at the komi of the model. The records go to
  * `<out>/<size>/<generation>/<game id>.json`, one when each game ends. At the end, the games and
  * moves played, the time per simulation in the network and in the engine, and Black's win rate
  * are printed. Options that are not valid exit with [[Cli.BadOptionsExitCode]], a model that does
  * not load, a record of the run that exists already (without `--resume`) or a game that fails
  * with [[Cli.FailureExitCode]]. `--help` prints the usage.
  */
object SelfPlay:
  def main(args: Array[String]): Unit = Cli.exit(execute(args.toIndexedSeq))

  /** Runs self-play with the options in `args` and returns the exit code. */
  def execute(args: Seq[String]): Int =
    Cli.execute(
      SelfPlayOptions.Usage,
      SelfPlayOptions.asksForHelp(args),
      SelfPlayOptions.parse(args)
    )(playWithModel)

  /** Plays with the model of `options` and returns the report. */
  private def playWithModel(options: SelfPlayOptions): Try[String] =
    for
      store <- Try(Paths.get(options.modelStore))
      path = OnnxModel.path(store, options.size, options.generation)
      model <- OnnxModel.load(path, options.size, options.device)
      summary <- Using(model)(run(options, _, model.komi)).flatten
    yield summary.report(options, model.device)

  /** The directory of the records of a run with `options`. */
  def recordDir(options: SelfPlayOptions): Path =
    Paths.get(options.out).resolve(options.size.toString).resolve(options.generation.toString)

  /** Plays the games of `options` with `evaluator` from an empty board at `komi`, `parallel` at a
    * time, and writes a record when each game ends. A run whose records would overwrite one that
    * exists fails before it plays; with `resume` it plays only the games that have no record.
    */
  def run(options: SelfPlayOptions, evaluator: Evaluator, komi: Double): Try[SelfPlaySummary] =
    val started = System.nanoTime
    for
      start <- Game.start(options.size, komi)
      numbers <- gamesToPlay(options)
      dir <- Try(Files.createDirectories(recordDir(options)))
      games <- Parallel.games(numbers, options.parallel)(
        playAndWrite(options, evaluator, start, dir, _)
      ).sequence
    yield SelfPlaySummary(games, options.simulations, System.nanoTime - started)

  /** The numbers of the games of `options` to play: those without a record with `resume`, else
    * all of them, or a failure that names the first record that exists already. The ids of a run
    * depend only on its seed, size and generation.
    */
  private def gamesToPlay(options: SelfPlayOptions): Try[Seq[Int]] =
    Try(recordDir(options)).flatMap { dir =>
      val numbers = 0 until options.games
      def recordPath(number: Int): Path = SelfPlayRecord.path(
        dir,
        SelfPlayRecord.gameId(options.seed, options.size, options.generation, number)
      )
      if options.resume then Success(numbers.filterNot(number => Files.exists(recordPath(number))))
      else
        numbers.iterator.map(recordPath).find(Files.exists(_)).fold[Try[Seq[Int]]](
          Success(numbers)
        ) { path =>
          Failure(
            IllegalStateException(s"$path exists: choose another --seed or --out, or --resume")
          )
        }
    }

  private def playAndWrite(
      options: SelfPlayOptions,
      evaluator: Evaluator,
      start: Game,
      dir: Path,
      number: Int
  ): Try[GameSummary] =
    val seed = SelfPlayRecord.gameSeed(options.seed, options.size, options.generation, number)
    val id = SelfPlayRecord.gameId(options.seed, options.size, options.generation, number)
    for
      played <- SelfPlayGame.play(evaluator, start, options.simulations, seed)
      record = SelfPlayRecord(
        options.generation,
        options.simulations,
        seed,
        played.game,
        played.policies
      )
      _ <- SelfPlayRecord.write(dir, id, record)
    yield GameSummary(
      played.game.moves.length,
      played.game.winner.contains(Black),
      played.searchNanos,
      played.networkNanos
    )

/** What the report needs of one game: its moves, whether Black won, and the time spent in the
  * search and, of that, in the network.
  */
final case class GameSummary(moves: Int, blackWon: Boolean, searchNanos: Long, networkNanos: Long)

/** The games of a run with `simulations` per move, and the run's wall-clock time. */
final case class SelfPlaySummary(games: Seq[GameSummary], simulations: Int, wallNanos: Long):
  def moves: Int = games.map(_.moves).sum

  def blackWins: Int = games.count(_.blackWon)

  /** The network's time per simulation in µs, summed over the games played at the same time. */
  def networkMicros: Double = perSimulation(games.map(_.networkNanos).sum)

  /** The rest of the time per simulation in µs: the tree search and the rules. */
  def engineMicros: Double = perSimulation(games.map(g => g.searchNanos - g.networkNanos).sum)

  private def perSimulation(nanos: Long): Double =
    nanos / 1000.0 / (moves.toLong * simulations).max(1L)

  def report(options: SelfPlayOptions, device: Device): String =
    val seconds = SelfPlaySummary.oneDecimal(wallNanos / 1e9)
    val network = SelfPlaySummary.oneDecimal(networkMicros)
    val engine = SelfPlaySummary.oneDecimal(engineMicros)
    val rate = SelfPlaySummary.oneDecimal(100.0 * blackWins / games.length.max(1))
    val run = s"size ${options.size}, generation ${options.generation}, $simulations simulations"
    val time = "time per simulation, summed over the games played at the same time"
    Seq(
      s"$run on ${device.name}: ${games.length} games, $moves moves in $seconds s",
      s"records in ${SelfPlay.recordDir(options)}",
      s"$time: network $network µs, engine $engine µs",
      s"Black wins $blackWins of ${games.length} games ($rate%)"
    ).mkString("\n")

object SelfPlaySummary:
  /** `value` rounded to one decimal, for the report. */
  def oneDecimal(value: Double): String =
    BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toString
