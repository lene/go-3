package go3d.testing

import go3d._
import org.junit.{Assert, Test}

class TestMove:
  @Test def testStandardConstructor(): Unit =
    for
      x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Move(Position(x, y, z), Color.Black)
      Move(Position(x, y, z), Color.White)

  @Test def testOverloadedConstructor(): Unit =
    for
      x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Move(x, y, z, Color.Black)
      Move(x, y, z, Color.White)

  @Test def testMoveIsNotPass(): Unit =
    Assert.assertFalse(Move(1, 1, 1, Color.Black).isPass)

  @Test def testPassIsPass(): Unit =
    Assert.assertTrue(Pass(Color.Black).isPass)
