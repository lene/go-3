package go3d.engine

import cats.syntax.all.*
import go3d.{Black, Game, White}
import io.circe.Json

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption, StandardOpenOption}
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try, Using}

/** The promotion match: the candidate for generation `g` plays the promoted generation `g - 1`,
  * and is promoted if it wins more than the threshold (`docs/model-contract.md`, Storage).
  *
  * {{{
  * sbt "engine/runMain go3d.engine.Match --size 5 --generation 1"
  * }}}
  *
  * The colours alternate, so each side is Black in half the games. Every game starts at the komi
  * of the candidate, the komi refitted by training. The result is appended as a line to the match
  * log `<store>/<size>/matches.jsonl`; then the candidate's directory `candidates/<g>/` is renamed
  * to `<g>/` if it won, else to `rejected/<g>-<attempt>/`. Options that are not valid exit with
  * [[Cli.BadOptionsExitCode]]; a promoted generation `g`, a missing candidate or generation
  * `g - 1`, a model that does not load or a game that fails exit with [[Cli.FailureExitCode]]
  * and play, log and rename nothing.
  */
object Match:
  val RejectedDirName: String = "rejected"
  val LogFileName: String = "matches.jsonl"

  def main(args: Array[String]): Unit = Cli.exit(execute(args.toIndexedSeq))

  /** Runs the match with the options in `args` and returns the exit code. */
  def execute(args: Seq[String]): Int =
    Cli.execute(MatchOptions.Usage, MatchOptions.asksForHelp(args), MatchOptions.parse(args))(
      playWithModels
    )

  /** The directory that attempt `attempt` of the candidate for `generation` is set aside to. */
  def rejectedDir(store: Path, size: Int, generation: Int, attempt: Int): Path =
    store.resolve(size.toString).resolve(RejectedDirName).resolve(s"$generation-$attempt")

  /** The match log of size `size`: one JSON object per line, one line per match. */
  def logPath(store: Path, size: Int): Path = store.resolve(size.toString).resolve(LogFileName)

  /** Plays the match of `options` with the models of the store and returns the report. */
  private def playWithModels(options: MatchOptions): Try[String] =
    def load(path: Path) = OnnxModel.load(path, options.size, options.device)
    for
      store <- Try(Paths.get(options.modelStore))
      _ <- notPromoted(store, options)
      candidate <- load(OnnxModel.candidatePath(store, options.size, options.generation))
      report <- Using(candidate) { candidate =>
        load(OnnxModel.path(store, options.size, options.opponent)).flatMap { opponent =>
          Using(opponent) { opponent =>
            run(options, store, candidate, opponent, candidate.komi).map(
              _.report(options, store, candidate.device, candidate.komi, opponent.komi)
            )
          }.flatten
        }
      }.flatten
    yield report

  /** Plays the match of `options` in the model store `store` with `candidate` and `opponent` at
    * `komi`, `parallel` games at a time, appends its line to the match log, and promotes or sets
    * aside the candidate's directory. The candidate's directory must exist and the generation must
    * not be promoted yet: otherwise nothing is played, logged or renamed.
    */
  def run(
      options: MatchOptions,
      store: Path,
      candidate: Evaluator,
      opponent: Evaluator,
      komi: Double
  ): Try[MatchOutcome] =
    val started = System.nanoTime
    val source = OnnxModel.candidateDir(store, options.size, options.generation)
    for
      _ <- notPromoted(store, options)
      _ <- if Files.isDirectory(source) then Success(()) else Failure(missing(source))
      start <- Game.start(options.size, komi)
      games <- Parallel.games(0 until options.games, options.parallel)(
        playGame(options, candidate, opponent, start, _)
      ).sequence
      attempt <- nextAttempt(store, options)
      result = MatchResult(games)
      promoted = result.wins > options.threshold
      target =
        if promoted then OnnxModel.generationDir(store, options.size, options.generation)
        else rejectedDir(store, options.size, options.generation, attempt)
      outcome = MatchOutcome(result, promoted, attempt, target, System.nanoTime - started)
      _ <- if Files.exists(target) then Failure(exists(target)) else Success(())
      _ <- appendToLog(store, options, komi, outcome)
      _ <- Try(Files.createDirectories(target.getParent))
      _ <- Try(Files.move(source, target, StandardCopyOption.ATOMIC_MOVE))
    yield outcome

  /** The seed of game `number` of a match with `options`: other than any seed of self-play. */
  def gameSeed(options: MatchOptions, number: Int): Long =
    Parallel.seed(s"match/${options.seed}/${options.size}/${options.generation}/$number")

  private def playGame(
      options: MatchOptions,
      candidate: Evaluator,
      opponent: Evaluator,
      start: Game,
      number: Int
  ): Try[GameResult] =
    val candidateIsBlack = number % 2 == 0
    val (black, white) = if candidateIsBlack then (candidate, opponent) else (opponent, candidate)
    val candidateColor = if candidateIsBlack then Black else White
    MatchGame.play(black, white, start, options.simulations, gameSeed(options, number)).map(game =>
      GameResult(candidateIsBlack, game.winner.contains(candidateColor))
    )

  private def notPromoted(store: Path, options: MatchOptions): Try[Unit] =
    val promoted = OnnxModel.generationDir(store, options.size, options.generation)
    if Files.exists(promoted) then
      Failure(
        IllegalStateException(s"$promoted exists: generation ${options.generation} is promoted")
      )
    else Success(())

  private def missing(path: Path) = IllegalStateException(s"no candidate at $path")

  private def exists(path: Path) = IllegalStateException(s"$path exists and is not overwritten")

  /** The next attempt of the candidate of `options`: one more than the highest attempt set aside,
    * 1 if none was.
    */
  private def nextAttempt(store: Path, options: MatchOptions): Try[Int] =
    val rejected = store.resolve(options.size.toString).resolve(RejectedDirName)
    val pattern = s"${options.generation}-([0-9]+)".r
    if !Files.isDirectory(rejected) then Success(1)
    else
      Using(Files.list(rejected)) { entries =>
        entries.iterator.asScala.map(_.getFileName.toString).collect {
          case pattern(attempt) => attempt.toInt
        }.maxOption.fold(1)(_ + 1)
      }

  private def appendToLog(
      store: Path,
      options: MatchOptions,
      komi: Double,
      outcome: MatchOutcome
  ): Try[Unit] =
    val line = outcome.logLine(options, store, komi, Instant.now).noSpaces
    Try(Files.writeString(
      logPath(store, options.size),
      s"$line\n",
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    ))

/** A game of a match: whether the candidate was Black and whether it won. */
final case class GameResult(candidateIsBlack: Boolean, candidateWon: Boolean)

/** The candidate's wins in the games of a match. */
final case class MatchResult(games: Seq[GameResult]):
  def winsAsBlack: Int = games.count(game => game.candidateIsBlack && game.candidateWon)

  def winsAsWhite: Int = games.count(game => !game.candidateIsBlack && game.candidateWon)

  def wins: Int = winsAsBlack + winsAsWhite

/** A match that was played: its result, whether the candidate was promoted, the attempt it was
  * for that generation, the directory the candidate went to, and the match's wall-clock time.
  */
final case class MatchOutcome(
    result: MatchResult,
    promoted: Boolean,
    attempt: Int,
    target: Path,
    nanos: Long
):
  private def seconds: Double = nanos / 1e9

  /** The line of the match log (`docs/model-contract.md`, Storage), finished at `finished`. */
  def logLine(options: MatchOptions, store: Path, komi: Double, finished: Instant): Json =
    Json.obj(
      "size" -> Json.fromInt(options.size),
      "generation" -> Json.fromInt(options.generation),
      "opponent" -> Json.fromInt(options.opponent),
      "attempt" -> Json.fromInt(attempt),
      "games" -> Json.fromInt(options.games),
      "simulations" -> Json.fromInt(options.simulations),
      "threshold" -> Json.fromInt(options.threshold),
      "seed" -> Json.fromLong(options.seed),
      "komi" -> Json.fromDoubleOrNull(komi),
      "candidate_wins" -> Json.fromInt(result.wins),
      "candidate_wins_as_black" -> Json.fromInt(result.winsAsBlack),
      "candidate_wins_as_white" -> Json.fromInt(result.winsAsWhite),
      "promoted" -> Json.fromBoolean(promoted),
      "moved_to" -> Json.fromString(store.relativize(target).toString),
      "duration_seconds" -> Json.fromDoubleOrNull(seconds),
      "finished" -> Json.fromString(finished.toString)
    )

  def report(
      options: MatchOptions,
      store: Path,
      device: Device,
      komi: Double,
      opponentKomi: Double
  ): String =
    val opponent = options.opponent
    val half = options.games / 2
    val time = SelfPlaySummary.oneDecimal(seconds)
    val run =
      s"candidate ${options.generation} against generation $opponent of size ${options.size}"
    val played = s"${options.simulations} simulations: ${options.games} games in $time s"
    val black = s"${result.winsAsBlack} of $half as Black"
    val white = s"${result.winsAsWhite} of $half as White"
    val decision =
      if promoted then s"promoted with more than ${options.threshold} wins: $target"
      else s"rejected with ${options.threshold} wins or fewer: set aside to $target"
    Seq(
      s"$run on ${device.name}, $played",
      s"komi $komi, the candidate's; generation $opponent was made at komi $opponentKomi",
      s"candidate won ${result.wins} of ${options.games} games: $black, $white",
      decision,
      s"match log: ${Match.logPath(store, options.size)}"
    ).mkString("\n")
