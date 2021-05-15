package go3d.testing

import go3d._
import org.junit.{Assert, Ignore, Test}

class TestGame:

  @Test def testGameCtorBasic(): Unit =
    val game = Game(TestSize)
    Assert.assertEquals(TestSize, game.size)

  @Test def testEmptyBoardToStringEmptyPlaces(): Unit =
    val game = Game(TestSize)
    Assert.assertEquals(Math.pow(TestSize, 3).toInt, game.toString.count(_ == ' '))

  @Test def testEmptyBoardToStringSentinels(): Unit =
    val game = Game(TestSize)
    Assert.assertEquals(
      2*(TestSize+2)*TestSize + 2*TestSize*TestSize,
      game.toString.count(_ == '·')
    )

  @Test def testEmptyBoardToStringNewlines(): Unit =
    val game = Game(TestSize)
    Assert.assertEquals(TestSize+2, game.toString.count(_ == '\n'))

  @Test def testEmptyBoardAt(): Unit =
    val empty = Game(TestSize)
    for x <- 1 to TestSize
      y <- 1 to TestSize
      z <- 1 to TestSize
    do
      Assert.assertEquals(Color.Empty, empty.at(Position(x, y, z)))

  @Test def testSetStone(): Unit =
    val empty = Game(TestSize)
    val newBoard = empty.makeMove(Move(2, 2, 2, Color.Black))
    Assert.assertEquals("\n"+newBoard.toString, newBoard.at(Position(2, 2, 2)), Color.Black)

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val empty = Game(TestSize)
    val newBoard = empty.makeMove(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({empty.makeMove(Move(2, 2, 2, Color.White))})

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = Game(TestSize)
    assertThrowsIllegalMove({empty.makeMove(Move(TestSize+1, 2, 2, Color.White))})
    assertThrowsIllegalMove({empty.makeMove(Move(2, TestSize+1, 2, Color.White))})
    assertThrowsIllegalMove({empty.makeMove(Move(2, 2, TestSize+1, Color.White))})

  @Test def testSetTwoSubsequentStonesOfDifferentColorSucceeds(): Unit =
    val empty = Game(TestSize)
    val firstMove = empty.makeMove(Move(2, 2, 2, Color.Black))
    val secondMove = firstMove.makeMove(Move(2, 2, 1, Color.White))
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 2)), Color.Black)
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 1)), Color.White)

  @Test def testSetTwoSubsequentStonesOfSameColorFails(): Unit =
    val empty = Game(TestSize)
    val firstMove = empty.makeMove(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({firstMove.makeMove(Move(2, 2, 1, Color.Black))})

  @Test def testSetAndPassSucceeds(): Unit =
    val empty = Game(TestSize)
    val firstMove = empty.makeMove(Move(2, 2, 2, Color.Black))
    val secondMove = firstMove.makeMove(Pass(Color.White))
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 2)), Color.Black)
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 1)), Color.Empty)
  
  @Test def testGameOverAfterTwoConsecutivePasses(): Unit =
    val empty = Game(TestSize)
    val firstMove = empty.makeMove(Pass(Color.Black))
    assertThrowsGameOver({firstMove.makeMove(Pass(Color.White))})

  @Test def testPlayListOfMoves(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves.dropRight(1))
    for move <- CaptureMoves.dropRight(1) do
      Assert.assertEquals(
        move.toString+"\n"+game.toString,
        move.color, game.at(move.position)
      )

  @Test def testCaptureStone(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    Assert.assertEquals(
      "\n"+game.toString,
      Color.Empty, game.at(Position(2, 2, 1))
    )

  @Test def testCaptureStoneDoesNotRemoveOthers(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val presentStones = CaptureMoves.filterNot(move => move == Move(2, 2, 1, Color.White))
    for move <- presentStones do
      Assert.assertEquals(
        move.toString+"\n"+game.toString,
        move.color, game.at(move.position)
      )

  @Test def testDoNotCaptureStoneWithNeighbors(): Unit =
    val moves =
      Move(1, 1, 1, Color.Black) :: Move(2, 1, 1, Color.White) ::
      Move(3, 1, 1, Color.Black) :: Move(2, 1, 2, Color.White) ::
      Move(2, 2, 1, Color.Black) :: Nil
    val game = playListOfMoves(TestSize, moves)
    checkStonesOnBoard(game, moves)

  @Test def testTwoNonConsecutivePasses(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Color.Black), Pass(Color.White),
      Move(3, 1, 1, Color.Black), Pass(Color.White)
    )
    val game = playListOfMoves(TestSize, moves)

  @Test def testConnectedStoneOneStone(): Unit =
    val moves = List(Move(1, 1, 1, Color.Black))
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(moves, game.connectedStones(moves(0)))

  @Test def testConnectedStoneOneStoneLeavesBoardUnchanged(): Unit =
    val moves = List(Move(1, 1, 1, Color.Black))
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(Color.Black, game.at(Position(1, 1, 1)))
    for
      x <- 1 to 3
      y <- 1 to 3
      z <- 1 to 3
      if (x, y, z) != (1, 1, 1)
    do
      Assert.assertEquals(Color.Empty, game.at(Position(x, y, z)))

  @Test def testConnectedStoneTwoUnconnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Color.Black), Pass(Color.White), Move(2, 2, 1, Color.Black)
    )
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(1, game.connectedStones(Move(1, 1, 1, Color.Black)).length)
    Assert.assertEquals(1, game.connectedStones(Move(2, 2, 1, Color.Black)).length)


  @Test def testConnectedStoneTwoConnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Color.Black), Pass(Color.White), Move(2, 1, 1, Color.Black)
    )
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(2, game.connectedStones(Move(1, 1, 1, Color.Black)).length)
    Assert.assertEquals(2, game.connectedStones(Move(2, 1, 1, Color.Black)).length)

  @Test def testConnectedStoneMinimalEye(): Unit =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Color.Black), Pass(Color.White), Move(1, 2, 1, Color.Black), Pass(Color.White),
      Move(2, 1, 2, Color.Black), Pass(Color.White), Move(1, 2, 2, Color.Black), Pass(Color.White),
      Move(1, 1, 2, Color.Black), Pass(Color.White)
    )
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(5, game.connectedStones(Move(2, 1, 1, Color.Black)).length)
    Assert.assertEquals(5, game.connectedStones(Move(1, 2, 1, Color.Black)).length)
    Assert.assertEquals(5, game.connectedStones(Move(2, 1, 2, Color.Black)).length)
    Assert.assertEquals(5, game.connectedStones(Move(1, 2, 2, Color.Black)).length)
    Assert.assertEquals(5, game.connectedStones(Move(1, 1, 2, Color.Black)).length)

  @Test def testCaptureStoneWithNeighbors(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Color.Black), Move(2, 1, 1, Color.White),
      Move(3, 1, 1, Color.Black), Move(2, 1, 2, Color.White),
      Move(2, 2, 1, Color.Black), Pass(Color.White),
      Move(1, 1, 2, Color.Black), Pass(Color.White),
      Move(3, 1, 2, Color.Black), Pass(Color.White),
      Move(2, 2, 2, Color.Black), Pass(Color.White)
    )
    var game = playListOfMoves(TestSize, moves)
    checkStonesOnBoard(game, moves)
    game = game.makeMove(Move(2, 1, 3, Color.Black))
    Assert.assertEquals("\n"+game.toString, Color.Empty, game.at(Position(2, 1, 1)))
    Assert.assertEquals("\n"+game.toString, Color.Empty, game.at(Position(2, 1, 2)))

  @Test def testCaptureMinimalEye(): Unit =
    val game = buildAndCaptureEye()
    Assert.assertEquals(Color.Empty, game.at(Position(2, 1, 1)))
    Assert.assertEquals(Color.Empty, game.at(Position(1, 2, 1)))
    Assert.assertEquals(Color.Empty, game.at(Position(2, 1, 2)))
    Assert.assertEquals(Color.Empty, game.at(Position(1, 2, 2)))
    Assert.assertEquals(Color.Empty, game.at(Position(1, 1, 2)))

  def buildEye(): Game =
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
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(5, game.connectedStones(Move(2, 1, 1, Color.Black)).length)
    Assert.assertEquals(5, game.connectedStones(Move(1, 2, 1, Color.Black)).length)
    Assert.assertEquals(5, game.connectedStones(Move(2, 1, 2, Color.Black)).length)
    Assert.assertEquals(5, game.connectedStones(Move(1, 2, 2, Color.Black)).length)
    Assert.assertEquals(5, game.connectedStones(Move(1, 1, 2, Color.Black)).length)
    return game

  def buildAndCaptureEye(): Game =
    val game = buildEye()
    return game.makeMove(Move(1, 1, 1, Color.White))


  @Test def testCapturedStonesAreListed(): Unit =
    val game = buildAndCaptureEye()
    Assert.assertEquals(5, game.captures.length)

  @Test def testCapturedStonesAreCounted(): Unit =
    val game = buildAndCaptureEye()
    Assert.assertEquals(5, game.captures(Color.Black))
    Assert.assertEquals(0, game.captures(Color.White))

  @Ignore("To do")
  @Test def testCapturingStoneWithSettingIntoEyeIsNotSuicide(): Unit =
    val game = buildEye()

  @Ignore("To do")
  @Test def testCapturingEyeIsNotSuicide(): Unit =
    buildAndCaptureEye()

  @Ignore("To do")
  @Test def testSettingStoneIntoNotEncircledEyeIsSuicide(): Unit =
    val game = buildEye()
