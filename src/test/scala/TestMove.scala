package go3d.testing

import go3d._
import go3d.{Black, White}
import org.junit.{Assert, Test}

class TestMove:
  @Test def testStandardConstructor(): Unit =
    for
      x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Move(Position(x, y, z), Black)
      Move(Position(x, y, z), White)

  @Test def testOverloadedConstructor(): Unit =
    for
      x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Move(x, y, z, Black)
      Move(x, y, z, White)

  @Test def testEqual(): Unit =
    Assert.assertTrue(Move(1, 1, 1, Black) == Move(1, 1, 1, Black))
    Assert.assertTrue(Move(1, 1, 1, White) == Move(1, 1, 1, White))

  @Test def testNotEqual(): Unit =
    Assert.assertFalse(Move(1, 1, 1, Black) == Move(1, 1, 1, White))
    Assert.assertFalse(Move(1, 1, 1, White) == Move(1, 1, 2, White))

  @Test def testPassNeverEqual(): Unit =
    Assert.assertFalse(Move(1, 1, 1, Black) == Pass(Black))
    Assert.assertFalse(Pass(Black) == Pass(Black))
    Assert.assertFalse(Pass(Black) == Pass(White))

  @Test def testFilterList(): Unit =
    val list = List(Move(1, 1, 1, Black), Move(1, 1, 1, White))
    val filtered = list.filterNot(move => move == Move(1, 1, 1, White))
    Assert.assertTrue(List(Move(1, 1, 1, Black)) == filtered)

  @Test def testShortcuts(): Unit =
    val move = Move(1, 2, 3, Black)
    Assert.assertEquals(1, move.x)
    Assert.assertEquals(2, move.y)
    Assert.assertEquals(3, move.z)