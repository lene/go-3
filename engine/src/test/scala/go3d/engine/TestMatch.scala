package go3d.engine

import go3d.{Black, Game, White}
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
import scala.util.{Success, Using}

class TestMatch:

  private val store = Files.createTempDirectory("match")
  private val komi = 4.5
  private val pass = Encoding.actionCount(5) - 1

  @AfterEach def deleteStore(): Unit =
    Using.resource(Files.walk(store))(
      _.sorted(Comparator.reverseOrder()).iterator.asScala.foreach(Files.delete)
    )

  /** With one simulation the search plays the action of the highest prior: the first legal stone
    * of the action order for these evaluators, or the pass.
    */
  private def evaluator(passes: Game => Boolean): Evaluator = game =>
    val logits = IndexedSeq.tabulate(Encoding.actionCount(game.size)) { action =>
      if action != pass then 0f else if passes(game) then 10f else -10f
    }
    Success(Evaluation(logits, 0.0))

  /** Fills the board with its stones, so it wins against [[passer]] in either colour. */
  private val filler = evaluator(_ => false)
  private val passer = evaluator(_ => true)

  /** Fills the board as Black and passes as White: Black wins every game it plays itself. */
  private val blackFills = evaluator(_.moveColor == White)

  private def candidateDir: Path = OnnxModel.candidateDir(store, 5, 1)
  private def promotedDir: Path = OnnxModel.generationDir(store, 5, 1)

  /** A store with generation 0 and a candidate for generation 1, as files without models. */
  private def withModels(): Unit =
    Files.createDirectories(OnnxModel.generationDir(store, 5, 0))
    Files.createDirectories(candidateDir)
    Files.writeString(candidateDir.resolve(OnnxModel.FileName), "candidate")

  /** A store with copies of the fixture model as generation 0 and as the candidate. */
  private def withFixtureModels(dir: Path): Unit =
    Seq(OnnxModel.path(dir, 5, 0), OnnxModel.candidatePath(dir, 5, 1)).foreach { path =>
      Files.createDirectories(path.getParent)
      Files.copy(fixtureModelPath, path)
    }

  private def options(games: Int, threshold: Int): MatchOptions =
    MatchOptions(5, 1, games, 1, threshold, 1L, 1, store.toString, Device.Cpu)

  private def logLines(dir: Path): List[Json] =
    val log = Match.logPath(dir, 5)
    if !Files.exists(log) then List()
    else Files.readAllLines(log).asScala.toList.map(parse(_).value)

  private def onlyLine(dir: Path): Json =
    val lines = logLines(dir)
    Assertions.assertEquals(1, lines.length, lines.toString)
    lines.head

  /** The files of `dir` and their content, by path relative to `dir`. */
  private def snapshot(dir: Path): Map[String, Seq[Byte]] =
    Using.resource(Files.walk(dir))(_.iterator.asScala.toList)
      .filter(Files.isRegularFile(_))
      .map(path => dir.relativize(path).toString -> Files.readAllBytes(path).toSeq)
      .toMap

  private def executed(args: String*): (Int, String, String) =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val code = Console.withOut(stdout)(Console.withErr(stderr)(Match.execute(args)))
    (code, stdout.toString, stderr.toString)

  private def args(dir: Path, extra: String*): Seq[String] =
    Seq("--size", "5", "--generation", "1", "--model-store", dir.toString, "--device", "cpu") ++
      extra

  private def field[A: io.circe.Decoder](line: Json, name: String): A =
    line.hcursor.downField(name).as[A].value

  @Test def testCandidateThatWinsMoreThanTheThresholdIsPromoted(): Unit =
    withModels()
    val outcome = Match.run(options(4, 1), store, blackFills, blackFills, komi).success.value
    val result = outcome.result
    Assertions.assertEquals((2, 2, 0), (result.wins, result.winsAsBlack, result.winsAsWhite))
    Assertions.assertTrue(outcome.promoted)
    Assertions.assertFalse(Files.exists(candidateDir))
    Assertions.assertEquals("candidate", Files.readString(promotedDir.resolve(OnnxModel.FileName)))
    val line = onlyLine(store)
    Assertions.assertTrue(field[Boolean](line, "promoted"))
    Assertions.assertEquals("5/1", field[String](line, "moved_to"))

  @Test def testCandidateThatWinsTheThresholdIsSetAside(): Unit =
    withModels()
    val outcome = Match.run(options(4, 2), store, blackFills, blackFills, komi).success.value
    Assertions.assertFalse(outcome.promoted)
    Assertions.assertFalse(Files.exists(candidateDir))
    Assertions.assertFalse(Files.exists(promotedDir))
    val rejected = Match.rejectedDir(store, 5, 1, 1)
    Assertions.assertEquals("candidate", Files.readString(rejected.resolve(OnnxModel.FileName)))
    val line = onlyLine(store)
    Assertions.assertFalse(field[Boolean](line, "promoted"))
    Assertions.assertEquals("5/rejected/1-1", field[String](line, "moved_to"))

  @Test def testLogLineCarriesTheMatch(): Unit =
    withModels()
    Match.run(options(4, 1).copy(seed = 7), store, filler, passer, komi).success.value
    val line = onlyLine(store)
    val expected = Seq(
      "size" -> 5,
      "generation" -> 1,
      "opponent" -> 0,
      "attempt" -> 1,
      "games" -> 4,
      "simulations" -> 1,
      "threshold" -> 1,
      "seed" -> 7,
      "candidate_wins" -> 4,
      "candidate_wins_as_black" -> 2,
      "candidate_wins_as_white" -> 2
    )
    expected.foreach((name, value) => Assertions.assertEquals(value, field[Int](line, name), name))
    Assertions.assertEquals(komi, field[Double](line, "komi"))
    Assertions.assertTrue(field[Double](line, "duration_seconds") >= 0)
    Assertions.assertTrue(field[String](line, "finished").nonEmpty)

  @Test def testCandidateThatLosesEveryGameWinsNone(): Unit =
    withModels()
    val outcome = Match.run(options(4, 0), store, passer, filler, komi).success.value
    Assertions.assertEquals(0, outcome.result.wins)
    Assertions.assertFalse(outcome.promoted)

  @Test def testEachRejectionIsTheNextAttemptAndKeepsTheLog(): Unit =
    withModels()
    Match.run(options(4, 2), store, blackFills, blackFills, komi).success.value
    val first = Files.readString(Match.logPath(store, 5))
    Files.createDirectories(candidateDir)
    Match.run(options(4, 2), store, blackFills, blackFills, komi).success.value
    Assertions.assertTrue(Files.isDirectory(Match.rejectedDir(store, 5, 1, 1)))
    Assertions.assertTrue(Files.isDirectory(Match.rejectedDir(store, 5, 1, 2)))
    Assertions.assertTrue(Files.readString(Match.logPath(store, 5)).startsWith(first))
    Assertions.assertEquals(List(1, 2), logLines(store).map(field[Int](_, "attempt")))

  @Test def testPromotedGenerationIsNotPlayedAgain(): Unit =
    withModels()
    Files.createDirectories(promotedDir)
    val calls = AtomicInteger()
    val counting: Evaluator = game =>
      calls.incrementAndGet
      filler.evaluate(game)
    val before = snapshot(store)
    val error = Match.run(options(4, 1), store, counting, counting, komi).failure.exception
    Assertions.assertTrue(error.getMessage.contains(promotedDir.toString), error.getMessage)
    Assertions.assertEquals(0, calls.get)
    Assertions.assertEquals(before, snapshot(store))

  @Test def testMissingCandidateIsNotPlayed(): Unit =
    Files.createDirectories(OnnxModel.generationDir(store, 5, 0))
    val error = Match.run(options(4, 1), store, filler, passer, komi).failure.exception
    Assertions.assertTrue(error.getMessage.contains(candidateDir.toString), error.getMessage)
    Assertions.assertEquals(List(), logLines(store))

  @Test def testEachEvaluatorPlaysItsColour(): Unit =
    val start = Game.start(5, komi).success.value
    Assertions.assertEquals(
      Some(Black),
      MatchGame.play(filler, passer, start, 1, 1L).success.value.winner
    )
    Assertions.assertEquals(
      Some(White),
      MatchGame.play(passer, filler, start, 1, 1L).success.value.winner
    )

  /** Black prefers the centre, White follows the uniform priors. */
  private val centreBlack = FakeEvaluator(Map(62 -> 2f), 0.0)
  private val uniformWhite = FakeEvaluator(Map(), 0.0)

  private def searchOf(game: Game, simulations: Int): Search =
    Search(if game.moveColor == Black then centreBlack else uniformWhite, simulations)

  @Test def testMovesAfterTheOpeningAreThoseOfPlay(): Unit =
    val start = Game.start(5, komi).success.value
    val game = MatchGame.play(centreBlack, uniformWhite, start, 8, 5L).success.value
    val opening = SelfPlayGame.openingMoves(5)
    Assertions.assertTrue(game.moves.length > opening, game.moves.length.toString)
    (opening until game.moves.length).foreach { index =>
      val before = game.atMove(index).success.value
      val played = searchOf(before, 8).move(before).success.value
      Assertions.assertEquals(played, game.moves(index), s"move $index")
    }

  @Test def testOpeningMovesAreSampledWithoutNoise(): Unit =
    // the same random generator, drawn only by the visit sampling: noise would draw from it too
    val start = Game.start(5, komi).success.value
    val game = MatchGame.play(centreBlack, uniformWhite, start, 8, 5L).success.value
    val random = scala.util.Random(5L)
    (0 until SelfPlayGame.openingMoves(5)).foreach { index =>
      val before = game.atMove(index).success.value
      val sampled = searchOf(before, 8)
        .selfPlayMove(before, random, sample = true, noise = false).success.value.move
      Assertions.assertEquals(sampled, game.moves(index), s"move $index")
    }

  @Test def testGameThatFailsFailsTheMatchAndChangesNothing(): Unit =
    withModels()
    val calls = AtomicInteger()
    val failing: Evaluator = game =>
      if calls.incrementAndGet > 20 then scala.util.Failure(IllegalStateException("network failed"))
      else filler.evaluate(game)
    val before = snapshot(store)
    val error = Match.run(options(4, 1), store, failing, passer, komi).failure.exception
    Assertions.assertEquals("network failed", error.getMessage)
    Assertions.assertEquals(before, snapshot(store))
    Assertions.assertTrue(Files.isDirectory(candidateDir))
    Assertions.assertFalse(Files.exists(Match.logPath(store, 5)))
    Assertions.assertFalse(Files.exists(store.resolve("5").resolve(Match.RejectedDirName)))

  @Test def testOpeningIsSampledSoThatSeedsDiffer(): Unit =
    val uniform = FakeEvaluator(Map(), 0.0)
    val start = Game.start(5, komi).success.value
    def game(seed: Long): Game = MatchGame.play(uniform, uniform, start, 4, seed).success.value
    Assertions.assertEquals(game(3).moves.toList, game(3).moves.toList)
    Assertions.assertNotEquals(game(3).moves.toList, game(4).moves.toList)

  @Test def testGameSeedsDifferFromSelfPlay(): Unit =
    val seeds = (0 until 100).map(Match.gameSeed(options(100, 55), _))
    Assertions.assertEquals(seeds.length, seeds.distinct.length)
    Assertions.assertFalse(seeds.contains(SelfPlayRecord.gameSeed(1, 5, 1, 0)))

  @Test def testFixtureModelsPlayAMatchAndReportIt(): Unit =
    withFixtureModels(store)
    val (code, report, error) =
      executed(args(store, "--games", "2", "--simulations", "2", "--threshold", "1")*)
    Assertions.assertEquals(0, code, error)
    val line = onlyLine(store)
    val promoted = field[Boolean](line, "promoted")
    Assertions.assertEquals(promoted, Files.isDirectory(promotedDir))
    Assertions.assertEquals(!promoted, Files.isDirectory(Match.rejectedDir(store, 5, 1, 1)))
    Assertions.assertFalse(Files.exists(candidateDir))
    // threshold 1 of 2 games: promoted only with both wins
    Assertions.assertEquals(promoted, field[Int](line, "candidate_wins") == 2)
    Assertions.assertTrue(report.contains(" of 1 as Black, "), report)
    Assertions.assertTrue(report.contains(Match.logPath(store, 5).toString), report)

  @Test def testSameSeedGivesTheSameMatchOnTheCpu(): Unit =
    val stores = Seq("first", "second").map(store.resolve)
    stores.foreach(withFixtureModels)
    val results = stores.map { dir =>
      val (code, _, error) =
        executed(args(dir, "--games", "4", "--simulations", "2", "--threshold", "1")*)
      Assertions.assertEquals(0, code, error)
      val line = onlyLine(dir)
      line.mapObject(_.remove("duration_seconds").remove("finished"))
    }
    Assertions.assertEquals(results.head, results.last)

  @Test def testMissingCandidateModelExitsWithoutPlaying(): Unit =
    Files.createDirectories(OnnxModel.generationDir(store, 5, 0))
    val (code, _, error) = executed(args(store)*)
    Assertions.assertEquals(Cli.FailureExitCode, code)
    Assertions.assertTrue(error.contains(OnnxModel.candidatePath(store, 5, 1).toString), error)
    Assertions.assertEquals(List(), logLines(store))

  @Test def testMissingOpponentExitsWithoutPlaying(): Unit =
    withFixtureModels(store)
    Files.delete(OnnxModel.path(store, 5, 0))
    val before = snapshot(store)
    val (code, _, error) = executed(args(store)*)
    Assertions.assertEquals(Cli.FailureExitCode, code)
    Assertions.assertTrue(error.contains(OnnxModel.path(store, 5, 0).toString), error)
    Assertions.assertEquals(before, snapshot(store))

  @Test def testPromotedGenerationExitsWithoutPlaying(): Unit =
    withFixtureModels(store)
    Files.createDirectories(promotedDir)
    val before = snapshot(store)
    val (code, _, error) = executed(args(store)*)
    Assertions.assertEquals(Cli.FailureExitCode, code)
    Assertions.assertTrue(error.contains(promotedDir.toString), error)
    Assertions.assertEquals(before, snapshot(store))

  @Test def testBadOptionExitsWithTheUsage(): Unit =
    val (code, _, error) = executed(args(store, "--games", "3")*)
    Assertions.assertEquals(Cli.BadOptionsExitCode, code)
    Assertions.assertTrue(error.contains("Usage"), error)

  @Test def testHelpPrintsTheUsage(): Unit =
    val (code, usage, error) = executed("--help")
    Assertions.assertEquals(0, code)
    Assertions.assertTrue(usage.contains(MatchOptions.Usage), usage)
    Assertions.assertEquals("", error)
