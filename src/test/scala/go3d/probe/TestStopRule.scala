package go3d.probe

import org.junit.jupiter.api.{Assertions, Test}

class TestStopRule:

  @Test def testTheThresholdIsTenPercent(): Unit =
    Assertions.assertEquals(10, StopRule.FlipRatePercent)

  @Test def testItFiresAtExactlyTenPercent(): Unit =
    Assertions.assertTrue(StopRule.evaluate(Map(5 -> Rate(10, 100))).fired)
    Assertions.assertTrue(StopRule.evaluate(Map(5 -> Rate(640, 6400))).fired)

  @Test def testItDoesNotFireJustBelowTenPercent(): Unit =
    Assertions.assertFalse(StopRule.evaluate(Map(5 -> Rate(9, 100))).fired)
    Assertions.assertFalse(StopRule.evaluate(Map(5 -> Rate(639, 6400))).fired)
    Assertions.assertFalse(StopRule.evaluate(Map(5 -> Rate(999, 10000))).fired)

  @Test def testItFiresIfAnySizeReachesTheThreshold(): Unit =
    val outcome = StopRule.evaluate(Map(5 -> Rate(1, 100), 7 -> Rate(2, 100), 9 -> Rate(30, 100)))
    Assertions.assertTrue(outcome.fired)
    Assertions.assertEquals(List(9), outcome.firingSizes)

  @Test def testItListsEverySizeThatReachesTheThreshold(): Unit =
    val outcome = StopRule.evaluate(Map(9 -> Rate(30, 100), 5 -> Rate(10, 100), 7 -> Rate(2, 100)))
    Assertions.assertEquals(List(5, 9), outcome.firingSizes)

  @Test def testItGivesAVerdictForEverySize(): Unit =
    val outcome = StopRule.evaluate(Map(5 -> Rate(10, 100), 7 -> Rate(9, 100), 9 -> Rate(0, 0)))
    Assertions.assertEquals(Map(5 -> true, 7 -> false, 9 -> false), outcome.reached)

  @Test def testItDoesNotFireWithoutFlips(): Unit =
    val outcome = StopRule.evaluate(Map(5 -> Rate(0, 100), 7 -> Rate(0, 50), 9 -> Rate(0, 10)))
    Assertions.assertFalse(outcome.fired)
    Assertions.assertEquals(Nil, outcome.firingSizes)

  @Test def testItDoesNotFireForSizesWithoutGames(): Unit =
    Assertions.assertFalse(StopRule.evaluate(Map(5 -> Rate(0, 0))).fired)
    Assertions.assertFalse(StopRule.evaluate(Map.empty).fired)

  @Test def testRatesAreExactForLargeCounts(): Unit =
    Assertions.assertTrue(Rate(Int.MaxValue / 10 + 1, Int.MaxValue).atLeastPercent(10))
    Assertions.assertFalse(Rate(Int.MaxValue / 10, Int.MaxValue).atLeastPercent(10))
