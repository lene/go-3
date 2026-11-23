package go3d.probe

import go3d.Game
import go3d.server.FileIO
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.EitherValues.*
import org.scalatest.TryValues.*

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.{Files, Path}

class TestRulesProbe:

  private val Quiet = PrintStream(ByteArrayOutputStream())

  /** Runs the probe on two cheap strategies on size 5 into a new folder, and returns the folder. */
  private def runProbe(seed: String, parallelism: String): Path =
    val out = Files.createTempDirectory("rules-probe-run")
    val args = List(
      "--sizes",
      "5",
      "--games",
      "2",
      "--seed",
      seed,
      "--parallelism",
      parallelism,
      "--strategies",
      "random,closestToCenter",
      "--out",
      out.toString
    )
    val exitCode = Console.withOut(Quiet)(RulesProbe.execute(args, 1))
    Assertions.assertEquals(0, exitCode)
    out

  private def csvOf(out: Path): String = Files.readString(out.resolve("games-5.csv"))

  @Test def testTheSameSeedGivesTheSameCsv(): Unit =
    Assertions.assertEquals(csvOf(runProbe("3", "4")), csvOf(runProbe("3", "4")))

  @Test def testTheCsvDoesNotDependOnTheParallelism(): Unit =
    Assertions.assertEquals(csvOf(runProbe("3", "4")), csvOf(runProbe("3", "1")))

  @Test def testAnotherSeedGivesAnotherCsv(): Unit =
    Assertions.assertNotEquals(csvOf(runProbe("3", "4")), csvOf(runProbe("4", "4")))

  @Test def testOneRowPerGameInTheOrderOfThePairings(): Unit =
    val rows = Csv.parse(csvOf(runProbe("3", "4"))).value
    Assertions.assertEquals(
      List(
        ("random", "random"),
        ("random", "closestToCenter"),
        ("closestToCenter", "random"),
        ("closestToCenter", "closestToCenter")
      ).flatMap(pairing => List(pairing, pairing)),
      rows.map(row => (row.black, row.white))
    )
    Assertions.assertEquals(List.fill(4)(List(0, 1)).flatten, rows.map(_.game))
    Assertions.assertTrue(rows.forall(_.size == 5))

  @Test def testTheReportStatesTheStopRule(): Unit =
    val report = Files.readString(runProbe("3", "4").resolve("report.md"))
    Assertions.assertTrue(report.contains("## Size 5"), report)
    Assertions.assertTrue(
      report.contains("The stop rule fired") || report.contains("The stop rule did not fire"),
      report
    )

  @Test def testEverySampleRecordReplaysToTheAreaScoreOfItsRow(): Unit =
    val out = runProbe("3", "4")
    val rows = Csv.parse(csvOf(out)).value
    val fileIO = FileIO(out.toString).success.value
    Assertions.assertEquals(8, rows.count(_.record.nonEmpty))
    Assertions.assertEquals(rows.flatMap(_.record).sorted, fileIO.getArchivedGames.sorted)
    for row <- rows; id <- row.record do
      val record = fileIO.loadArchivedGame(id).success.value
      val replayed = record.game.moves.foldLeft(Game.start(5, record.game.komi).success.value) {
        (game, move) => game.makeMove(move).success.value
      }
      Assertions.assertEquals(row.blackSide.area, replayed.score(go3d.Black), id)
      Assertions.assertEquals(row.whiteSide.area, replayed.score(go3d.White), id)
      Assertions.assertEquals(row.moves, replayed.moves.length, id)
      Assertions.assertEquals(row.blackSide.captures, replayed.captures(go3d.Black), id)

  /** The exit code of `--report-from` the folders `from`, and the new folder it wrote to. */
  private def rebuildReport(from: List[Path]): (Int, Path) =
    val out = Files.createTempDirectory("rules-probe-report")
    val args = List(
      "--report-from",
      from.mkString(","),
      "--sizes",
      "5",
      "--seed",
      "3",
      "--parallelism",
      "4",
      "--out",
      out.toString
    )
    (Console.withOut(Quiet)(Console.withErr(Quiet)(RulesProbe.execute(args, 1))), out)

  /** The report without the lines that differ between a run and a rebuilt report. */
  private def metricsOf(folder: Path): List[String] =
    val runOnly = List("- komi ", "- size ", "- rebuilt from")
    Files.readString(folder.resolve("report.md")).linesIterator.toList
      .filterNot(line => runOnly.exists(line.startsWith))

  @Test def testAReportFromTheCsvFilesOfARunHasTheMetricsOfItsReport(): Unit =
    val run = runProbe("3", "4")
    val (exitCode, rebuilt) = rebuildReport(List(run))
    Assertions.assertEquals(0, exitCode)
    Assertions.assertEquals(metricsOf(run), metricsOf(rebuilt))
    val report = Files.readString(rebuilt.resolve("report.md"))
    Assertions.assertTrue(report.contains("size 5: 2 games per pairing, 8 games"), report)

  @Test def testEachSizeOfAReportComesFromTheLastFolderWithItsCsv(): Unit =
    val (first, last) = (runProbe("3", "4"), runProbe("4", "4"))
    val (_, rebuilt) = rebuildReport(List(first, last, Files.createTempDirectory("empty")))
    Assertions.assertEquals(metricsOf(last), metricsOf(rebuilt))

  @Test def testAReportFromFoldersWithoutTheCsvFailsAndWritesNothing(): Unit =
    val (exitCode, rebuilt) = rebuildReport(List(Files.createTempDirectory("empty")))
    Assertions.assertEquals(RulesProbe.BadOptionsExitCode, exitCode)
    Assertions.assertFalse(Files.exists(rebuilt.resolve("report.md")))

  @Test def testBadOptionsRunNothingAndFail(): Unit =
    val out = Files.createTempDirectory("rules-probe-bad").resolve("never")
    val badArgs = List(
      List("--sizes", "4"),
      List("--sizes", "3"),
      List("--sizes", "11"),
      List("--strategies", "cleverest")
    )
    for args <- badArgs do
      val exitCode =
        Console.withErr(Quiet)(RulesProbe.execute(args ++ List("--out", out.toString), 1))
      Assertions.assertEquals(RulesProbe.BadOptionsExitCode, exitCode, args.toString)
      Assertions.assertTrue(RulesProbe.BadOptionsExitCode != 0)
      Assertions.assertFalse(Files.exists(out), args.toString)
