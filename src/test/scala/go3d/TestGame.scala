package go3d

import org.junit.jupiter.api.{Assertions, Test}

class TestGame:

  @Test def testGameCtorBasic(): Unit =
    val game = newGame(TestSize)
    Assertions.assertEquals(TestSize, game.size)

  @Test def testEmptyBoardToStringEmptyPlaces(): Unit =
    val game = newGame(TestSize)
    Assertions.assertEquals(Math.pow(TestSize, 3).toInt+7, game.toString.count(_ == ' '))

  @Test def testEmptyBoardToStringSentinels(): Unit =
    val game = newGame(TestSize)
    Assertions.assertEquals(
      2*(TestSize+2)*TestSize + 2*TestSize*TestSize,
      game.toString.count(_ == '·')
    )

  @Test def testEmptyBoardToStringNewlines(): Unit =
    val game = newGame(TestSize)
    Assertions.assertTrue(TestSize+2 <= game.toString.count(_ == '\n'))

  @Test def testEmptyBoardAt(): Unit =
    val empty = newGame(TestSize)
    for p <- empty.goban.allPositions do
      Assertions.assertEquals(Empty, empty.at(p))

  @Test def testSetStone(): Unit =
    val board = newGame(TestSize).makeMove(Move(2, 2, 2, Black))
    Assertions.assertEquals(board.at(Position(2, 2, 2)), Black, "\n"+board.toString)

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
    Assertions.assertEquals(secondMove.at(Position(2, 2, 2)), Black, "\n"+secondMove.toString)
    Assertions.assertEquals(secondMove.at(Position(2, 2, 1)), White, "\n"+secondMove.toString)

  @Test def testSetTwoSubsequentStonesOfSameColorFails(): Unit =
    val firstMove = newGame(TestSize).makeMove(Move(2, 2, 2, Black))
    assertThrows[WrongTurn]({firstMove.makeMove(Move(2, 2, 1, Black))})

  @Test def testSetAndPassSucceeds(): Unit =
    val firstMove = newGame(TestSize).makeMove(Move(2, 2, 2, Black))
    val secondMove = firstMove.makeMove(Pass(White))
    Assertions.assertEquals(secondMove.at(Position(2, 2, 2)), Black, "\n"+secondMove.toString)
    Assertions.assertEquals(secondMove.at(Position(2, 2, 1)), Empty, "\n"+secondMove.toString)

  @Test def testGameOverAfterTwoConsecutivePasses(): Unit =
    val firstMove = newGame(TestSize).makeMove(Pass(Black))
    Assertions.assertTrue(firstMove.makeMove(Pass(White)).isOver)

  @Test def testPlayListOfMoves(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves.dropRight(1))
    for move <- CaptureMoves.dropRight(1) do
      Assertions.assertEquals(
        move.color, game.at(move.position), move.toString+"\n"+game.toString
      )

  @Test def testCaptureStone(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    Assertions.assertEquals(
      Empty, game.at(Position(2, 2, 1)), "\n"+game.toString
    )

  @Test def testCaptureStoneDoesNotRemoveOthers(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val presentStones = CaptureMoves.filterNot(move => move == Move(2, 2, 1, White))
    for move <- presentStones do
      Assertions.assertEquals(
        move.color, game.at(move.position), move.toString+"\n"+game.toString
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
    Assertions.assertEquals(moves, game.connectedStones(moves.head))

  @Test def testConnectedStoneOneStoneLeavesBoardUnchanged(): Unit =
    val moves = List(Move(1, 1, 1, Black))
    val game = playListOfMoves(TestSize, moves)
    Assertions.assertEquals(Black, game.at(Position(1, 1, 1)))
    for p <- game.goban.allPositions if p != Position(1, 1, 1)
    do
      Assertions.assertEquals(Empty, game.at(p))

  @Test def testConnectedStoneTwoUnconnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Black), Pass(White), Move(2, 2, 1, Black)
    )
    val game = playListOfMoves(TestSize, moves)
    Assertions.assertEquals(1, game.connectedStones(Move(1, 1, 1, Black)).size)
    Assertions.assertEquals(1, game.connectedStones(Move(2, 2, 1, Black)).size)

  @Test def testConnectedStoneTwoConnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Black), Pass(White), Move(2, 1, 1, Black)
    )
    val game = playListOfMoves(TestSize, moves)
    Assertions.assertEquals(2, game.connectedStones(Move(1, 1, 1, Black)).size)
    Assertions.assertEquals(2, game.connectedStones(Move(2, 1, 1, Black)).size)

  @Test def testConnectedStoneMinimalEye(): Unit =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Black), Pass(White), Move(1, 2, 1, Black), Pass(White),
      Move(2, 1, 2, Black), Pass(White), Move(1, 2, 2, Black), Pass(White),
      Move(1, 1, 2, Black), Pass(White)
    )
    val game = playListOfMoves(TestSize, moves)
    Assertions.assertEquals(5, game.connectedStones(Move(2, 1, 1, Black)).size)
    Assertions.assertEquals(5, game.connectedStones(Move(1, 2, 1, Black)).size)
    Assertions.assertEquals(5, game.connectedStones(Move(2, 1, 2, Black)).size)
    Assertions.assertEquals(5, game.connectedStones(Move(1, 2, 2, Black)).size)
    Assertions.assertEquals(5, game.connectedStones(Move(1, 1, 2, Black)).size)

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
    Assertions.assertEquals(Empty, game.at(Position(2, 1, 1)), "\n"+game.toString)
    Assertions.assertEquals(Empty, game.at(Position(2, 1, 2)), "\n"+game.toString)

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
    Assertions.assertTrue(game.captures.isEmpty)
    game = game.makeMove(Move(3, 1, 1, White))
    Assertions.assertEquals(2, game.captures(White))

  @Test def testCaptureMinimalEye(): Unit =
    val game = buildAndCaptureEye()
    Assertions.assertEquals(Empty, game.at(Position(2, 1, 1)))
    Assertions.assertEquals(Empty, game.at(Position(1, 2, 1)))
    Assertions.assertEquals(Empty, game.at(Position(2, 1, 2)))
    Assertions.assertEquals(Empty, game.at(Position(1, 2, 2)))
    Assertions.assertEquals(Empty, game.at(Position(1, 1, 2)))

  def buildEye(): Game =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Black), Pass(White), Move(1, 2, 1, Black), Pass(White),
      Move(2, 1, 2, Black), Pass(White), Move(1, 2, 2, Black), Pass(White),
      Move(1, 1, 2, Black),
    )
    playListOfMoves(TestSize, moves)

  final val BLACK_AREA_SIZE = 5

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
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(2, 1, 1, Black)).size)
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(1, 2, 1, Black)).size)
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(2, 1, 2, Black)).size)
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(1, 2, 2, Black)).size)
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(1, 1, 2, Black)).size)
    nextGame

  def buildAndCaptureEye(): Game =
    val game = encircleEye(buildEye())
    game.makeMove(Move(1, 1, 1, White))

  @Test def testCapturedStonesAreListed(): Unit =
    val game = buildAndCaptureEye()
    Assertions.assertEquals(BLACK_AREA_SIZE, game.captures(White))

  @Test def testCapturedStonesAreCounted(): Unit =
    val game = buildAndCaptureEye()
    Assertions.assertEquals(BLACK_AREA_SIZE, game.captures(White))
    Assertions.assertEquals(0, game.captures(Black))

  @Test def testCapturingStoneWithSettingIntoEyeIsNotSuicide(): Unit =
    val goban = fromStrings(eyeSituation)
    val game = fromGoban(goban)
    Assertions.assertTrue(game.hasLiberties(Move(2, 2, 3, Black)))
    Assertions.assertEquals(Empty, game.at(2, 2, 2))
    for stone <- game.goban.neighbors(Position(2, 2, 2)) do
      Assertions.assertEquals(Black, game.at(stone))
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
    Assertions.assertEquals(Empty, game.at(2, 2, 3))
    Assertions.assertEquals(1, game.captures(White))
    Assertions.assertEquals(Move(2, 2, 3, Black), game.lastCapture(0))
    assertThrows[Ko]({game.checkValid(Move(2, 2, 3, Black))})

  @Test def testPossibleMovesForBlackEmptyBoard(): Unit =
    val empty = newGame(TestSize)
    Assertions.assertEquals(TestSize*TestSize*TestSize, empty.possibleMoves(Black).length)

  @Test def testPossibleMovesForWhiteEmptyBoard(): Unit =
    val empty = newGame(TestSize)
    Assertions.assertTrue(empty.possibleMoves(White).isEmpty)

  @Test def testPossibleMovesAfterOneMove(): Unit =
    val board = newGame(TestSize).makeMove(Move(1, 1, 1, Black))
    Assertions.assertEquals(TestSize*TestSize*TestSize-1, board.possibleMoves(White).length)
    Assertions.assertEquals(0, board.possibleMoves(Black).length)

  @Test def testPossibleMovesWithSuicide(): Unit =
    val game = buildEye()
    Assertions.assertEquals(TestSize*TestSize*TestSize-6, game.possibleMoves(White).length)
    Assertions.assertEquals(0, game.possibleMoves(Black).length)

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
    Assertions.assertEquals(5*5*5-moves.length, game.possibleMoves(Black).length)
    Assertions.assertFalse(game.possibleMoves(Black).contains(Move(2, 2, 3, Black)))
    Assertions.assertEquals(5*5*5-moves.length, game.possibleMoves(Black).length)
    Assertions.assertFalse(game.possibleMoves(Black).contains(Move(2, 2, 3, Black)))

  @Test def testScoring(): Unit =
    val finalSituation = Map(
      1 -> """@@@|
             |@@@|
             |@@@|""",
      3 -> """OOO|
             |OOO|
             |OOO|"""
    )
    val game = fromGoban(fromStrings(finalSituation))
    Assertions.assertEquals(9, game.score(Black))
    Assertions.assertEquals(9, game.score(White))

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
    val game = fromGoban(fromStrings(finalSituation))
    Assertions.assertEquals(13, game.score(Black))
    Assertions.assertEquals(13, game.score(White))

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
    val game = fromGoban(fromStrings(finalSituation))
    Assertions.assertEquals(13, game.score(Black))
    Assertions.assertEquals(13, game.score(White))

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
    val game = fromGoban(fromStrings(finalSituation))
    Assertions.assertEquals(13, game.score(Black))
    Assertions.assertEquals(13, game.score(White))

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
    val game = fromGoban(fromStrings(finalSituation))
    Assertions.assertEquals(9, game.score(Black))
    Assertions.assertEquals(13, game.score(White))

  @Test def testScoringOneStoneControlsAll(): Unit =
    val finalSituation = Map(
      2 -> """   |
             | @ |
             |   |"""
    )
    val game = fromGoban(fromStrings(finalSituation))
    Assertions.assertEquals(27, game.score(Black))
    Assertions.assertEquals(0, game.score(White))

  @Test def testScoringTwoStonesControlNothing(): Unit =
    val finalSituation = Map(
      2 -> """ O |
             | @ |
             |   |"""
    )
    val game = fromGoban(fromStrings(finalSituation))
    Assertions.assertEquals(1, game.score(Black))
    Assertions.assertEquals(1, game.score(White))

  @Test def testScoringCountsCaptures(): Unit =
    val game = buildAndCaptureEye()
    Assertions.assertEquals(0, game.goban.allPositions.count(game.at(_) == Black))
    Assertions.assertEquals(0, game.score(Black))
    Assertions.assertTrue(game.goban.allPositions.count(game.at(_) == White) > 0)
    Assertions.assertEquals(BLACK_AREA_SIZE, game.captures(White))
    val expectedScore = game.size * game.size * game.size + BLACK_AREA_SIZE
    Assertions.assertEquals(expectedScore, game.score(White))

  @Test def testToStringContainsCaptures(): Unit =
    val game = buildAndCaptureEye()
    Assertions.assertTrue(game.toString.contains(Black.toString * BLACK_AREA_SIZE))

  @Test def testIsOverNewGame(): Unit =
    val game = newGame(TestSize)
    Assertions.assertFalse(game.isOver)

  @Test def testIsOverAfterSinglePass(): Unit =
    val game = newGame(TestSize).makeMove(Pass(Black))
    Assertions.assertFalse(game.isOver)

  @Test def testIsOverAfterDoublePass(): Unit =
    val game = newGame(TestSize).makeMove(Pass(Black)).makeMove(Pass(White))
    Assertions.assertTrue(game.isOver)

  @Test def testIsTurnEmptyBoard(): Unit =
    val game = newGame(TestSize)
    Assertions.assertTrue(game.isTurn(Black))
    Assertions.assertFalse(game.isTurn(White))

  @Test def testIsTurnAfterBlackSet(): Unit =
    val game = newGame(TestSize).makeMove(Pass(Black))
    Assertions.assertFalse(game.isTurn(Black))
    Assertions.assertTrue(game.isTurn(White))

  @Test def testIsTurnAfterWhiteSet(): Unit =
    val game = newGame(TestSize).makeMove(Pass(Black)).makeMove(Pass(White))
    Assertions.assertTrue(game.isTurn(Black))
    Assertions.assertFalse(game.isTurn(White))

