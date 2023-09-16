package go3d

import org.junit.jupiter.api.{Assertions, Test}

class TestPosition:
  @Test def testPosition(): Unit =
    for
      x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Position(x, y, z)

  @Test def testPositionTooSmall(): Unit =
    Assertions.assertThrows(classOf[OutsideBoard], () => Position(1, 1, 0))

  @Test def testEqual(): Unit =
    Assertions.assertTrue(Position(1, 1, 1) == Position(1, 1, 1))

  @Test def testNotEqual(): Unit =
    Assertions.assertFalse(Position(1, 1, 1) == Position(1, 1, 2))

  @Test def testMinus(): Unit =
    Assertions.assertEquals(Delta(-1, -1, -1), Position(1, 1, 1) - Position(2, 2, 2))
    Assertions.assertEquals(Delta(1, 1, 1), Position(2, 2, 2) - Position(1, 1, 1))

  @Test def testDistance(): Unit =
    Assertions.assertEquals(3, (Position(1, 1, 1) - Position(2, 2, 2)).abs)
    Assertions.assertEquals(3, (Position(2, 2, 2) - Position(1, 1, 1)).abs)
    Assertions.assertEquals(0, (Position(2, 2, 2) - Position(2, 2, 2)).abs)

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
    assertPositionsEqual(
      List((1, 3, 4), (3, 3, 4), (2, 2, 4), (2, 4, 4), (2, 3, 3), (2, 3, 5)), neighbors
    )
    Assertions.assertFalse(neighbors.contains(Position(2, 3, 4)))