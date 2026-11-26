package go3d.probe

import go3d.Komi

import scala.math.BigDecimal.RoundingMode

/** The markdown report of a run: the numbers it used, the stop rule, and the metrics of each
  * board size.
  */
object Report:

  /** `metrics` has one entry for each size played, `seconds` the time each took. */
  def render(
      options: ProbeOptions,
      metrics: List[SizeMetrics],
      seconds: Map[Int, Long]
  ): String =
    val sections =
      List(numbersUsed(options, metrics, seconds), stopRule(metrics)) ++
        metrics.map(sizeSection(options.strategies, _))
    sections.mkString("# Rules probe\n\n", "\n\n", "\n")

  /** `rate` as a percentage with one decimal and the counts it comes from. */
  def percent(rate: Rate): String =
    if rate.total == 0 then "n/a"
    else s"${decimal(rate.fraction * 100, 1)}% (${rate.count} of ${rate.total})"

  def decimal(value: Double, digits: Int): String =
    BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toString

  private def numbersUsed(
      options: ProbeOptions,
      metrics: List[SizeMetrics],
      seconds: Map[Int, Long]
  ): String =
    val sizes = metrics.map { m =>
      val perPairing = options.gamesPerPairing.get(m.size).fold("?")(_.toString)
      val time = seconds.get(m.size).fold("time not recorded")(s => s"$s s")
      s"- size ${m.size}: $perPairing games per pairing, ${m.games} games, $time"
    }
    val folders = options.reportFrom.mkString(", ")
    // the CSV files do not record the seed and the parallelism of the runs that wrote them
    val run =
      if options.reportFrom.isEmpty then
        s"seed ${options.seed}, parallelism ${options.parallelism}"
      else "seed and parallelism of the runs that wrote the CSV files"
    val rebuilt =
      Option.when(options.reportFrom.nonEmpty)(s"- rebuilt from the CSV files in $folders")
    val limited = options.timeLimit.map { limit =>
      s"- each size played until its games per pairing or a time limit of $limit, if sooner"
    }
    val common = List(
      s"- komi ${Komi.Default}, $run",
      s"- strategies: ${options.strategies.mkString(", ")}"
    ) ++ rebuilt ++ limited
    ("## Numbers used" :: common ++ sizes).mkString("\n")

  private def stopRule(metrics: List[SizeMetrics]): String =
    val outcome = StopRule.evaluate(metrics.map(m => m.size -> m.flips).toMap)
    val threshold = s"${StopRule.FlipRatePercent}%"
    val explanation =
      "The rule fires when the flip rate, pooled over the pairings of a size, is at least " +
        "the threshold at any of the sizes played. The flip rate is the share of games whose " +
        "winner at komi 0.5 differs between area scoring and area scoring plus captures."
    val rows = metrics.map { m =>
      val reached = if outcome.reached.getOrElse(m.size, false) then "yes" else "no"
      s"| ${m.size} | ${percent(m.flips)} | $reached |"
    }
    val firingSizes = outcome.firingSizes.mkString(", ")
    val verdict =
      if outcome.fired then
        s"**The stop rule fired**: the flip rate is at least $threshold at size $firingSizes."
      else s"**The stop rule did not fire**: the flip rate is below $threshold at every size."
    val table = (s"| Size | Flip rate | At least $threshold |" :: "|---|---|---|" :: rows).mkString(
      "\n"
    )
    List(
      "## Stop rule",
      explanation,
      table,
      verdict,
      "The gate verdict is the user's decision at the done checkpoint, not this report's."
    ).mkString("\n\n")

  private def sizeSection(strategies: List[String], m: SizeMetrics): String =
    List(
      s"## Size ${m.size}",
      metricsTable(m),
      "### Ratings",
      ratingTable(m),
      "### Black win rate by pairing (area scoring, komi 0.5)",
      pairingTable(strategies, m)
    ).mkString("\n\n")

  private def metricsTable(m: SizeMetrics): String =
    val length = s"${decimal(m.meanMoves, 1)} / ${decimal(m.medianMoves, 1)}"
    val rows = List(
      "Games" -> s"${m.games} in ${m.pairings} pairings",
      "Black win rate, self-play" -> percent(m.selfPlay),
      "Black win rate, pooled" -> percent(m.pooled),
      "Black win rate, pooled, area plus captures" -> percent(m.pooledWithCaptures),
      "First-move term of the rating" -> firstMove(m.rating),
      "Median Black-minus-White area margin, self-play" -> decimal(m.medianMargin, 1),
      "Fitted komi" -> decimal(m.fittedKomi, 1),
      "Komi 0.5 balances the board" -> (if m.komiBalances then "yes" else "no"),
      "Game length in moves, mean / median" -> length,
      "Games ended by the move limit, not by two passes" -> percent(m.moveLimit),
      "Games with any territory" -> percent(m.territory),
      "Captures per game" -> decimal(m.capturesPerGame, 2),
      "Flip rate" -> percent(m.flips)
    )
    val body = rows.map((name, value) => s"| $name | $value |")
    ("| Metric | Value |" :: "|---|---|" :: body).mkString("\n")

  private def firstMove(fit: Rating.Fit): String =
    val elo = decimal(fit.firstMove * Rating.EloPerLogit, 0)
    val blackWins = decimal(fit.blackWinsBetweenEquals * 100, 1)
    s"$elo Elo points, Black wins $blackWins% between equals"

  private def ratingTable(m: SizeMetrics): String =
    val rows = m.rating.strengths.toList.sortBy(-_._2).map { (name, strength) =>
      s"| $name | ${decimal(strength * Rating.EloPerLogit, 0)} |"
    }
    ("| Strategy | Rating (Elo points, mean 0) |" :: "|---|---|" :: rows).mkString("\n")

  private def winRate(rate: Rate): String = s"${decimal(rate.fraction * 100, 0)}%"

  private def pairingTable(strategies: List[String], m: SizeMetrics): String =
    val header = s"| Black \\ White | ${strategies.mkString(" | ")} |"
    val separator = s"|---|${strategies.map(_ => "---").mkString("|")}|"
    val rows = strategies.map { black =>
      val cells = strategies.map { white =>
        m.blackWinsByPairing.get((black, white)).fold("-")(winRate)
      }
      s"| $black | ${cells.mkString(" | ")} |"
    }
    (header :: separator :: rows).mkString("\n")
