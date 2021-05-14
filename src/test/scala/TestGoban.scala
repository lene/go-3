package go3d.testing

import go3d._
import org.junit.{Assert, Ignore, Test}

class TestGoban:

  @Test def testGobanCtorBasic(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(TestSize, goban.size)

  @Test def testMemoryAllocation(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(TestSize+2, goban.stones.length)
    Assert.assertEquals(TestSize+2, goban.stones(0).length)
    Assert.assertEquals(TestSize+2, goban.stones(TestSize+1).length)
    Assert.assertEquals(TestSize+2, goban.stones(0)(0).length)
    Assert.assertEquals(TestSize+2, goban.stones(TestSize+1)(TestSize+1).length)

  @Test def testSentinels(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(Color.Sentinel, goban.stones(0)(0)(0))
    Assert.assertEquals(Color.Sentinel, goban.stones(TestSize+1)(TestSize+1)(TestSize+1))
    for x <- 1 to TestSize do
      Assert.assertEquals(Color.Sentinel, goban.stones(x)(0)(0))
      Assert.assertEquals(Color.Sentinel, goban.stones(x)(TestSize+1)(TestSize+1))
      for y <- 1 to TestSize do
        Assert.assertEquals(Color.Sentinel, goban.stones(x)(y)(0))
        Assert.assertEquals(Color.Sentinel, goban.stones(x)(y)(TestSize+1))
        for z <- 1 to TestSize do Assert.assertEquals(Color.Empty, goban.stones(x)(y)(z))

  @Test def testBoardSizeTooSmall(): Unit = assertThrowsIllegalArgument({Goban(1)})

  @Test def testBoardSizeTooBig(): Unit = assertThrowsIllegalArgument({Goban(MaxBoardSize+2)})

  @Test def testBoardSizeEven(): Unit = assertThrowsIllegalArgument({Goban(4)})

  @Test def testEmptyBoardToStringEmptyPlaces(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(Math.pow(TestSize, 3).toInt, goban.toString.count(_ == ' '))

  @Test def testEmptyBoardToStringSentinels(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(
      2*(TestSize+2)*TestSize + 2*TestSize*TestSize,
      goban.toString.count(_ == '·')
    )

  @Test def testEmptyBoardToStringNewlines(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(TestSize+2, goban.toString.count(_ == '\n'))

  @Test def testEmptyBoardAt(): Unit =
    val empty = Goban(TestSize)
    for x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Assert.assertEquals(Color.Empty, empty.at(Position(x, y, z)))

  @Test def testSetStone(): Unit =
    val empty = Goban(TestSize)
    val newBoard = empty.makeMove(Move(2, 2, 2, Color.Black))
    Assert.assertEquals("\n"+newBoard.toString, newBoard.at(Position(2, 2, 2)), Color.Black)

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val empty = Goban(TestSize)
    val newBoard = empty.makeMove(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({empty.makeMove(Move(2, 2, 2, Color.White))})

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = Goban(TestSize)
    assertThrowsIllegalMove({empty.makeMove(Move(TestSize+1, 2, 2, Color.White))})
    assertThrowsIllegalMove({empty.makeMove(Move(2, TestSize+1, 2, Color.White))})
    assertThrowsIllegalMove({empty.makeMove(Move(2, 2, TestSize+1, Color.White))})

  @Test def testSetTwoSubsequentStonesOfDifferentColorSucceeds(): Unit =
    val empty = Goban(TestSize)
    val firstMove = empty.makeMove(Move(2, 2, 2, Color.Black))
    val secondMove = firstMove.makeMove(Move(2, 2, 1, Color.White))
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 2)), Color.Black)
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 1)), Color.White)

  @Test def testSetTwoSubsequentStonesOfSameColorFails(): Unit =
    val empty = Goban(TestSize)
    val firstMove = empty.makeMove(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({firstMove.makeMove(Move(2, 2, 1, Color.Black))})

  @Test def testSetAndPassSucceeds(): Unit =
    val empty = Goban(TestSize)
    val firstMove = empty.makeMove(Move(2, 2, 2, Color.Black))
    val secondMove = firstMove.makeMove(Pass(Color.White))
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 2)), Color.Black)
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 1)), Color.Empty)
  
  @Test def testGameOverAfterTwoConsecutivePasses(): Unit =
    val empty = Goban(TestSize)
    val firstMove = empty.makeMove(Pass(Color.Black))
    assertThrowsGameOver({firstMove.makeMove(Pass(Color.White))})

  @Test def testPlayListOfMoves(): Unit =
    val goban = playListOfMoves(TestSize, CaptureMoves.dropRight(1))
    for move <- CaptureMoves.dropRight(1) do
      Assert.assertEquals(
        move.toString+"\n"+goban.toString,
        move.color, goban.at(move.position)
      )

  @Test def testCaptureStone(): Unit =
    val goban = playListOfMoves(TestSize, CaptureMoves)
    Assert.assertEquals(
      "\n"+goban.toString,
      Color.Empty, goban.at(Position(2, 2, 1))
    )

  @Test def testCaptureStoneDoesNotRemoveOthers(): Unit =
    val goban = playListOfMoves(TestSize, CaptureMoves)
    val presentStones = CaptureMoves.filterNot(move => move == Move(2, 2, 1, Color.White))
    for move <- presentStones do
      Assert.assertEquals(
        move.toString+"\n"+goban.toString,
        move.color, goban.at(move.position)
      )

  @Test def testDoNotCaptureStoneWithNeighbors(): Unit =
    val moves =
      Move(1, 1, 1, Color.Black) :: Move(2, 1, 1, Color.White) ::
      Move(3, 1, 1, Color.Black) :: Move(2, 1, 2, Color.White) ::
      Move(2, 2, 1, Color.Black) :: Nil
    val goban = playListOfMoves(TestSize, moves)
    checkStonesOnBoard(goban, moves)

  @Test def testTwoNonConsecutivePasses(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Color.Black), Pass(Color.White),
      Move(3, 1, 1, Color.Black), Pass(Color.White)
    )
    val goban = playListOfMoves(TestSize, moves)

  @Test def testConnectedStoneOneStone(): Unit =
    val moves = List(Move(1, 1, 1, Color.Black))
    val goban = playListOfMoves(TestSize, moves)
    Assert.assertEquals(moves, goban.connectedStones(moves(0)))

  @Test def testConnectedStoneOneStoneLeavesBoardUnchanged(): Unit =
    val moves = List(Move(1, 1, 1, Color.Black))
    val goban = playListOfMoves(TestSize, moves)
    Assert.assertEquals(Color.Black, goban.at(Position(1, 1, 1)))
    for
      x <- 1 to 3
      y <- 1 to 3
      z <- 1 to 3
      if (x, y, z) != (1, 1, 1)
    do
      Assert.assertEquals(Color.Empty, goban.at(Position(x, y, z)))

  @Test def testConnectedStoneTwoUnconnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Color.Black), Pass(Color.White), Move(2, 2, 1, Color.Black)
    )
    val goban = playListOfMoves(TestSize, moves)
    Assert.assertEquals(1, goban.connectedStones(Move(1, 1, 1, Color.Black)).length)
    Assert.assertEquals(1, goban.connectedStones(Move(2, 2, 1, Color.Black)).length)


  @Test def testConnectedStoneTwoConnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Color.Black), Pass(Color.White), Move(2, 1, 1, Color.Black)
    )
    val goban = playListOfMoves(TestSize, moves)
    Assert.assertEquals(2, goban.connectedStones(Move(1, 1, 1, Color.Black)).length)
    Assert.assertEquals(2, goban.connectedStones(Move(2, 1, 1, Color.Black)).length)

  @Test def testConnectedStoneMinimalEye(): Unit =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Color.Black), Pass(Color.White), Move(1, 2, 1, Color.Black), Pass(Color.White),
      Move(2, 1, 2, Color.Black), Pass(Color.White), Move(1, 2, 2, Color.Black), Pass(Color.White),
      Move(1, 1, 2, Color.Black), Pass(Color.White)
    )
    val goban = playListOfMoves(TestSize, moves)
    Assert.assertEquals(5, goban.connectedStones(Move(2, 1, 1, Color.Black)).length)
    Assert.assertEquals(5, goban.connectedStones(Move(1, 2, 1, Color.Black)).length)
    Assert.assertEquals(5, goban.connectedStones(Move(2, 1, 2, Color.Black)).length)
    Assert.assertEquals(5, goban.connectedStones(Move(1, 2, 2, Color.Black)).length)
    Assert.assertEquals(5, goban.connectedStones(Move(1, 1, 2, Color.Black)).length)

  @Test def testCaptureStoneWithNeighbors(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Color.Black), Move(2, 1, 1, Color.White),
      Move(3, 1, 1, Color.Black), Move(2, 1, 2, Color.White),
      Move(2, 2, 1, Color.Black), Pass(Color.White),
      Move(1, 1, 2, Color.Black), Pass(Color.White),
      Move(3, 1, 2, Color.Black), Pass(Color.White),
      Move(2, 2, 2, Color.Black), Pass(Color.White)
    )
    var goban = playListOfMoves(TestSize, moves)
    checkStonesOnBoard(goban, moves)
    goban = goban.makeMove(Move(2, 1, 3, Color.Black))
    Assert.assertEquals("\n"+goban.toString, Color.Empty, goban.at(Position(2, 1, 1)))
    Assert.assertEquals("\n"+goban.toString, Color.Empty, goban.at(Position(2, 1, 2)))

  @Test def testCaptureMinimalEye(): Unit =
    val goban = buildAndCaptureEye()
    Assert.assertEquals(Color.Empty, goban.at(Position(2, 1, 1)))
    Assert.assertEquals(Color.Empty, goban.at(Position(1, 2, 1)))
    Assert.assertEquals(Color.Empty, goban.at(Position(2, 1, 2)))
    Assert.assertEquals(Color.Empty, goban.at(Position(1, 2, 2)))
    Assert.assertEquals(Color.Empty, goban.at(Position(1, 1, 2)))

  def buildAndCaptureEye(): Goban =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Color.Black), Pass(Color.White), Move(1, 2, 1, Color.Black), Pass(Color.White),
      Move(2, 1, 2, Color.Black), Pass(Color.White), Move(1, 2, 2, Color.Black), Pass(Color.White),
      Move(1, 1, 2, Color.Black),
      // build the eye first and then encircle it, IMHO that is easier to read
      Move(1, 3, 1, Color.White), Pass(Color.Black), Move(2, 2, 1, Color.White), Pass(Color.Black),
      Move(3, 1, 1, Color.White), Pass(Color.Black), Move(1, 3, 2, Color.White), Pass(Color.Black),
      Move(2, 2, 2, Color.White), Pass(Color.Black), Move(3, 1, 2, Color.White), Pass(Color.Black),
      Move(1, 1, 3, Color.White), Pass(Color.Black), Move(2, 1, 3, Color.White), Pass(Color.Black),
      Move(1, 2, 3, Color.White), Pass(Color.Black)
    )
    var goban = playListOfMoves(TestSize, moves)
    Assert.assertEquals(5, goban.connectedStones(Move(2, 1, 1, Color.Black)).length)
    Assert.assertEquals(5, goban.connectedStones(Move(1, 2, 1, Color.Black)).length)
    Assert.assertEquals(5, goban.connectedStones(Move(2, 1, 2, Color.Black)).length)
    Assert.assertEquals(5, goban.connectedStones(Move(1, 2, 2, Color.Black)).length)
    Assert.assertEquals(5, goban.connectedStones(Move(1, 1, 2, Color.Black)).length)
    goban.makeMove(Move(1, 1, 1, Color.White))

  @Test def testCapturedStonesAreListed(): Unit =
    val goban = buildAndCaptureEye()
    Assert.assertEquals(5, goban.captures.length)

  @Test def testCapturedStonesAreCounted(): Unit =
    val goban = buildAndCaptureEye()
    Assert.assertEquals(5, goban.captures(Color.Black))
    Assert.assertEquals(0, goban.captures(Color.White))
