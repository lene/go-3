package go3d.probe

import org.junit.jupiter.api.{Assertions, Test}

import scala.concurrent.duration.*

class TestProbeOptions:

  private def parse(args: String*): Either[String, ProbeOptions] =
    ProbeOptions.parse(args, 4)

  private def failure(args: String*): String =
    parse(args*).swap.getOrElse("it parsed")

  @Test def testTimeLimitIsReadInSecondsMinutesOrHours(): Unit =
    Assertions.assertEquals(None, ProbeOptions.defaults(1).timeLimit)
    val limits = List("90s" -> 90.seconds, "30m" -> 30.minutes, "5h" -> 5.hours, "0s" -> 0.seconds)
    for (text, limit) <- limits do
      val parsed = parse("--time-limit", text).map(_.timeLimit)
      Assertions.assertEquals(Right(Some(limit)), parsed, text)

  @Test def testTimeLimitWithoutAUnitOrNotAWholeNumberIsRejected(): Unit =
    for text <- List("5", "5x", "-1h", "h", "1.5h", "") do
      Assertions.assertTrue(failure("--time-limit", text).contains("time limit"), text)

  @Test def testDefaults(): Unit =
    val options = parse().getOrElse(ProbeOptions.defaults(0))
    Assertions.assertEquals(List(5, 7, 9), options.sizes)
    Assertions.assertEquals(Map(5 -> 100, 7 -> 100, 9 -> 100), options.gamesPerPairing)
    Assertions.assertEquals(1L, options.seed)
    Assertions.assertEquals(4, options.parallelism)
    Assertions.assertEquals("results/rules-probe", options.outDir)
    Assertions.assertEquals(ProbeOptions.StrategyNames, options.strategies)

  @Test def testTheEightStrategiesAreTheDefaultPlayers(): Unit =
    Assertions.assertEquals(8, ProbeOptions.StrategyNames.length)
    Assertions.assertEquals(64, ProbeOptions.defaults(1).pairings.length)

  @Test def testPairingsIncludeEveryOrderedPairAndSelfPlay(): Unit =
    val options = ProbeOptions.defaults(1).copy(strategies = List("random", "onStarPoints"))
    Assertions.assertEquals(
      List(
        ("random", "random"),
        ("random", "onStarPoints"),
        ("onStarPoints", "random"),
        ("onStarPoints", "onStarPoints")
      ),
      options.pairings
    )

  @Test def testAllOptionsAreRead(): Unit =
    val options = parse(
      "--sizes",
      "7,5",
      "--games",
      "12",
      "--seed",
      "-3",
      "--parallelism",
      "2",
      "--out",
      "somewhere",
      "--strategies",
      "random,closestToCenter"
    ).getOrElse(ProbeOptions.defaults(0))
    Assertions.assertEquals(List(7, 5), options.sizes)
    Assertions.assertEquals(Map(7 -> 12, 5 -> 12), options.gamesPerPairing)
    Assertions.assertEquals(-3L, options.seed)
    Assertions.assertEquals(2, options.parallelism)
    Assertions.assertEquals("somewhere", options.outDir)
    Assertions.assertEquals(List("random", "closestToCenter"), options.strategies)

  @Test def testGamesPerSize(): Unit =
    val options = parse("--games", "5=200,7=100,9=20").getOrElse(ProbeOptions.defaults(0))
    Assertions.assertEquals(Map(5 -> 200, 7 -> 100, 9 -> 20), options.gamesPerPairing)

  @Test def testReportFromIsReadAndPlaysNothingByDefault(): Unit =
    Assertions.assertEquals(Nil, ProbeOptions.defaults(1).reportFrom)
    val options = parse("--report-from", "run-a,run-b").getOrElse(ProbeOptions.defaults(0))
    Assertions.assertEquals(List("run-a", "run-b"), options.reportFrom)

  @Test def testGamesPerSizeNeedAnEntryForEachSize(): Unit =
    Assertions.assertTrue(failure("--games", "5=200,7=100").contains("--games"))
    Assertions.assertTrue(failure("--sizes", "5", "--games", "5=2,7=2").contains("--games"))
    Assertions.assertTrue(failure("--games", "5=2,5=3,7=1,9=1").contains("--games"))

  @Test def testEvenSizeIsRejected(): Unit =
    for size <- List("4", "6", "8", "10") do
      Assertions.assertTrue(failure("--sizes", size).contains(s"size $size"), size)

  @Test def testSizesOutsideTheSupportedOnesAreRejected(): Unit =
    for size <- List("3", "11", "25", "0", "-5", "x", "") do
      Assertions.assertTrue(failure("--sizes", s"5,$size").contains("size"), size)

  @Test def testRepeatedSizeIsRejected(): Unit =
    Assertions.assertTrue(failure("--sizes", "5,5").contains("twice"))

  @Test def testUnknownStrategyIsRejected(): Unit =
    val message = failure("--strategies", "random,cleverest")
    Assertions.assertTrue(message.contains("unknown strategy 'cleverest'"), message)
    Assertions.assertTrue(message.contains("prioritiseCapture"), message)

  @Test def testRepeatedStrategyIsRejected(): Unit =
    Assertions.assertTrue(failure("--strategies", "random,random").contains("twice"))

  @Test def testNumbersMustBeInRange(): Unit =
    for args <- List(
        List("--games", "0"),
        List("--games", "1000000"),
        List("--games", "many"),
        List("--seed", "1.5"),
        List("--parallelism", "0"),
        List("--parallelism", "-1")
      )
    do Assertions.assertTrue(parse(args*).isLeft, args.toString)

  @Test def testUnknownOptionsAndMalformedArgumentsAreRejected(): Unit =
    Assertions.assertTrue(failure("--size", "5").contains("unknown option '--size'"))
    Assertions.assertTrue(failure("5").contains("expected an option"))
    Assertions.assertTrue(failure("--sizes").contains("needs a value"))
    Assertions.assertTrue(failure("--seed", "1", "--seed", "2").contains("twice"))
