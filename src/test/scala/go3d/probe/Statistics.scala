package go3d.probe

import go3d.Black
import go3d.Komi

/** `count` of `total` games. */
final case class Rate(count: Int, total: Int):
  def fraction: Double = if total == 0 then 0.0 else count.toDouble / total

  /** Whether `count` is at least `percent` percent of `total`, without rounding. */
  def atLeastPercent(percent: Int): Boolean =
    total > 0 && count.toLong * 100 >= total.toLong * percent

/** The metrics of the games of one board size, for the report. All are at komi 0.5 unless noted;
  * `medianMargin` and `fittedKomi` are those of the self-play games.
  */
final case class SizeMetrics(
    size: Int,
    games: Int,
    pairings: Int,
    selfPlay: Rate,
    pooled: Rate,
    pooledWithCaptures: Rate,
    medianMargin: Double,
    fittedKomi: Double,
    meanMoves: Double,
    medianMoves: Double,
    moveLimit: Rate,
    territory: Rate,
    capturesPerGame: Double,
    flips: Rate,
    rating: Rating.Fit,
    blackWinsByPairing: Map[(String, String), Rate]
):
  /** Komi 0.5 balances the board if no other half-integer komi comes closer to the median margin
    * of the self-play games.
    */
  def komiBalances: Boolean = fittedKomi == Komi.Default

object Statistics:

  def forSize(size: Int, results: Seq[GameResult]): SizeMetrics =
    val komi = Komi.Default
    val selfPlay = results.filter(_.isSelfPlay)
    val margin = median(selfPlay.map(_.areaMargin))
    val byPairing = results.groupBy(r => (r.black, r.white))
    SizeMetrics(
      size = size,
      games = results.length,
      pairings = byPairing.size,
      selfPlay = rate(selfPlay)(_.winnerByArea(komi) == Black),
      pooled = rate(results)(_.winnerByArea(komi) == Black),
      pooledWithCaptures = rate(results)(_.winnerByAreaWithCaptures(komi) == Black),
      medianMargin = margin,
      fittedKomi = fittedKomi(margin),
      meanMoves = mean(results.map(_.moves)),
      medianMoves = median(results.map(_.moves)),
      moveLimit = rate(results)(_.ending == Ending.MoveLimit),
      territory = rate(results)(_.hasTerritory),
      capturesPerGame = mean(results.map(_.captures)),
      flips = rate(results)(_.flips(komi)),
      rating = Rating.fit(pairCounts(byPairing, komi)),
      blackWinsByPairing = byPairing.view.mapValues(rate(_)(_.winnerByArea(komi) == Black)).toMap
    )

  def rate(results: Seq[GameResult])(holds: GameResult => Boolean): Rate =
    Rate(results.count(holds), results.length)

  def mean(values: Seq[Int]): Double =
    if values.isEmpty then 0.0 else values.map(_.toDouble).sum / values.length

  /** The middle value, or the mean of the two middle ones; 0 for no values. */
  def median(values: Seq[Int]): Double =
    val sorted = values.sorted
    val n = sorted.length
    if n == 0 then 0.0
    else if n % 2 == 1 then sorted(n / 2).toDouble
    else (sorted(n / 2 - 1) + sorted(n / 2)) / 2.0

  /** The half-integer komi nearest to `medianMargin`, the median of Black's area score minus
    * White's. With a margin that is an integer, two komis are equally near, and the higher one is
    * taken. About half of the games then end with a margin above the komi.
    */
  def fittedKomi(medianMargin: Double): Double = math.floor(medianMargin) + 0.5

  private def pairCounts(
      byPairing: Map[(String, String), Seq[GameResult]],
      komi: Double
  ): Seq[Rating.PairCounts] =
    byPairing.toSeq.map { case ((black, white), games) =>
      Rating.PairCounts(black, white, games.count(_.winnerByArea(komi) == Black), games.length)
    }
