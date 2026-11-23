package go3d.probe

import org.junit.jupiter.api.{Assertions, Test}

class TestStatistics:

  /** A game with territory for both players unless the stones are all that is scored. */
  private def row(
      black: String,
      white: String,
      blackArea: Int,
      whiteArea: Int,
      blackCaptures: Int,
      whiteCaptures: Int
  ): GameResult =
    BlackWinsResult.copy(
      black = black,
      white = white,
      blackSide = SideScore(blackArea - 10, blackArea, blackCaptures),
      whiteSide = SideScore(whiteArea - 5, whiteArea, whiteCaptures)
    )

  /** Four games in two pairings: three self-play games and one that the captures flip. */
  private val Rows: List[GameResult] = List(
    row("random", "random", 70, 55, 0, 0),
    row("random", "random", 50, 75, 0, 0),
    row("random", "random", 60, 60, 0, 0).copy(
      blackSide = SideScore(60, 60, 0),
      whiteSide = SideScore(60, 60, 0)
    ),
    row("random", "onStarPoints", 70, 60, 0, 12).copy(ending = Ending.Passes, moves = 100)
  )

  @Test def testMedianOfAnOddNumberOfValues(): Unit =
    Assertions.assertEquals(3.0, Statistics.median(Seq(5, 1, 3)))

  @Test def testMedianOfAnEvenNumberOfValues(): Unit =
    Assertions.assertEquals(2.5, Statistics.median(Seq(4, 1, 2, 3)))
    Assertions.assertEquals(-0.5, Statistics.median(Seq(-3, 2)))

  @Test def testMedianOfNoValues(): Unit =
    Assertions.assertEquals(0.0, Statistics.median(Seq.empty[Int]))

  @Test def testMean(): Unit =
    Assertions.assertEquals(2.5, Statistics.mean(Seq(1, 2, 3, 4)))
    Assertions.assertEquals(0.0, Statistics.mean(Seq.empty[Int]))

  @Test def testFittedKomiIsTheNearestHalfInteger(): Unit =
    val expected = Map(
      0.5 -> 0.5,
      1.5 -> 1.5,
      -0.5 -> -0.5,
      6.5 -> 6.5,
      1.0 -> 1.5,
      0.0 -> 0.5,
      3.0 -> 3.5,
      -1.0 -> -0.5,
      -4.0 -> -3.5
    )
    for (median, komi) <- expected do
      Assertions.assertEquals(komi, Statistics.fittedKomi(median), s"median $median")

  @Test def testKomiBalancesIfTheFittedKomiIsTheDefault(): Unit =
    val metrics = Statistics.forSize(5, Rows)
    Assertions.assertEquals(0.5, metrics.fittedKomi)
    Assertions.assertTrue(metrics.komiBalances)
    Assertions.assertFalse(metrics.copy(fittedKomi = 2.5).komiBalances)

  @Test def testBlackWinRates(): Unit =
    val metrics = Statistics.forSize(5, Rows)
    Assertions.assertEquals(Rate(1, 3), metrics.selfPlay)
    Assertions.assertEquals(Rate(2, 4), metrics.pooled)
    Assertions.assertEquals(Rate(1, 4), metrics.pooledWithCaptures)

  @Test def testTiesGoToWhiteAtTheDefaultKomi(): Unit =
    val tie = Statistics.forSize(5, List(Rows(2)))
    Assertions.assertEquals(Rate(0, 1), tie.pooled)

  @Test def testFlipRate(): Unit =
    Assertions.assertEquals(Rate(1, 4), Statistics.forSize(5, Rows).flips)

  @Test def testSelfPlayMedianMargin(): Unit =
    Assertions.assertEquals(0.0, Statistics.forSize(5, Rows).medianMargin)

  @Test def testGameLength(): Unit =
    val metrics = Statistics.forSize(5, Rows)
    Assertions.assertEquals(118.75, metrics.meanMoves)
    Assertions.assertEquals(125.0, metrics.medianMoves)
    Assertions.assertEquals(Rate(3, 4), metrics.moveLimit)

  @Test def testTerritoryShareAndCapturesPerGame(): Unit =
    val metrics = Statistics.forSize(5, Rows)
    Assertions.assertEquals(Rate(3, 4), metrics.territory)
    Assertions.assertEquals(3.0, metrics.capturesPerGame)

  @Test def testGamesAndPairings(): Unit =
    val metrics = Statistics.forSize(5, Rows)
    Assertions.assertEquals(4, metrics.games)
    Assertions.assertEquals(2, metrics.pairings)
    Assertions.assertEquals(5, metrics.size)

  @Test def testBlackWinRateByPairing(): Unit =
    val byPairing = Statistics.forSize(5, Rows).blackWinsByPairing
    Assertions.assertEquals(Rate(1, 3), byPairing(("random", "random")))
    Assertions.assertEquals(Rate(1, 1), byPairing(("random", "onStarPoints")))
    Assertions.assertEquals(None, byPairing.get(("onStarPoints", "random")))

  @Test def testTheRatingOfASizeIsFittedToItsGames(): Unit =
    val metrics = Statistics.forSize(5, Rows)
    Assertions.assertEquals(Set("random", "onStarPoints"), metrics.rating.strengths.keySet)
