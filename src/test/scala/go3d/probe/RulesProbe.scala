package go3d.probe

import go3d.server.SaveGame

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/** The rules probe (CAP-7): the strategies of [[go3d.client.SetStrategy]] play each other and
  * themselves, in both colours, on the board sizes 5, 7 and 9. Each game is scored with and without
  * captures, to see whether the scoring rule decides games differently in 3D.
  *
  * {{{
  * sbt "Test/runMain go3d.probe.RulesProbe --sizes 5 --games 2 --seed 1 \
  *   --out results/rules-probe/smoke"
  * }}}
  *
  * It writes `games-<size>.csv` (one row per game), `report.md` (the metrics and the stop rule) and
  * about three sample games per pairing in `archive/`, which `go-server --save-dir <out>` serves to
  * the viewer. The same options and seed give the same CSV files. Options that are not valid run
  * nothing and exit with a non-zero code.
  */
object RulesProbe:

  private final case class Task(size: Int, black: String, white: String, game: Int)

  private final case class Played(result: GameResult, record: Option[(String, SaveGame)])

  private final case class SizeRun(size: Int, metrics: SizeMetrics, seconds: Long)

  /** The exit code for options that are not valid. */
  val BadOptionsExitCode: Int = 2

  def main(args: Array[String]): Unit =
    val exitCode = execute(args.toList, Runtime.getRuntime.availableProcessors)
    if exitCode != 0 then sys.exit(exitCode)

  /** Runs the probe with the options in `args` and returns the exit code: 0 after a run,
    * [[BadOptionsExitCode]] with a message on stderr and nothing run if the options are not valid
    * or, with `--report-from`, a CSV file is missing or cannot be read.
    */
  def execute(args: Seq[String], processors: Int): Int =
    ProbeOptions.parse(args, processors) match
      case Left(message) =>
        Console.err.println(s"$message\n${ProbeOptions.Usage}")
        BadOptionsExitCode
      case Right(options) if options.reportFrom.nonEmpty =>
        rebuildReport(options) match
          case Left(message) =>
            Console.err.println(message)
            BadOptionsExitCode
          case Right(()) => 0
      case Right(options) =>
        run(options)
        0

  def run(options: ProbeOptions): Unit =
    val out = Files.createDirectories(Paths.get(options.outDir))
    val runs = options.sizes.map(playSize(options, out, _))
    val seconds = runs.map(run => run.size -> run.seconds).toMap
    writeReport(out, Report.render(options, runs.map(_.metrics), seconds))

  private def writeReport(out: Path, report: String): Unit =
    Files.writeString(out.resolve("report.md"), report)
    println(report)

  /** Writes the report on the CSV files of earlier runs, playing nothing: each of the sizes comes
    * from the last folder of `reportFrom` that has its CSV file. The games per pairing and the
    * strategies are those of the rows; the time of each size is not in the CSV.
    */
  private def rebuildReport(options: ProbeOptions): Either[String, Unit] =
    val read = options.sizes.map(size => readResults(options.reportFrom, size).map(size -> _))
    read.collectFirst { case Left(message) => message }.toLeft(read.collect { case Right(r) => r })
      .map { results =>
        val metrics = results.map((size, rows) => Statistics.forSize(size, rows))
        val names = results.flatMap(_._2).map(_.black).toSet
        val rebuilt = options.copy(
          gamesPerPairing = metrics.map(m => m.size -> m.games / m.pairings.max(1)).toMap,
          strategies = ProbeOptions.StrategyNames.filter(names)
        )
        writeReport(
          Files.createDirectories(Paths.get(options.outDir)),
          Report.render(rebuilt, metrics, Map.empty)
        )
      }

  private def readResults(folders: List[String], size: Int): Either[String, List[GameResult]] =
    val name = s"games-$size.csv"
    folders.reverse.map(Paths.get(_).resolve(name)).find(Files.exists(_))
      .toRight(s"no $name in ${folders.mkString(", ")}")
      .flatMap(path => Csv.parse(Files.readString(path)).left.map(reason => s"$path: $reason"))

  private def playSize(options: ProbeOptions, out: Path, size: Int): SizeRun =
    val started = System.nanoTime
    val games = options.gamesPerPairing(size)
    val tasks =
      for
        (black, white) <- options.pairings
        game <- 0 until games
      yield Task(size, black, white, game)
    val progress = Progress(size, tasks.length, started)
    val played = parallelMap(tasks, options.parallelism) { task =>
      val outcome = play(options.seed, task)
      progress.finished()
      outcome
    }
    val results = played.map(_.result)
    Files.writeString(out.resolve(s"games-$size.csv"), Csv.render(results))
    SampleRecords.write(out, played.flatMap(_.record))
    SizeRun(size, Statistics.forSize(size, results), secondsSince(started))

  private def play(seed: Long, task: Task): Played =
    val gameSeed = GamePlayer.gameSeed(seed, task.size, task.black, task.white, task.game)
    val game = GamePlayer.play(task.size, task.black, task.white, gameSeed)
      .fold(e => sys.error(s"$task: ${e.getMessage}"), identity)
    val id = Option.when(SampleRecords.isSample(task.game))(
      SampleRecords.recordId(task.size, task.black, task.white, task.game)
    )
    Played(
      GameResult.of(task.size, task.black, task.white, task.game, game, id),
      id.map(recordId => recordId -> SampleRecords.saveGame(game, recordId))
    )

  /** The results of `f` for the `items`, in the order of the items, computed `parallelism` at a
    * time.
    */
  private def parallelMap[A, B](items: List[A], parallelism: Int)(f: A => B): List[B] =
    val pool = Executors.newFixedThreadPool(parallelism)
    try
      given ExecutionContext = ExecutionContext.fromExecutor(pool)
      Await.result(Future.traverse(items)(item => Future(f(item))), Duration.Inf)
    finally pool.shutdownNow()

  private def secondsSince(started: Long): Long = (System.nanoTime - started) / 1_000_000_000L

  /** Prints a line about every twentieth of the games of a size. */
  private final class Progress(size: Int, total: Int, started: Long):
    private val done = AtomicInteger(0)
    private val interval = (total / 20).max(1)

    def finished(): Unit =
      val count = done.incrementAndGet()
      if count % interval == 0 || count == total then
        println(s"size $size: $count of $total games, ${secondsSince(started)} s")
