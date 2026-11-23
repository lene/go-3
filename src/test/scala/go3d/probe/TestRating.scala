package go3d.probe

import go3d.probe.Rating.PairCounts
import org.junit.jupiter.api.{Assertions, Test}

class TestRating:

  private val Names = List("strong", "middle", "weak")

  /** The games that a model with these parameters expects in each pairing, as counts. */
  private def expected(
      strengths: Map[String, Double],
      firstMove: Double,
      games: Int
  ): List[PairCounts] =
    for
      black <- Names
      white <- Names
    yield
      val chance = Rating.logistic(strengths(black) - strengths(white) + firstMove)
      PairCounts(black, white, math.round(chance * games).toInt, games)

  private def assertClose(expected: Double, actual: Double, tolerance: Double): Unit =
    Assertions.assertEquals(expected, actual, tolerance)

  @Test def testTheFitRecoversStrengthsAndTheFirstMoveTerm(): Unit =
    val truth = Map("strong" -> 0.8, "middle" -> 0.0, "weak" -> -0.8)
    val fit = Rating.fit(expected(truth, 0.3, 2000))
    assertClose(0.3, fit.firstMove, 0.01)
    for (name, strength) <- truth do assertClose(strength, fit.strengths(name), 0.01)

  @Test def testTheFitRecoversANegativeFirstMoveTerm(): Unit =
    val truth = Map("strong" -> 0.5, "middle" -> 0.1, "weak" -> -0.6)
    val fit = Rating.fit(expected(truth, -0.4, 2000))
    assertClose(-0.4, fit.firstMove, 0.01)
    for (name, strength) <- truth do assertClose(strength, fit.strengths(name), 0.01)

  @Test def testStrongerStrategiesGetHigherRatings(): Unit =
    val truth = Map("strong" -> 1.0, "middle" -> 0.0, "weak" -> -1.0)
    val strengths = Rating.fit(expected(truth, 0.2, 500)).strengths
    Assertions.assertTrue(strengths("strong") > strengths("middle"))
    Assertions.assertTrue(strengths("middle") > strengths("weak"))

  @Test def testAFirstMoveEdgeIsNotMistakenForStrength(): Unit =
    val equal = Names.map(_ -> 0.0).toMap
    val fit = Rating.fit(expected(equal, 0.85, 1000))
    assertClose(0.85, fit.firstMove, 0.01)
    for name <- Names do assertClose(0.0, fit.strengths(name), 0.01)

  @Test def testEqualStrategiesWithoutAnEdgeAreRatedEqual(): Unit =
    val fit = Rating.fit(expected(Names.map(_ -> 0.0).toMap, 0.0, 1000))
    assertClose(0.0, fit.firstMove, 1e-6)
    for name <- Names do assertClose(0.0, fit.strengths(name), 1e-6)

  @Test def testSelfPlayAloneSaysNothingAboutStrength(): Unit =
    val counts = Names.map(name => PairCounts(name, name, 600, 1000))
    val fit = Rating.fit(counts)
    assertClose(math.log(0.6 / 0.4), fit.firstMove, 0.01)
    for name <- Names do assertClose(0.0, fit.strengths(name), 1e-6)

  @Test def testWinningBothColoursSeparatesStrengthFromTheFirstMove(): Unit =
    val counts = List(
      PairCounts("a", "b", 750, 1000),
      PairCounts("b", "a", 250, 1000)
    )
    val fit = Rating.fit(counts)
    assertClose(0.0, fit.firstMove, 0.01)
    Assertions.assertTrue(fit.strengths("a") > 0.5)
    assertClose(0.0, fit.strengths("a") + fit.strengths("b"), 0.01)

  @Test def testAStrategyThatNeverLosesGetsAFiniteRating(): Unit =
    val counts = List(
      PairCounts("a", "b", 100, 100),
      PairCounts("b", "a", 0, 100),
      PairCounts("a", "a", 55, 100),
      PairCounts("b", "b", 55, 100)
    )
    val fit = Rating.fit(counts)
    Assertions.assertTrue(fit.strengths("a").isFinite)
    Assertions.assertTrue(fit.strengths("a") > fit.strengths("b"))
    Assertions.assertTrue(fit.firstMove.isFinite)

  @Test def testNoGamesGiveNoRatings(): Unit =
    val fit = Rating.fit(Nil)
    Assertions.assertTrue(fit.strengths.isEmpty)
    Assertions.assertEquals(0.0, fit.firstMove)

  @Test def testTheChanceThatBlackWins(): Unit =
    val fit = Rating.Fit(Map("a" -> 1.0, "b" -> -1.0), 0.5)
    assertClose(Rating.logistic(2.5), fit.chanceOfBlack("a", "b"), 1e-12)
    assertClose(Rating.logistic(-1.5), fit.chanceOfBlack("b", "a"), 1e-12)
    assertClose(Rating.logistic(0.5), fit.blackWinsBetweenEquals, 1e-12)

  @Test def testTheLogistic(): Unit =
    assertClose(0.5, Rating.logistic(0.0), 1e-12)
    assertClose(0.75, Rating.logistic(math.log(3.0)), 1e-12)

  @Test def testEloPoints(): Unit =
    assertClose(400.0, math.log(10.0) * Rating.EloPerLogit, 1e-9)
