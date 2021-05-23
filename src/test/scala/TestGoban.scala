package go3d.testing

import go3d._
import org.junit.{Assert, Ignore, Test}

class TestGoban:

  @Test def testGameCtorBasic(): Unit =
    val goban = newGoban(TestSize)
    Assert.assertEquals(TestSize, goban.size)

  @Test def testMemoryAllocation(): Unit =
    val goban = newGoban(TestSize)
    Assert.assertEquals(TestSize+2, goban.stones.length)
    Assert.assertEquals(TestSize+2, goban.stones(0).length)
    Assert.assertEquals(TestSize+2, goban.stones(TestSize+1).length)
    Assert.assertEquals(TestSize+2, goban.stones(0)(0).length)
    Assert.assertEquals(TestSize+2, goban.stones(TestSize+1)(TestSize+1).length)

  @Test def testSentinels(): Unit =
    val goban = newGoban(TestSize)
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
    assertThrowsIllegalArgument({newGoban(1)})

  @Test def testBoardSizeTooBig(): Unit =
    assertThrowsIllegalArgument({newGoban(MaxBoardSize+2)})

  @Test def testBoardSizeEven(): Unit =
    assertThrowsIllegalArgument({newGoban(4)})

  @Test def testEmptyBoardAt(): Unit =
    val empty = newGoban(TestSize)
    for p <- empty.allPositions do Assert.assertEquals(Color.Empty, empty.at(p))

  @Test def testAtWithIntsOnBorder(): Unit =
    val empty = newGoban(TestSize)
    for x <- 0 to TestSize+1
      y <- 0 to TestSize+1 by TestSize+1
      z <- 0 to TestSize+1 by TestSize+1
    do
      Assert.assertEquals(Color.Sentinel, empty.at(x, y, z))

  @Test def testSetStoneWithMove(): Unit =
    val board = newGoban(TestSize).setStone(Move(2, 2, 2, Color.Black))
    Assert.assertEquals(board.at(Position(2, 2, 2)), Color.Black)

  @Test def testSetStoneWithMoveOutsideBoard(): Unit =
    val empty = newGoban(TestSize)
    assertThrowsIllegalArgument(
      {empty.setStone(Move(TestSize+2, TestSize+2, TestSize+2, Color.Black))}
    )

  @Test def testSetStoneWithInts(): Unit =
    val board = newGoban(TestSize).setStone(2, 2, 2, Color.Black)
    Assert.assertEquals(board.at(Position(2, 2, 2)), Color.Black)

  @Test def testSetStoneWithIntsOnBorder(): Unit =
    val empty = newGoban(TestSize).setStone(0, 0, 0, Color.Sentinel)
    Assert.assertEquals(empty.at(0, 0, 0), Color.Sentinel)

  @Test def testSetStoneWithIntsOutsideBoard(): Unit =
    val empty = newGoban(TestSize)
    assertThrowsIllegalArgument(
      {empty.setStone(TestSize+2, TestSize+2, TestSize+2, Color.Black)}
    )

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val board = newGoban(TestSize).setStone(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({board.checkValid(Move(2, 2, 2, Color.White))})

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = newGoban(TestSize)
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
    val original = newGoban(TestSize)
    val cloned = original.clone()
    cloned.setStone(1, 1, 1, Color.Black)
    Assert.assertEquals(Color.Empty, original.at(1, 1, 1))
