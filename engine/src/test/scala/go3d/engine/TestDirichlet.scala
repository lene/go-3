package go3d.engine

import org.junit.jupiter.api.{Assertions, Test}

import scala.util.Random

class TestDirichlet:

  private val tolerance = 1e-9

  private def assertIsADistribution(sample: Vector[Double], count: Int): Unit =
    Assertions.assertEquals(count, sample.length)
    Assertions.assertTrue(sample.forall(_ >= 0), sample.toString)
    Assertions.assertEquals(1.0, sample.sum, tolerance)

  @Test def testSampleIsADistributionForTheSelfPlayAlpha(): Unit =
    // α = 10 / 126 on an empty size-5 board
    assertIsADistribution(Dirichlet.sample(10.0 / 126, 126, Random(1)), 126)

  @Test def testSampleIsADistributionForAnAlphaAboveOne(): Unit =
    assertIsADistribution(Dirichlet.sample(2.5, 4, Random(1)), 4)

  @Test def testSampleOfOneValueIsOne(): Unit =
    Assertions.assertEquals(Vector(1.0), Dirichlet.sample(10.0, 1, Random(1)))

  @Test def testSameSeedGivesTheSameSample(): Unit =
    Assertions.assertEquals(
      Dirichlet.sample(0.1, 50, Random(7)),
      Dirichlet.sample(0.1, 50, Random(7))
    )

  @Test def testEachValueHasTheMeanOneOverCount(): Unit =
    val random = Random(3)
    val samples = Vector.fill(4000)(Dirichlet.sample(0.5, 4, random))
    (0 until 4).foreach { index =>
      Assertions.assertEquals(0.25, samples.map(_(index)).sum / samples.length, 0.02)
    }

  @Test def testSmallAlphaConcentratesTheSample(): Unit =
    // with α far below 1 most of the weight goes to few values
    val sample = Dirichlet.sample(10.0 / 730, 730, Random(5))
    Assertions.assertTrue(sample.max > 0.2, sample.max.toString)
