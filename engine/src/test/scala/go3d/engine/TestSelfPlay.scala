package go3d.engine

import go3d.{Black, Game, Move, Pass}
import go3d.server.given
import io.circe.Json
import io.circe.parser.parse
import org.junit.jupiter.api.{AfterEach, Assertions, Test}
import org.scalatest.EitherValues.*
import org.scalatest.TryValues.*

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Using}

class TestSelfPlay:

  private val out = Files.createTempDirectory("selfplay")
  private val uniform = FakeEvaluator(Map(), 0.0)
  private val komi = 4.5
  private val tolerance = 1e-9

  @AfterEach def deleteRecords(): Unit =
    Files.walk(out).sorted(Comparator.reverseOrder()).iterator.asScala.foreach(Files.delete)

  private def options(seed: Long, games: Int, parallel: Int, outDir: Path): SelfPlayOptions =
    SelfPlayOptions(
      5,
      0,
      games,
      6,
      seed,
      parallel,
      fixtureStore.toString,
      outDir.toString,
      Device.Cpu
    )

  /** The records of a run, by file name. */
  private def records(options: SelfPlayOptions): Map[String, Json] =
    val dir = SelfPlay.recordDir(options)
    if !Files.isDirectory(dir) then Map()
    else
      Files.list(dir).iterator.asScala.toList.map { path =>
        path.getFileName.toString -> parse(Files.readString(path)).value
      }.toMap

  private def played(seed: Long, games: Int, parallel: Int, outDir: Path): Map[String, Json] =
    val run = options(seed, games, parallel, outDir)
    SelfPlay.run(run, uniform, komi).success.value
    records(run)

  private def recordedGame(record: Json): Game = record.hcursor.downField("game").as[Game].value

  private def executed(args: String*): (Int, String, String) =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val code = Console.withOut(stdout)(Console.withErr(stderr)(SelfPlay.execute(args)))
    (code, stdout.toString, stderr.toString)

  @Test def testRecordReplaysToItsWinnerAndScore(): Unit =
    played(1, 2, 1, out).values.foreach { record =>
      val game = recordedGame(record)
      val start = Game.start(5, komi)
      val replayed = game.moves.foldLeft(start)((g, move) => g.flatMap(_.makeMove(move)))
        .success.value
      Assertions.assertTrue(replayed.isOver)
      Assertions.assertEquals(game, replayed)
      val winner = if replayed.winner.contains(Black) then "black" else "white"
      Assertions.assertEquals(winner, record.hcursor.downField("winner").as[String].value)
      val score = record.hcursor.downField("score")
      Assertions.assertEquals(replayed.score(Black), score.downField("black").as[Int].value)
      Assertions.assertEquals(replayed.score(!Black), score.downField("white").as[Int].value)
    }

  @Test def testRecordCarriesTheRulesOfItsGame(): Unit =
    val record = played(1, 1, 1, out).values.head.hcursor
    Assertions.assertEquals(1, record.downField("contract_version").as[Int].value)
    Assertions.assertEquals(5, record.downField("size").as[Int].value)
    Assertions.assertEquals(0, record.downField("generation").as[Int].value)
    Assertions.assertEquals(komi, record.downField("komi").as[Double].value)
    Assertions.assertEquals(komi, record.downField("game").downField("komi").as[Double].value)
    Assertions.assertEquals("area", record.downField("scoring_rule").as[String].value)
    Assertions.assertEquals(6, record.downField("simulations").as[Int].value)
    val seed = SelfPlayRecord.gameSeed(1, 5, 0, 0)
    Assertions.assertEquals(seed, record.downField("seed").as[Long].value)

  @Test def testPolicyOfEveryMoveIsADistributionOverItsLegalActions(): Unit =
    played(2, 1, 1, out).values.foreach { record =>
      val game = recordedGame(record)
      val policies = record.hcursor.downField("policies").as[Vector[Vector[Double]]].value
      Assertions.assertEquals(game.moves.length, policies.length)
      policies.zipWithIndex.foreach { (policy, index) =>
        Assertions.assertEquals(126, policy.length)
        Assertions.assertTrue(policy.forall(_ >= 0), policy.toString)
        Assertions.assertEquals(1.0, policy.sum, tolerance)
        val legal = legalActions(game.atMove(index).success.value)
        policy.indices.filterNot(legal).foreach(action =>
          Assertions.assertEquals(0.0, policy(action), s"move $index, action $action")
        )
        Assertions.assertTrue(policy(Encoding.action(game.moves(index), 5)) > 0)
      }
    }

  @Test def testSameSeedGivesTheSameRecords(): Unit =
    val first = played(3, 2, 1, out.resolve("first"))
    Assertions.assertEquals(2, first.size)
    Assertions.assertEquals(first, played(3, 2, 1, out.resolve("second")))

  @Test def testParallelGamesAreTheGamesOfASequentialRun(): Unit =
    // the fake evaluator is deterministic, unlike a network on a GPU
    Assertions.assertEquals(
      played(3, 3, 1, out.resolve("sequential")),
      played(3, 3, 3, out.resolve("parallel"))
    )

  @Test def testOtherSeedsExplore(): Unit =
    def firstMove(seed: Long): Move | Pass =
      recordedGame(played(seed, 1, 1, out.resolve(seed.toString)).values.head).moves.head
    Assertions.assertNotEquals(firstMove(1), firstMove(2))

  @Test def testGameIdsOfARunDiffer(): Unit =
    val ids = (0 until 1000).map(SelfPlayRecord.gameId(1, 5, 0, _))
    Assertions.assertEquals(ids.length, ids.distinct.length)
    ids.foreach(id => Assertions.assertTrue(go3d.server.IdGenerator.isValidId(id), id))
    Assertions.assertNotEquals(SelfPlayRecord.gameId(1, 5, 0, 0), SelfPlayRecord.gameId(2, 5, 0, 0))

  @Test def testWriterStillWritesTheFixtureRecord(): Unit =
    // training reads the fixture: a change here is a change of the record format
    val run = options(fixtureRecordSeed, 1, 1, out)
    SelfPlay.run(run, uniform, komi).success.value
    val id = SelfPlayRecord.gameId(fixtureRecordSeed, 5, 0, 0)
    val written = SelfPlayRecord.path(SelfPlay.recordDir(run), id)
    val fixture = SelfPlayRecord.path(fixtureRecords.resolve("5").resolve("0"), id)
    val hint = "if the format changed on purpose, update training and the fixture"
    val changed = s"the writer no longer writes $fixture: $hint"
    Assertions.assertEquals(Files.readString(fixture), Files.readString(written), changed)

  @Test def testOpeningMovesAreAnEighthOfTheBoard(): Unit =
    Assertions.assertEquals(15, SelfPlayGame.openingMoves(5))
    Assertions.assertEquals(91, SelfPlayGame.openingMoves(9))

  @Test def testFixtureModelPlaysAndReportsTheTimes(): Unit =
    val args = Seq("--size", "5", "--generation", "0", "--games", "2", "--simulations", "4") ++
      Seq("--parallel", "2", "--model-store", fixtureStore.toString, "--out", out.toString) ++
      Seq("--device", "cpu")
    val (code, report, _) = executed(args*)
    Assertions.assertEquals(0, code, report)
    val written = records(options(1, 2, 2, out))
    Assertions.assertEquals(2, written.size)
    val lines = report.linesIterator.toList
    val run = "size 5, generation 0, 4 simulations on cpu: 2 games, "
    Assertions.assertTrue(lines.exists(_.startsWith(run)), report)
    val time = "time per simulation, summed over the games played at the same time: network "
    Assertions.assertTrue(lines.exists(l => l.startsWith(time) && l.contains(" µs, engine ")))
    Assertions.assertTrue(lines.exists(_.matches("""Black wins [0-2] of 2 games \(.*%\)""")))
    // the fixture's komi
    written.values.foreach(record =>
      Assertions.assertEquals(4.5, record.hcursor.downField("komi").as[Double].value)
    )

  @Test def testCudaRunNamesTheDeviceOfTheModel(): Unit =
    // CUDA on a machine where its provider loads, else the CPU
    val device = Using.resource(OnnxModel.load(fixtureModelPath, 5, Device.Cuda).success.value)(
      _.device
    )
    val args = Seq("--size", "5", "--generation", "0", "--games", "1", "--simulations", "1") ++
      Seq("--model-store", fixtureStore.toString, "--out", out.toString, "--device", "cuda")
    val (code, report, _) = executed(args*)
    Assertions.assertEquals(0, code, report)
    Assertions.assertTrue(report.contains(s" on ${device.toString.toLowerCase}: "), report)
    Assertions.assertEquals(1, records(options(1, 1, 1, out)).size)

  @Test def testRunDoesNotOverwriteTheRecordsOfAnEarlierRun(): Unit =
    val run = options(1, 2, 1, out)
    val earlier = played(1, 2, 1, out)
    val error = SelfPlay.run(run, uniform, komi).failure.exception
    val first = SelfPlayRecord.path(SelfPlay.recordDir(run), SelfPlayRecord.gameId(1, 5, 0, 0))
    Assertions.assertTrue(error.getMessage.contains(first.toString), error.getMessage)
    Assertions.assertTrue(error.getMessage.contains("--seed"), error.getMessage)
    Assertions.assertEquals(earlier, records(run))

  @Test def testRerunOfTheCommandFailsWithoutWriting(): Unit =
    val earlier = played(1, 1, 1, out)
    val args = Seq("--size", "5", "--generation", "0", "--games", "2", "--simulations", "1") ++
      Seq("--model-store", fixtureStore.toString, "--out", out.toString, "--device", "cpu")
    val (code, _, message) = executed(args*)
    Assertions.assertEquals(SelfPlay.FailureExitCode, code)
    Assertions.assertTrue(message.contains("--out"), message)
    Assertions.assertEquals(earlier, records(options(1, 1, 1, out)))

  @Test def testGameThatFailsFailsTheRun(): Unit =
    val calls = AtomicInteger()
    val failing: Evaluator = game =>
      if calls.incrementAndGet > 50 then Failure(IllegalStateException("network failed"))
      else uniform.evaluate(game)
    val error = SelfPlay.run(options(1, 2, 1, out), failing, komi).failure.exception
    Assertions.assertEquals("network failed", error.getMessage)

  @Test def testOpeningMovesAreSampledAndTheRestAreTheMostVisited(): Unit =
    val start = Game.start(5, komi).success.value
    val game = SelfPlayGame.play(uniform, start, 10, 1).success.value
    val shares = game.game.moves.toVector.zip(game.policies).map { (move, policy) =>
      (policy(Encoding.action(move, 5)), policy.max)
    }
    val (opening, rest) = shares.splitAt(SelfPlayGame.openingMoves(5))
    Assertions.assertTrue(rest.nonEmpty)
    rest.foreach((share, max) => Assertions.assertEquals(max, share, tolerance))
    Assertions.assertTrue(opening.exists((share, max) => share < max), opening.toString)

  @Test def testHelpPrintsTheUsage(): Unit =
    val (code, usage, message) = executed("--help")
    Assertions.assertEquals(0, code)
    Assertions.assertTrue(usage.contains(SelfPlayOptions.Usage), usage)
    Assertions.assertEquals("", message)

  @Test def testMissingModelWritesNoRecords(): Unit =
    val args = Seq("--size", "5", "--generation", "1", "--games", "1") ++
      Seq("--model-store", fixtureStore.toString, "--out", out.toString, "--device", "cpu")
    val (code, _, message) = executed(args*)
    Assertions.assertEquals(SelfPlay.FailureExitCode, code)
    Assertions.assertTrue(message.contains(OnnxModel.path(fixtureStore, 5, 1).toString), message)
    Assertions.assertEquals(List(), Files.list(out).iterator.asScala.toList)

  @Test def testBadOptionWritesNoRecords(): Unit =
    val args = Seq("--size", "5", "--generation", "0", "--games", "0") ++
      Seq("--model-store", fixtureStore.toString, "--out", out.toString)
    val (code, _, message) = executed(args*)
    Assertions.assertEquals(SelfPlay.BadOptionsExitCode, code)
    Assertions.assertTrue(message.contains("Usage"), message)
    Assertions.assertEquals(List(), Files.list(out).iterator.asScala.toList)
