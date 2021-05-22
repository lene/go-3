package go3d.testing

import go3d._
import org.junit.{Assert, Ignore, Test}

class TestGoban:

  @Test def testGameCtorBasic(): Unit =
    val goban = Goban(TestSize, initializeBoard(TestSize))
    Assert.assertEquals(TestSize, goban.size)

  @Test def testMemoryAllocation(): Unit =
    val goban = Goban(TestSize, initializeBoard(TestSize))
    Assert.assertEquals(TestSize+2, goban.stones.length)
    Assert.assertEquals(TestSize+2, goban.stones(0).length)
    Assert.assertEquals(TestSize+2, goban.stones(TestSize+1).length)
    Assert.assertEquals(TestSize+2, goban.stones(0)(0).length)
    Assert.assertEquals(TestSize+2, goban.stones(TestSize+1)(TestSize+1).length)

  @Test def testSentinels(): Unit =
    val goban = Goban(TestSize, initializeBoard(TestSize))
    Assert.assertEquals(Color.Sentinel, goban.stones(0)(0)(0))
    Assert.assertEquals(Color.Sentinel, goban.stones(TestSize+1)(TestSize+1)(TestSize+1))
    for x <- 1 to TestSize do
      Assert.assertEquals(Color.Sentinel, goban.stones(x)(0)(0))
      Assert.assertEquals(Color.Sentinel, goban.stones(x)(TestSize+1)(TestSize+1))
      for y <- 1 to TestSize do
        Assert.assertEquals(Color.Sentinel, goban.stones(x)(y)(0))
        Assert.assertEquals(Color.Sentinel, goban.stones(x)(y)(TestSize+1))
        for z <- 1 to TestSize do Assert.assertEquals(Color.Empty, goban.stones(x)(y)(z))

  @Test def testBoardSizeTooSmall(): Unit =
    assertThrowsIllegalArgument({Goban(1, initializeBoard(1))})

  @Test def testBoardSizeTooBig(): Unit =
    assertThrowsIllegalArgument({Goban(MaxBoardSize+2, initializeBoard(MaxBoardSize+2))})

  @Test def testBoardSizeEven(): Unit =
    assertThrowsIllegalArgument({Goban(4, initializeBoard(4))})

  @Test def testEmptyBoardAt(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    for x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Assert.assertEquals(Color.Empty, empty.at(Position(x, y, z)))

  @Test def testAtWithIntsOnBorder(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    for x <- 0 to TestSize+1
      y <- 0 to TestSize+1 by TestSize+1
      z <- 0 to TestSize+1 by TestSize+1
    do
      Assert.assertEquals(Color.Sentinel, empty.at(x, y, z))

  @Test def testSetStoneWithMove(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    empty.setStone(Move(2, 2, 2, Color.Black))
    Assert.assertEquals(empty.at(Position(2, 2, 2)), Color.Black)

  @Test def testSetStoneWithMoveOutsideBoard(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    assertThrowsIllegalArgument(
      {empty.setStone(Move(TestSize+2, TestSize+2, TestSize+2, Color.Black))}
    )

  @Test def testSetStoneWithInts(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    empty.setStone(2, 2, 2, Color.Black)
    Assert.assertEquals(empty.at(Position(2, 2, 2)), Color.Black)

  @Test def testSetStoneWithIntsOnBorder(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    empty.setStone(0, 0, 0, Color.Sentinel)
    Assert.assertEquals(empty.at(0, 0, 0), Color.Sentinel)

  @Test def testSetStoneWithIntsOutsideBoard(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    assertThrowsIllegalArgument(
      {empty.setStone(TestSize+2, TestSize+2, TestSize+2, Color.Black)}
    )

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    empty.setStone(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({empty.checkValid(Move(2, 2, 2, Color.White))})

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = Goban(TestSize, initializeBoard(TestSize))
    assertThrowsIllegalArgument({empty.checkValid(Move(TestSize+1, 2, 2, Color.White))})
    assertThrowsIllegalArgument({empty.checkValid(Move(2, TestSize+1, 2, Color.White))})
    assertThrowsIllegalArgument({empty.checkValid(Move(2, 2, TestSize+1, Color.White))})

  @Test def testDeepCopy2D(): Unit =
    val original = Array(Array(1, 2), Array(3,4))
    val cloned = deepCopy(original)
    cloned(0)(0) = 9
    Assert.assertEquals(1, original(0)(0))

  @Test def testDeepCopy3D(): Unit =
    val original = Array(Array(Array(1, 2), Array(3,4)), Array(Array(5, 6), Array(7,8)))
    val cloned = deepCopy(original)
    cloned(0)(0)(0) = 9
    Assert.assertEquals(1, original(0)(0)(0))

  @Test def testClone(): Unit =
    val original = Goban(TestSize, initializeBoard(TestSize))
    val cloned = original.clone()
    cloned.setStone(1, 1, 1, Color.Black)
    Assert.assertEquals(Color.Empty, original.at(1, 1, 1))
