package go3d.testing

import go3d._
import go3d.Color.{Black, White, Empty, Sentinel}
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
    Assert.assertEquals(Sentinel, goban.stones(0)(0)(0))
    Assert.assertEquals(Sentinel, goban.stones(TestSize+1)(TestSize+1)(TestSize+1))
    for x <- 1 to TestSize do
      Assert.assertEquals(Sentinel, goban.stones(x)(0)(0))
      Assert.assertEquals(Sentinel, goban.stones(x)(TestSize+1)(TestSize+1))
      for y <- 1 to TestSize do
        Assert.assertEquals(Sentinel, goban.stones(x)(y)(0))
        Assert.assertEquals(Sentinel, goban.stones(x)(y)(TestSize+1))
        for z <- 1 to TestSize do Assert.assertEquals(Empty, goban.stones(x)(y)(z))

  @Test def testBoardSizeTooSmall(): Unit =
    assertThrows[BadBoardSize]({newGoban(1)})

  @Test def testBoardSizeTooBig(): Unit =
    assertThrows[BadBoardSize]({newGoban(MaxBoardSize+2)})

  @Test def testBoardSizeEven(): Unit =
    assertThrows[BadBoardSize]({newGoban(4)})

  @Test def testEmptyBoardAt(): Unit =
    val empty = newGoban(TestSize)
    for p <- empty.allPositions do Assert.assertEquals(Empty, empty.at(p))

  @Test def testAtWithIntsOnBorder(): Unit =
    val empty = newGoban(TestSize)
    for x <- 0 to TestSize+1
      y <- 0 to TestSize+1 by TestSize+1
      z <- 0 to TestSize+1 by TestSize+1
    do
      Assert.assertEquals(Sentinel, empty.at(x, y, z))

  @Test def testSetStoneWithMove(): Unit =
    val board = newGoban(TestSize).setStone(Move(2, 2, 2, Black))
    Assert.assertEquals(board.at(Position(2, 2, 2)), Black)

  @Test def testSetStoneWithMoveOutsideBoard(): Unit =
    val empty = newGoban(TestSize)
    assertThrows[OutsideBoard](
      {empty.setStone(Move(TestSize+2, TestSize+2, TestSize+2, Black))}
    )

  @Test def testSetStoneWithInts(): Unit =
    val board = newGoban(TestSize).setStone(2, 2, 2, Black)
    Assert.assertEquals(board.at(Position(2, 2, 2)), Black)

  @Test def testSetStoneWithIntsOnBorder(): Unit =
    val empty = newGoban(TestSize).setStone(0, 0, 0, Sentinel)
    Assert.assertEquals(empty.at(0, 0, 0), Sentinel)

  @Test def testSetStoneWithIntsOutsideBoard(): Unit =
    val empty = newGoban(TestSize)
    assertThrows[OutsideBoard](
      {empty.setStone(TestSize+2, TestSize+2, TestSize+2, Black)}
    )

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val board = newGoban(TestSize).setStone(Move(2, 2, 2, Black))
    assertThrows[PositionOccupied]({board.checkValid(Move(2, 2, 2, White))})

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = newGoban(TestSize)
    assertThrows[OutsideBoard]({empty.checkValid(Move(TestSize+1, 2, 2, White))})
    assertThrows[OutsideBoard]({empty.checkValid(Move(2, TestSize+1, 2, White))})
    assertThrows[OutsideBoard]({empty.checkValid(Move(2, 2, TestSize+1, White))})

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
    cloned.setStone(1, 1, 1, Black)
    Assert.assertEquals(Empty, original.at(1, 1, 1))

  @Test def testSetListOfStones(): Unit =
    val goban = setListOfStones(TestSize, CaptureMoves.dropRight(1))
    for move <- CaptureMoves.dropRight(1) do
      Assert.assertEquals(
        move.toString+"\n"+goban.toString,
        move.color, goban.at(move.position)
      )

  @Test def testFromStrings(): Unit =
    val goban = fromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
      ))
      checkStonesOnBoard(goban, 
        List(Move(2, 1, 1, Black), Move(1, 2, 1, Black), Move(3, 2, 1, Black), 
          Move(2, 3, 1, Black), Move(2, 2, 2, Black))
      )