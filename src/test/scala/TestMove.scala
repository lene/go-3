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

  @Test def testEqual(): Unit =
    Assert.assertTrue(Move(1, 1, 1, Color.Black) == Move(1, 1, 1, Color.Black))
    Assert.assertTrue(Move(1, 1, 1, Color.White) == Move(1, 1, 1, Color.White))

  @Test def testNotEqual(): Unit =
    Assert.assertFalse(Move(1, 1, 1, Color.Black) == Move(1, 1, 1, Color.White))
    Assert.assertFalse(Move(1, 1, 1, Color.White) == Move(1, 1, 2, Color.White))

  @Test def testPassNeverEqual(): Unit =
    Assert.assertFalse(Move(1, 1, 1, Color.Black) == Pass(Color.Black))
    Assert.assertFalse(Pass(Color.Black) == Pass(Color.Black))
    Assert.assertFalse(Pass(Color.Black) == Pass(Color.White))

  @Test def testFilterList(): Unit =
    val list = List(Move(1, 1, 1, Color.Black), Move(1, 1, 1, Color.White))
    val filtered = list.filterNot(move => move == Move(1, 1, 1, Color.White))
    Assert.assertTrue(List(Move(1, 1, 1, Color.Black)) == filtered)
