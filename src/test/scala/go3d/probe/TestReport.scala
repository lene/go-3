package go3d.probe

import org.junit.jupiter.api.{Assertions, Test}

class TestReport:

  private val Strategies = List("random", "closestToCenter", "onStarPoints")

  private val Options: ProbeOptions = ProbeOptions.defaults(4).copy(
    gamesPerPairing = Map(5 -> 2, 7 -> 3, 9 -> 1),
    seed = 42L,
    strategies = Strategies
  )

  /** 30 self-play games at `size`, the first `flipped` of which the captures decide. */
  private def rowsOf(size: Int, flipped: Int): List[GameResult] =
    val games =
      for
        strategy <- Strategies
        number <- 0 until 10
      yield (strategy, number)
    games.zipWithIndex.map { case ((strategy, number), index) =>
      val base = if index < flipped then CaptureFlipsResult else BlackWinsResult
      base.copy(size = size, black = strategy, white = strategy, game = number)
    }

  private def metricsFor(flipped: Map[Int, Int]): List[SizeMetrics] =
    Options.sizes.map(size => Statistics.forSize(size, rowsOf(size, flipped.getOrElse(size, 0))))

  private val Seconds = Map(5 -> 11L, 7 -> 222L, 9 -> 3333L)

  private def render(flipped: Map[Int, Int]): String =
    Report.render(Options, metricsFor(flipped), Seconds)

  private val MetricLabels = List(
    "Black win rate, self-play",
    "Black win rate, pooled",
    "First-move term of the rating",
    "Fitted komi",
    "Komi 0.5 balances the board",
    "Game length in moves, mean / median",
    "Games with any territory",
    "Captures per game",
    "Flip rate",
    "### Ratings",
    "### Black win rate by pairing"
  )

  @Test def testEverySizeHasEveryMetric(): Unit =
    val report = render(Map.empty)
    for size <- List(5, 7, 9) do
      val section = report.split("## Size ").find(_.startsWith(size.toString)).getOrElse("")
      Assertions.assertTrue(section.nonEmpty, s"size $size")
      for label <- MetricLabels do Assertions.assertTrue(section.contains(label), s"$size: $label")

  @Test def testItStatesTheNumbersUsed(): Unit =
    val report = render(Map.empty)
    for text <- List(
        "seed 42",
        "parallelism 4",
        "komi 0.5",
        "random, closestToCenter, onStarPoints",
        "size 5: 2 games per pairing, 30 games, 11 s",
        "size 7: 3 games per pairing, 30 games, 222 s",
        "size 9: 1 games per pairing, 30 games, 3333 s"
      )
    do Assertions.assertTrue(report.contains(text), text)

  @Test def testAReportRebuiltFromCsvFilesSaysSoAndHasNoTimes(): Unit =
    val options = Options.copy(reportFrom = List("run-a", "run-b"))
    val report = Report.render(options, metricsFor(Map.empty), Map.empty)
    Assertions.assertTrue(report.contains("rebuilt from the CSV files in run-a, run-b"), report)
    Assertions.assertFalse(report.contains("seed 42"), report)
    Assertions.assertTrue(
      report.contains("size 5: 2 games per pairing, 30 games, time not recorded"),
      report
    )
    Assertions.assertFalse(render(Map.empty).contains("rebuilt from"))

  @Test def testItStatesThatTheStopRuleDidNotFire(): Unit =
    val report = render(Map(5 -> 2, 7 -> 1, 9 -> 0))
    Assertions.assertTrue(report.contains("The stop rule did not fire"), report)
    Assertions.assertFalse(report.contains("The stop rule fired"), report)

  @Test def testItStatesThatTheStopRuleFiredAndWhere(): Unit =
    val report = render(Map(5 -> 2, 7 -> 3, 9 -> 0))
    Assertions.assertTrue(report.contains("The stop rule fired"), report)
    Assertions.assertTrue(report.contains("at size 7"), report)
    Assertions.assertFalse(report.contains("The stop rule did not fire"), report)

  @Test def testItListsTheFlipRateOfEverySize(): Unit =
    val report = render(Map(5 -> 2, 7 -> 3, 9 -> 0))
    Assertions.assertTrue(report.contains("| 5 | 6.7% (2 of 30) | no |"), report)
    Assertions.assertTrue(report.contains("| 7 | 10.0% (3 of 30) | yes |"), report)
    Assertions.assertTrue(report.contains("| 9 | 0.0% (0 of 30) | no |"), report)

  @Test def testItLeavesTheGateVerdictToTheUser(): Unit =
    for flipped <- List(Map.empty[Int, Int], Map(5 -> 30, 7 -> 30, 9 -> 30)) do
      val report = render(flipped)
      Assertions.assertTrue(report.contains("The gate verdict is the user's decision"), report)
      for forbidden <- List("area scoring ships", "keep area scoring", "run the rules lab") do
        Assertions.assertFalse(report.contains(forbidden), forbidden)

  @Test def testTablesHaveNoBlankLinesInside(): Unit =
    val report = render(Map.empty)
    Assertions.assertFalse(report.contains("|\n\n|"), report)

  @Test def testPercent(): Unit =
    Assertions.assertEquals("33.3% (1 of 3)", Report.percent(Rate(1, 3)))
    Assertions.assertEquals("100.0% (4 of 4)", Report.percent(Rate(4, 4)))
    Assertions.assertEquals("n/a", Report.percent(Rate(0, 0)))

  @Test def testDecimal(): Unit =
    Assertions.assertEquals("2.35", Report.decimal(2.345, 2))
    Assertions.assertEquals("-1.5", Report.decimal(-1.5, 1))
    Assertions.assertEquals("4", Report.decimal(3.5, 0))
