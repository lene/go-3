package go3d.testing

import go3d._
import org.junit.{Assert, Test}

class TestPosition:
  @Test def testPosition(): Unit =
    for
      x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Position(x, y, z)

  @Test def testPositionTooSmall(): Unit =
    assertThrowsIllegalArgument({Position(1, 1, 0)})

  @Test def testEqual(): Unit =
    Assert.assertTrue(Position(1, 1, 1) == Position(1, 1, 1))

  @Test def testNotEqual(): Unit =
    Assert.assertFalse(Position(1, 1, 1) == Position(1, 1, 2))