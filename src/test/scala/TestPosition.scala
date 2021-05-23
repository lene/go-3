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
    assertThrows[IllegalArgumentException]({Position(1, 1, 0)})

  @Test def testEqual(): Unit =
    Assert.assertTrue(Position(1, 1, 1) == Position(1, 1, 1))

  @Test def testNotEqual(): Unit =
    Assert.assertFalse(Position(1, 1, 1) == Position(1, 1, 2))

  @Test def testMinus(): Unit =
    Assert.assertEquals(Delta(-1, -1, -1), Position(1, 1, 1) - Position(2, 2, 2))
    Assert.assertEquals(Delta(1, 1, 1), Position(2, 2, 2) - Position(1, 1, 1))

  @Test def testDistance(): Unit =
    Assert.assertEquals(3, (Position(1, 1, 1) - Position(2, 2, 2)).abs)
    Assert.assertEquals(3, (Position(2, 2, 2) - Position(1, 1, 1)).abs)
    Assert.assertEquals(0, (Position(2, 2, 2) - Position(2, 2, 2)).abs)

  @Test def testLoopOverNeighbors(): Unit =
    val center = Position(2, 3, 4)
    var neighbors = List[Position]()
    for
      x <- center.x-1 to center.x+1
      y <- center.y-1 to center.y+1
      z <- center.z-1 to center.z+1
      if (center - Position(x, y, z)).abs == 1
    do
      neighbors = neighbors.appended(Position(x, y, z))
    Assert.assertEquals(6, neighbors.length)
    Assert.assertTrue(neighbors.contains(Position(1, 3, 4)))
    Assert.assertTrue(neighbors.contains(Position(3, 3, 4)))
    Assert.assertTrue(neighbors.contains(Position(2, 2, 4)))
    Assert.assertTrue(neighbors.contains(Position(2, 4, 4)))
    Assert.assertTrue(neighbors.contains(Position(2, 3, 3)))
    Assert.assertTrue(neighbors.contains(Position(2, 3, 5)))
    Assert.assertFalse(neighbors.contains(Position(2, 3, 4)))