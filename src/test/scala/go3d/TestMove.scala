package go3d

import org.junit.jupiter.api.{Assertions, Test}

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
    Assertions.assertTrue(Move(1, 1, 1, Black) == Move(1, 1, 1, Black))
    Assertions.assertTrue(Move(1, 1, 1, White) == Move(1, 1, 1, White))

  @Test def testNotEqual(): Unit =
    Assertions.assertFalse(Move(1, 1, 1, Black) == Move(1, 1, 1, White))
    Assertions.assertFalse(Move(1, 1, 1, White) == Move(1, 1, 2, White))

  @Test def testPassNeverEqual(): Unit =
    Assertions.assertFalse(Move(1, 1, 1, Black) == Pass(Black))
    Assertions.assertFalse(Pass(Black) == Pass(Black))
    Assertions.assertFalse(Pass(Black) == Pass(White))

  @Test def testFilterList(): Unit =
    val list = List(Move(1, 1, 1, Black), Move(1, 1, 1, White))
    val filtered = list.filterNot(move => move == Move(1, 1, 1, White))
    Assertions.assertTrue(List(Move(1, 1, 1, Black)) == filtered)

  @Test def testShortcuts(): Unit =
    val move = Move(1, 2, 3, Black)
    Assertions.assertEquals(1, move.x)
    Assertions.assertEquals(2, move.y)
    Assertions.assertEquals(3, move.z)