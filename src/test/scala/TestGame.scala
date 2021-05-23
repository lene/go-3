package go3d.testing

import go3d._
import org.junit.{Assert, Ignore, Test}

class TestGame:

  @Test def testGameCtorBasic(): Unit =
    val game = newGame(TestSize)
    Assert.assertEquals(TestSize, game.size)

  @Test def testEmptyBoardToStringEmptyPlaces(): Unit =
    val game = newGame(TestSize)
    Assert.assertEquals(Math.pow(TestSize, 3).toInt+2, game.toString.count(_ == ' '))

  @Test def testEmptyBoardToStringSentinels(): Unit =
    val game = newGame(TestSize)
    Assert.assertEquals(
      2*(TestSize+2)*TestSize + 2*TestSize*TestSize,
      game.toString.count(_ == '·')
    )

  @Test def testEmptyBoardToStringNewlines(): Unit =
    val game = newGame(TestSize)
    Assert.assertEquals(TestSize+2, game.toString.count(_ == '\n'))

  @Test def testEmptyBoardAt(): Unit =
    val empty = newGame(TestSize)
    for p <- empty.goban.allPositions do
      Assert.assertEquals(Color.Empty, empty.at(p))

  @Test def testSetStone(): Unit =
    val board = newGame(TestSize).makeMove(Move(2, 2, 2, Color.Black))
    Assert.assertEquals("\n"+board.toString, board.at(Position(2, 2, 2)), Color.Black)

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val board = newGame(TestSize).makeMove(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({board.makeMove(Move(2, 2, 2, Color.White))})

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = newGame(TestSize)
    assertThrowsIllegalMove({empty.makeMove(Move(TestSize+1, 2, 2, Color.White))})
    assertThrowsIllegalMove({empty.makeMove(Move(2, TestSize+1, 2, Color.White))})
    assertThrowsIllegalMove({empty.makeMove(Move(2, 2, TestSize+1, Color.White))})

  @Test def testSetTwoSubsequentStonesOfDifferentColorSucceeds(): Unit =
    val firstMove = newGame(TestSize).makeMove(Move(2, 2, 2, Color.Black))
    val secondMove = firstMove.makeMove(Move(2, 2, 1, Color.White))
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 2)), Color.Black)
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 1)), Color.White)

  @Test def testSetTwoSubsequentStonesOfSameColorFails(): Unit =
    val firstMove = newGame(TestSize).makeMove(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({firstMove.makeMove(Move(2, 2, 1, Color.Black))})

  @Test def testSetAndPassSucceeds(): Unit =
    val firstMove = newGame(TestSize).makeMove(Move(2, 2, 2, Color.Black))
    val secondMove = firstMove.makeMove(Pass(Color.White))
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 2)), Color.Black)
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 1)), Color.Empty)

  @Test def testGameOverAfterTwoConsecutivePasses(): Unit =
    val firstMove = newGame(TestSize).makeMove(Pass(Color.Black))
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
    for p <- game.goban.allPositions if p != Position(1, 1, 1)
    do
      Assert.assertEquals(Color.Empty, game.at(p))

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

  @Test def testCaptureTwoDisjointStonesWithOneMove(): Unit =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Color.Black), Move(1, 1, 1, Color.White),
      Move(4, 1, 1, Color.Black), Move(5, 1, 1, Color.White),
      Pass(Color.Black), Move(2, 2, 1, Color.White),
      Pass(Color.Black), Move(4, 2, 1, Color.White),
      Pass(Color.Black), Move(2, 1, 2, Color.White),
      Pass(Color.Black), Move(4, 1, 2, Color.White),
      Pass(Color.Black)
    )
    var game = playListOfMoves(5, moves)
    Assert.assertTrue(game.captures.isEmpty)
    game = game.makeMove(Move(3, 1, 1, Color.White))
    Assert.assertEquals(2, game.captures(Color.Black))

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
    )
    val game = playListOfMoves(TestSize, moves)
    return game

  def encircleEye(game: Game): Game =
    val moves = List[Move | Pass](
      Move(1, 3, 1, Color.White), Pass(Color.Black), Move(2, 2, 1, Color.White), Pass(Color.Black),
      Move(3, 1, 1, Color.White), Pass(Color.Black), Move(1, 3, 2, Color.White), Pass(Color.Black),
      Move(2, 2, 2, Color.White), Pass(Color.Black), Move(3, 1, 2, Color.White), Pass(Color.Black),
      Move(1, 1, 3, Color.White), Pass(Color.Black), Move(2, 1, 3, Color.White), Pass(Color.Black),
      Move(1, 2, 3, Color.White), Pass(Color.Black)
    )
    var nextGame = game
    for move <- moves do
      nextGame = nextGame.makeMove(move)
    Assert.assertEquals(5, nextGame.connectedStones(Move(2, 1, 1, Color.Black)).length)
    Assert.assertEquals(5, nextGame.connectedStones(Move(1, 2, 1, Color.Black)).length)
    Assert.assertEquals(5, nextGame.connectedStones(Move(2, 1, 2, Color.Black)).length)
    Assert.assertEquals(5, nextGame.connectedStones(Move(1, 2, 2, Color.Black)).length)
    Assert.assertEquals(5, nextGame.connectedStones(Move(1, 1, 2, Color.Black)).length)
    return nextGame

  def buildAndCaptureEye(): Game =
    val game = encircleEye(buildEye())
    return game.makeMove(Move(1, 1, 1, Color.White))

  @Test def testCapturedStonesAreListed(): Unit =
    val game = buildAndCaptureEye()
    Assert.assertEquals(5, game.captures(Color.Black))

  @Test def testCapturedStonesAreCounted(): Unit =
    val game = buildAndCaptureEye()
    Assert.assertEquals(5, game.captures(Color.Black))
    Assert.assertEquals(0, game.captures(Color.White))

  @Test def testCapturingStoneWithSettingIntoEyeIsNotSuicide(): Unit =
    val moves = List[Move | Pass](
      Move(2, 2, 3, Color.Black), Move(2, 2, 4, Color.White),
      Move(2, 3, 2, Color.Black), Move(2, 3, 3, Color.White),
      Move(2, 1, 2, Color.Black), Move(2, 1, 3, Color.White),
      Move(3, 2, 2, Color.Black), Move(3, 2, 3, Color.White),
      Move(1, 2, 2, Color.Black), Move(1, 2, 3, Color.White),
      Move(2, 2, 1, Color.Black)
    )
    val game = playListOfMoves(5, moves)
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 3, Color.Black)))
    Assert.assertEquals(Color.Empty, game.at(2, 2, 2))
    for stone <- game.goban.neighbors(Position(2, 2, 2)) do
      Assert.assertEquals(Color.Black, game.at(stone))
    game.checkValid(Move(2, 2, 2, Color.White))

  @Test def testCapturingEyeIsNotSuicide(): Unit =
    val game = encircleEye(buildEye())
    game.checkValid(Move(1, 1, 1, Color.White))

  @Test def testSettingStoneIntoNotEncircledEyeIsSuicide(): Unit =
    val game = buildEye()
    assertThrowsIllegalMove({game.checkValid(Move(1, 1, 1, Color.White))})

  @Test def testDetectKo(): Unit =
    val moves = List[Move | Pass](
      Move(2, 2, 3, Color.Black), Move(2, 2, 4, Color.White),
      Move(2, 3, 2, Color.Black), Move(2, 3, 3, Color.White),
      Move(2, 1, 2, Color.Black), Move(2, 1, 3, Color.White),
      Move(3, 2, 2, Color.Black), Move(3, 2, 3, Color.White),
      Move(1, 2, 2, Color.Black), Move(1, 2, 3, Color.White),
      Move(2, 2, 1, Color.Black), Move(2, 2, 2, Color.White)
    )
    val game = playListOfMoves(5, moves)
    Assert.assertEquals(Color.Empty, game.at(2, 2, 3))
    Assert.assertEquals(1, game.captures(Color.Black))
    Assert.assertEquals(Move(2, 2, 3, Color.Black), game.lastCapture(0))
    assertThrowsIllegalMove({game.checkValid(Move(2, 2, 3, Color.Black))})

  @Test def testPossibleMovesEmptyBoard(): Unit =
    val empty = newGame(TestSize)
    Assert.assertEquals(TestSize*TestSize*TestSize, empty.possibleMoves(Color.Black).length)

  @Test def testPossibleMovesAfterOneMove(): Unit =
    val board = newGame(TestSize).makeMove(Move(1, 1, 1, Color.Black))
    Assert.assertEquals(TestSize*TestSize*TestSize-1, board.possibleMoves(Color.White).length)
    Assert.assertEquals(0, board.possibleMoves(Color.Black).length)

  @Test def testPossibleMovesWithSuicide(): Unit =
    val game = buildEye()
    Assert.assertEquals(TestSize*TestSize*TestSize-6, game.possibleMoves(Color.White).length)
    Assert.assertEquals(0, game.possibleMoves(Color.Black).length)

  @Test def testPossibleMovesWithKo(): Unit =
    val moves = List[Move | Pass](
      Move(2, 2, 3, Color.Black), Move(2, 2, 4, Color.White),
      Move(2, 3, 2, Color.Black), Move(2, 3, 3, Color.White),
      Move(2, 1, 2, Color.Black), Move(2, 1, 3, Color.White),
      Move(3, 2, 2, Color.Black), Move(3, 2, 3, Color.White),
      Move(1, 2, 2, Color.Black), Move(1, 2, 3, Color.White),
      Move(2, 2, 1, Color.Black), Move(2, 2, 2, Color.White)
    )
    val game = playListOfMoves(5, moves)
    Assert.assertEquals(5*5*5-moves.length, game.possibleMoves(Color.Black).length)
    Assert.assertFalse(game.possibleMoves(Color.Black).contains(Move(2, 2, 3, Color.Black)))
