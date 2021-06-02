package go3d.testing

import go3d._
import go3d.{Black, White, Empty}
import org.junit.{Assert, Ignore, Test}
import scala.util.Random

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
    Assert.assertTrue(TestSize+2 <= game.toString.count(_ == '\n'))

  @Test def testEmptyBoardAt(): Unit =
    val empty = newGame(TestSize)
    for p <- empty.goban.allPositions do
      Assert.assertEquals(Empty, empty.at(p))

  @Test def testSetStone(): Unit =
    val board = newGame(TestSize).makeMove(Move(2, 2, 2, Black))
    Assert.assertEquals("\n"+board.toString, board.at(Position(2, 2, 2)), Black)

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val board = newGame(TestSize).makeMove(Move(2, 2, 2, Black))
    assertThrows[PositionOccupied]({board.makeMove(Move(2, 2, 2, White))})

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = newGame(TestSize)
    assertThrows[OutsideBoard]({empty.makeMove(Move(TestSize+1, 2, 2, White))})
    assertThrows[OutsideBoard]({empty.makeMove(Move(2, TestSize+1, 2, White))})
    assertThrows[OutsideBoard]({empty.makeMove(Move(2, 2, TestSize+1, White))})

  @Test def testSetTwoSubsequentStonesOfDifferentColorSucceeds(): Unit =
    val firstMove = newGame(TestSize).makeMove(Move(2, 2, 2, Black))
    val secondMove = firstMove.makeMove(Move(2, 2, 1, White))
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 2)), Black)
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 1)), White)

  @Test def testSetTwoSubsequentStonesOfSameColorFails(): Unit =
    val firstMove = newGame(TestSize).makeMove(Move(2, 2, 2, Black))
    assertThrows[WrongTurn]({firstMove.makeMove(Move(2, 2, 1, Black))})

  @Test def testSetAndPassSucceeds(): Unit =
    val firstMove = newGame(TestSize).makeMove(Move(2, 2, 2, Black))
    val secondMove = firstMove.makeMove(Pass(White))
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 2)), Black)
    Assert.assertEquals("\n"+secondMove.toString, secondMove.at(Position(2, 2, 1)), Empty)

  @Test def testGameOverAfterTwoConsecutivePasses(): Unit =
    val firstMove = newGame(TestSize).makeMove(Pass(Black))
    assertThrows[GameOver]({firstMove.makeMove(Pass(White))})

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
      Empty, game.at(Position(2, 2, 1))
    )

  @Test def testCaptureStoneDoesNotRemoveOthers(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val presentStones = CaptureMoves.filterNot(move => move == Move(2, 2, 1, White))
    for move <- presentStones do
      Assert.assertEquals(
        move.toString+"\n"+game.toString,
        move.color, game.at(move.position)
      )

  @Test def testDoNotCaptureStoneWithNeighbors(): Unit =
    val moves =
      Move(1, 1, 1, Black) :: Move(2, 1, 1, White) ::
      Move(3, 1, 1, Black) :: Move(2, 1, 2, White) ::
      Move(2, 2, 1, Black) :: Nil
    val game = playListOfMoves(TestSize, moves)
    checkStonesOnBoard(game, moves)

  @Test def testTwoNonConsecutivePasses(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Black), Pass(White), Move(3, 1, 1, Black), Pass(White)
    )
    val game = playListOfMoves(TestSize, moves)

  @Test def testConnectedStoneOneStone(): Unit =
    val moves = Set(Move(1, 1, 1, Black))
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(moves, game.connectedStones(moves.head))

  @Test def testConnectedStoneOneStoneLeavesBoardUnchanged(): Unit =
    val moves = List(Move(1, 1, 1, Black))
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(Black, game.at(Position(1, 1, 1)))
    for p <- game.goban.allPositions if p != Position(1, 1, 1)
    do
      Assert.assertEquals(Empty, game.at(p))

  @Test def testConnectedStoneTwoUnconnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Black), Pass(White), Move(2, 2, 1, Black)
    )
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(1, game.connectedStones(Move(1, 1, 1, Black)).size)
    Assert.assertEquals(1, game.connectedStones(Move(2, 2, 1, Black)).size)

  @Test def testConnectedStoneTwoConnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Black), Pass(White), Move(2, 1, 1, Black)
    )
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(2, game.connectedStones(Move(1, 1, 1, Black)).size)
    Assert.assertEquals(2, game.connectedStones(Move(2, 1, 1, Black)).size)

  @Test def testConnectedStoneMinimalEye(): Unit =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Black), Pass(White), Move(1, 2, 1, Black), Pass(White),
      Move(2, 1, 2, Black), Pass(White), Move(1, 2, 2, Black), Pass(White),
      Move(1, 1, 2, Black), Pass(White)
    )
    val game = playListOfMoves(TestSize, moves)
    Assert.assertEquals(5, game.connectedStones(Move(2, 1, 1, Black)).size)
    Assert.assertEquals(5, game.connectedStones(Move(1, 2, 1, Black)).size)
    Assert.assertEquals(5, game.connectedStones(Move(2, 1, 2, Black)).size)
    Assert.assertEquals(5, game.connectedStones(Move(1, 2, 2, Black)).size)
    Assert.assertEquals(5, game.connectedStones(Move(1, 1, 2, Black)).size)

  @Test def testCaptureStoneWithNeighbors(): Unit =
    val captureSituation = Map(
      1 -> """@O@|
             | @ |
             |   |""",
      2 ->"""@O@|
            | @ |
            |   |"""
    )
    var game = fromGoban(fromStrings(captureSituation))
    game = game.makeMove(Move(2, 1, 3, Black))
    Assert.assertEquals("\n"+game.toString, Empty, game.at(Position(2, 1, 1)))
    Assert.assertEquals("\n"+game.toString, Empty, game.at(Position(2, 1, 2)))

  @Test def testCaptureTwoDisjointStonesWithOneMove(): Unit =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Black), Move(1, 1, 1, White),
      Move(4, 1, 1, Black), Move(5, 1, 1, White),
      Pass(Black), Move(2, 2, 1, White),
      Pass(Black), Move(4, 2, 1, White),
      Pass(Black), Move(2, 1, 2, White),
      Pass(Black), Move(4, 1, 2, White),
      Pass(Black)
    )
    var game = playListOfMoves(5, moves)
    Assert.assertTrue(game.captures.isEmpty)
    game = game.makeMove(Move(3, 1, 1, White))
    Assert.assertEquals(2, game.captures(Black))

  @Test def testCaptureMinimalEye(): Unit =
    val game = buildAndCaptureEye()
    Assert.assertEquals(Empty, game.at(Position(2, 1, 1)))
    Assert.assertEquals(Empty, game.at(Position(1, 2, 1)))
    Assert.assertEquals(Empty, game.at(Position(2, 1, 2)))
    Assert.assertEquals(Empty, game.at(Position(1, 2, 2)))
    Assert.assertEquals(Empty, game.at(Position(1, 1, 2)))

  def buildEye(): Game =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Black), Pass(White), Move(1, 2, 1, Black), Pass(White),
      Move(2, 1, 2, Black), Pass(White), Move(1, 2, 2, Black), Pass(White),
      Move(1, 1, 2, Black),
    )
    val game = playListOfMoves(TestSize, moves)
    return game

  def encircleEye(game: Game): Game =
    val moves = List[Move | Pass](
      Move(1, 3, 1, White), Pass(Black), Move(2, 2, 1, White), Pass(Black),
      Move(3, 1, 1, White), Pass(Black), Move(1, 3, 2, White), Pass(Black),
      Move(2, 2, 2, White), Pass(Black), Move(3, 1, 2, White), Pass(Black),
      Move(1, 1, 3, White), Pass(Black), Move(2, 1, 3, White), Pass(Black),
      Move(1, 2, 3, White), Pass(Black)
    )
    var nextGame = game
    for move <- moves do
      nextGame = nextGame.makeMove(move)
    Assert.assertEquals(5, nextGame.connectedStones(Move(2, 1, 1, Black)).size)
    Assert.assertEquals(5, nextGame.connectedStones(Move(1, 2, 1, Black)).size)
    Assert.assertEquals(5, nextGame.connectedStones(Move(2, 1, 2, Black)).size)
    Assert.assertEquals(5, nextGame.connectedStones(Move(1, 2, 2, Black)).size)
    Assert.assertEquals(5, nextGame.connectedStones(Move(1, 1, 2, Black)).size)
    return nextGame

  def buildAndCaptureEye(): Game =
    val game = encircleEye(buildEye())
    return game.makeMove(Move(1, 1, 1, White))

  @Test def testCapturedStonesAreListed(): Unit =
    val game = buildAndCaptureEye()
    Assert.assertEquals(5, game.captures(Black))

  @Test def testCapturedStonesAreCounted(): Unit =
    val game = buildAndCaptureEye()
    Assert.assertEquals(5, game.captures(Black))
    Assert.assertEquals(0, game.captures(White))

  @Test def testCapturingStoneWithSettingIntoEyeIsNotSuicide(): Unit =
    val goban = fromStrings(eyeSituation)
    val game = fromGoban(goban)
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 3, Black)))
    Assert.assertEquals(Empty, game.at(2, 2, 2))
    for stone <- game.goban.neighbors(Position(2, 2, 2)) do
      Assert.assertEquals(Black, game.at(stone))
    game.checkValid(Move(2, 2, 2, White))

  @Test def testCapturingEyeIsNotSuicide(): Unit =
    val game = encircleEye(buildEye())
    game.checkValid(Move(1, 1, 1, White))

  @Test def testSettingStoneIntoNotEncircledEyeIsSuicide(): Unit =
    val game = buildEye()
    assertThrows[Suicide]({game.checkValid(Move(1, 1, 1, White))})

  @Test def testDetectKo(): Unit =
    val goban = fromStrings(eyeSituation)
    val game = fromGoban(goban).makeMove(Move(2, 2, 2, White))
    Assert.assertEquals(Empty, game.at(2, 2, 3))
    Assert.assertEquals(1, game.captures(Black))
    Assert.assertEquals(Move(2, 2, 3, Black), game.lastCapture(0))
    assertThrows[Ko]({game.checkValid(Move(2, 2, 3, Black))})

  @Test def testPossibleMovesEmptyBoard(): Unit =
    val empty = newGame(TestSize)
    Assert.assertEquals(TestSize*TestSize*TestSize, empty.possibleMoves(Black).length)

  @Test def testPossibleMovesAfterOneMove(): Unit =
    val board = newGame(TestSize).makeMove(Move(1, 1, 1, Black))
    Assert.assertEquals(TestSize*TestSize*TestSize-1, board.possibleMoves(White).length)
    Assert.assertEquals(0, board.possibleMoves(Black).length)

  @Test def testPossibleMovesWithSuicide(): Unit =
    val game = buildEye()
    Assert.assertEquals(TestSize*TestSize*TestSize-6, game.possibleMoves(White).length)
    Assert.assertEquals(0, game.possibleMoves(Black).length)

  @Test def testPossibleMovesWithKo(): Unit =
    val moves = List[Move | Pass](
      Move(2, 2, 3, Black), Move(2, 2, 4, White),
      Move(2, 3, 2, Black), Move(2, 3, 3, White),
      Move(2, 1, 2, Black), Move(2, 1, 3, White),
      Move(3, 2, 2, Black), Move(3, 2, 3, White),
      Move(1, 2, 2, Black), Move(1, 2, 3, White),
      Move(2, 2, 1, Black), Move(2, 2, 2, White)
    )
    val game = playListOfMoves(5, moves)
    Assert.assertEquals(5*5*5-moves.length, game.possibleMoves(Black).length)
    Assert.assertFalse(game.possibleMoves(Black).contains(Move(2, 2, 3, Black)))
    Assert.assertEquals(5*5*5-moves.length, game.possibleMoves(Black).length)
    Assert.assertFalse(game.possibleMoves(Black).contains(Move(2, 2, 3, Black)))

  @Test def testScoring(): Unit =
    val finalSituation = Map(
      1 -> """@@@|
             |@@@|
             |@@@|""",
      3 -> """OOO|
             |OOO|
             |OOO|"""
    )
    var game = fromGoban(fromStrings(finalSituation))
    Assert.assertEquals(9, game.score(Black))
    Assert.assertEquals(9, game.score(White))

  @Test def testScoring2(): Unit =
    val finalSituation = Map(
      1 -> """@@@|
             |@@@|
             |@@@|""",
      2 -> """@@@|
             |@ O|
             |OOO|""",
      3 -> """OOO|
             |OOO|
             |OOO|"""
    )
    var game = fromGoban(fromStrings(finalSituation))
    Assert.assertEquals(13, game.score(Black))
    Assert.assertEquals(13, game.score(White))

  @Test def testScoringWithEyes(): Unit =
    val finalSituation = Map(
      1 -> """ @@|
             |@@@|
             |@@@|""",
      2 -> """@@@|
             |@ O|
             |OOO|""",
      3 -> """OOO|
             |OOO|
             |OO |"""
    )
    var game = fromGoban(fromStrings(finalSituation))
    Assert.assertEquals(13, game.score(Black))
    Assert.assertEquals(13, game.score(White))

  @Test def testScoringWithBiggerTerritory(): Unit =
    val finalSituation = Map(
      1 -> """  @|
             | @@|
             |@@@|""",
      2 -> """@@@|
             |@ O|
             |OOO|""",
      3 -> """OOO|
             |OOO|
             |OO |"""
    )
    var game = fromGoban(fromStrings(finalSituation))
    Assert.assertEquals(13, game.score(Black))
    Assert.assertEquals(13, game.score(White))

  @Test def testScoringWithNotControlledTerritory(): Unit =
    val finalSituation = Map(
      1 -> """  @|
             | @@|
             |@@@|""",
      2 -> """ @@|
             |@ O|
             |OOO|""",
      3 -> """OOO|
             |OOO|
             |OO |"""
    )
    var game = fromGoban(fromStrings(finalSituation))
    Assert.assertEquals(9, game.score(Black))
    Assert.assertEquals(13, game.score(White))

  @Test def testScoringOneStoneControlsAll(): Unit =
    val finalSituation = Map(
      2 -> """   |
             | @ |
             |   |"""
    )
    var game = fromGoban(fromStrings(finalSituation))
    Assert.assertEquals(27, game.score(Black))
    Assert.assertEquals(0, game.score(White))

  @Test def testScoringTwoStonesControlNothing(): Unit =
    val finalSituation = Map(
      2 -> """ O |
             | @ |
             |   |"""
    )
    var game = fromGoban(fromStrings(finalSituation))
    Assert.assertEquals(1, game.score(Black))
    Assert.assertEquals(1, game.score(White))
