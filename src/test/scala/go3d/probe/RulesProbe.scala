package go3d.probe

import go3d.server.SaveGame

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.Executors
import scala.annotation.tailrec
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

  private final case class Played(result: GameResult, record: Option[(String, SaveGame)])

  private final case class SizeRun(
      size: Int,
      gamesPerPairing: Int,
      metrics: SizeMetrics,
      seconds: Long
  )

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
    // with a time limit, a size can stop before its games per pairing: report what was played
    val played =
      options.copy(gamesPerPairing = runs.map(run => run.size -> run.gamesPerPairing).toMap)
    writeReport(out, Report.render(played, runs.map(_.metrics), seconds))

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

  /** Plays a size in rounds of one game per pairing, until the games per pairing are played or,
    * with a time limit, the time is up after a round. Every pairing so has the same number of
    * games, and a run that stops after n rounds has the games of `--games n`. The CSV and the
    * sample records are written after each round, so a run that is stopped keeps what it played.
    */
  private def playSize(options: ProbeOptions, out: Path, size: Int): SizeRun =
    val started = System.nanoTime
    val rounds = options.gamesPerPairing(size)
    val deadline = options.timeLimit.map(limit => started + limit.toNanos)
    val pairings = options.pairings
    val printEvery = (rounds / 20).max(1)

    @tailrec
    def playRounds(round: Int, results: List[GameResult]): List[GameResult] =
      if round == rounds then results
      else if round > 0 && deadline.exists(System.nanoTime >= _) then
        println(s"size $size: time limit reached after $round games per pairing")
        results
      else
        val tasks = pairings.map((black, white) => Task(size, black, white, round))
        val played = parallelMap(tasks, options.parallelism)(play(options.seed, _))
        val all = results ++ played.map(_.result)
        Files.writeString(
          out.resolve(s"games-$size.csv"),
          Csv.render(inPairingOrder(all, pairings))
        )
        SampleRecords.write(out, played.flatMap(_.record))
        if (round + 1) % printEvery == 0 || round + 1 == rounds then
          val time = secondsSince(started)
          println(s"size $size: ${round + 1} of $rounds games per pairing, $time s")
        playRounds(round + 1, all)

    val results = inPairingOrder(playRounds(0, Nil), pairings)
    val gamesPerPairing = results.length / pairings.length.max(1)
    SizeRun(size, gamesPerPairing, Statistics.forSize(size, results), secondsSince(started))

  /** The results by pairing, in the order of `pairings`, and by game number within a pairing. */
  private def inPairingOrder(
      results: List[GameResult],
      pairings: List[(String, String)]
  ): List[GameResult] =
    val index = pairings.zipWithIndex.toMap
    results.sortBy(result => (index.getOrElse((result.black, result.white), 0), result.game))

  private def play(seed: Long, task: Task): Played =
    val gameSeed = GamePlayer.gameSeed(seed, task.size, task.black, task.white, task.game)
    val game = GamePlayer.play(task.size, task.black, task.white, gameSeed)
      .fold(e => sys.error(s"$task: ${e.getMessage}"), identity)
    val id = Option.when(SampleRecords.isSample(task.game))(
      SampleRecords.recordId(task.size, task.black, task.white, task.game)
    )
    Played(
      GameResult.of(task, game, id),
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
