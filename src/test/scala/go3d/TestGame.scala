package go3d

import go3d.server.service.GamePositions
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestGame:

  @Test def testGameCtorBasic(): Unit =
    val game = Game.start(TestSize).success.value
    Assertions.assertEquals(TestSize, game.size)

  @Test def testEmptyBoardToStringEmptyPlaces(): Unit =
    val game = Game.start(TestSize).success.value
    Assertions.assertEquals(Math.pow(TestSize, 3).toInt + 7, game.toString.count(_ == ' '))

  @Test def testEmptyBoardToStringSentinels(): Unit =
    val game = Game.start(TestSize).success.value
    Assertions.assertEquals(
      2 * (TestSize + 2) * TestSize + 2 * TestSize * TestSize,
      game.toString.count(_ == '·')
    )

  @Test def testEmptyBoardToStringNewlines(): Unit =
    val game = Game.start(TestSize).success.value
    Assertions.assertTrue(TestSize + 2 <= game.toString.count(_ == '\n'))

  @Test def testEmptyBoardAt(): Unit =
    val empty = Game.start(TestSize).success.value
    for p <- empty.goban.allPositions do
      Assertions.assertEquals(Empty, empty.at(p))

  @Test def testSetStone(): Unit =
    val board = Game.start(TestSize).success.value.makeMove(Move(2, 2, 2, Black)).success.value
    Assertions.assertEquals(board.at(Position(2, 2, 2)), Black, "\n" + board.toString)

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val board = Game.start(TestSize).success.value.makeMove(Move(2, 2, 2, Black)).success.value
    Assertions.assertInstanceOf(
      classOf[PositionOccupied],
      board.makeMove(Move(2, 2, 2, White)).failure.exception
    )

  @Test def testSetStoneOutsideBoardFails(): Unit =
    val empty = Game.start(TestSize).success.value
    Assertions.assertInstanceOf(
      classOf[OutsideBoard],
      empty.makeMove(Move(TestSize + 1, 2, 2, White)).failure.exception
    )
    Assertions.assertInstanceOf(
      classOf[OutsideBoard],
      empty.makeMove(Move(2, TestSize + 1, 2, White)).failure.exception
    )
    Assertions.assertInstanceOf(
      classOf[OutsideBoard],
      empty.makeMove(Move(2, 2, TestSize + 1, White)).failure.exception
    )

  @Test def testSetTwoSubsequentStonesOfDifferentColorSucceeds(): Unit =
    val firstMove = Game.start(TestSize).success.value.makeMove(Move(2, 2, 2, Black)).success.value
    val secondMove = firstMove.makeMove(Move(2, 2, 1, White)).success.value
    Assertions.assertEquals(secondMove.at(Position(2, 2, 2)), Black, "\n" + secondMove.toString)
    Assertions.assertEquals(secondMove.at(Position(2, 2, 1)), White, "\n" + secondMove.toString)

  @Test def testSetTwoSubsequentStonesOfSameColorFails(): Unit =
    val firstMove = Game.start(TestSize).success.value.makeMove(Move(2, 2, 2, Black)).success.value
    Assertions.assertInstanceOf(
      classOf[WrongTurn],
      firstMove.makeMove(Move(2, 2, 1, Black)).failure.exception
    )

  @Test def testSetAndPassSucceeds(): Unit =
    val firstMove = Game.start(TestSize).success.value.makeMove(Move(2, 2, 2, Black)).success.value
    val secondMove = firstMove.makeMove(Pass(White)).success.value
    Assertions.assertEquals(secondMove.at(Position(2, 2, 2)), Black, "\n" + secondMove.toString)
    Assertions.assertEquals(secondMove.at(Position(2, 2, 1)), Empty, "\n" + secondMove.toString)

  @Test def testGameOverAfterTwoConsecutivePasses(): Unit =
    val firstMove = Game.start(TestSize).success.value.makeMove(Pass(Black)).success.value
    Assertions.assertTrue(firstMove.makeMove(Pass(White)).success.value.isOver)

  @Test def testPlayListOfMoves(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves.dropRight(1))
    for move <- CaptureMoves.dropRight(1) do
      Assertions.assertEquals(
        move.color,
        game.at(move.position),
        move.toString + "\n" + game.toString
      )

  @Test def testCaptureStone(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    Assertions.assertEquals(
      Empty,
      game.at(Position(2, 2, 1)),
      "\n" + game.toString
    )

  @Test def testCaptureStoneDoesNotRemoveOthers(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    val presentStones = CaptureMoves.filterNot(move => move == Move(2, 2, 1, White))
    for move <- presentStones do
      Assertions.assertEquals(
        move.color,
        game.at(move.position),
        move.toString + "\n" + game.toString
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
      Move(1, 1, 1, Black),
      Pass(White),
      Move(3, 1, 1, Black),
      Pass(White)
    )
    playListOfMoves(TestSize, moves)

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
      Move(1, 1, 1, Black),
      Pass(White),
      Move(2, 2, 1, Black)
    )
    val game = playListOfMoves(TestSize, moves)
    Assertions.assertEquals(1, game.connectedStones(Move(1, 1, 1, Black)).size)
    Assertions.assertEquals(1, game.connectedStones(Move(2, 2, 1, Black)).size)

  @Test def testConnectedStoneTwoConnectedStones(): Unit =
    val moves = List[Move | Pass](
      Move(1, 1, 1, Black),
      Pass(White),
      Move(2, 1, 1, Black)
    )
    val game = playListOfMoves(TestSize, moves)
    Assertions.assertEquals(2, game.connectedStones(Move(1, 1, 1, Black)).size)
    Assertions.assertEquals(2, game.connectedStones(Move(2, 1, 1, Black)).size)

  @Test def testConnectedStoneMinimalEye(): Unit =
    val moves = List[Move | Pass](
      Move(2, 1, 1, Black),
      Pass(White),
      Move(1, 2, 1, Black),
      Pass(White),
      Move(2, 1, 2, Black),
      Pass(White),
      Move(1, 2, 2, Black),
      Pass(White),
      Move(1, 1, 2, Black),
      Pass(White)
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
      2 -> """@O@|
            | @ |
            |   |"""
    )
    var game = fromGoban(fromStrings(captureSituation))
    game = game.makeMove(Move(2, 1, 3, Black)).success.value
    Assertions.assertEquals(Empty, game.at(Position(2, 1, 1)), "\n" + game.toString)
    Assertions.assertEquals(Empty, game.at(Position(2, 1, 2)), "\n" + game.toString)

  @Test def testCaptureTwoDisjointStonesWithOneMove(): Unit =
    var game = playListOfMoves(5, TwoDisjointStonesCaptureSetup)
    Assertions.assertTrue(game.captures.isEmpty)
    game = game.makeMove(Move(3, 1, 1, White)).success.value
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
      Move(2, 1, 1, Black),
      Pass(White),
      Move(1, 2, 1, Black),
      Pass(White),
      Move(2, 1, 2, Black),
      Pass(White),
      Move(1, 2, 2, Black),
      Pass(White),
      Move(1, 1, 2, Black)
    )
    playListOfMoves(TestSize, moves)

  final val BLACK_AREA_SIZE = 5

  def encircleEye(game: Game): Game =
    val whiteStones = List(
      Move(1, 3, 1, White),
      Move(2, 2, 1, White),
      Move(3, 1, 1, White),
      Move(1, 3, 2, White),
      Move(2, 2, 2, White),
      Move(3, 1, 2, White),
      Move(1, 1, 3, White),
      Move(2, 1, 3, White),
      Move(1, 2, 3, White)
    )
    // Black passes after every white stone
    val moves = whiteStones.flatMap(stone => List[Move | Pass](stone, Pass(Black)))
    var nextGame = game
    for move <- moves do
      nextGame = nextGame.makeMove(move).success.value
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(2, 1, 1, Black)).size)
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(1, 2, 1, Black)).size)
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(2, 1, 2, Black)).size)
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(1, 2, 2, Black)).size)
    Assertions.assertEquals(BLACK_AREA_SIZE, nextGame.connectedStones(Move(1, 1, 2, Black)).size)
    nextGame

  def buildAndCaptureEye(): Game =
    val game = encircleEye(buildEye())
    game.makeMove(Move(1, 1, 1, White)).success.value

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
    Assertions.assertTrue(game.groupHasLiberties(Move(2, 2, 3, Black)))
    Assertions.assertEquals(Empty, game.at(2, 2, 2))
    for stone <- game.goban.neighbors(Position(2, 2, 2)) do
      Assertions.assertEquals(Black, game.at(stone))
    game.checkValid(Move(2, 2, 2, White)).success.value

  @Test def testCapturingEyeIsNotSuicide(): Unit =
    val game = encircleEye(buildEye())
    game.checkValid(Move(1, 1, 1, White)).success.value

  /** Black's corner stone at 1 1 1 with its last liberty at 2 1 1, every other neighbour of both
    * points white, and no white stone in atari.
    */
  private val ownLastLibertyMoves = List[Move | Pass](
    Move(1, 1, 1, Black),
    Move(1, 2, 1, White),
    Pass(Black),
    Move(1, 1, 2, White),
    Pass(Black),
    Move(3, 1, 1, White),
    Pass(Black),
    Move(2, 2, 1, White),
    Pass(Black),
    Move(2, 1, 2, White)
  )

  @Test def testFillingTheLastLibertyOfAnOwnGroupIsSuicide(): Unit =
    val game = playListOfMoves(5, ownLastLibertyMoves)
    Assertions.assertInstanceOf(
      classOf[Suicide],
      game.makeMove(Move(2, 1, 1, Black)).failure.exception
    )
    Assertions.assertFalse(game.possibleMoves(Black).contains(Position(2, 1, 1)))

  @Test def testAGameRecordedBeforeTheSuicideRuleReplaysAsItWasPlayed(): Unit =
    val recorded = playListOfMoves(5, ownLastLibertyMoves).replayed(Move(2, 1, 1, Black))
    val replayed = recorded.atMove(recorded.moves.length).success.value
    Assertions.assertEquals(Black, replayed.at(2, 1, 1))
    Assertions.assertEquals(recorded.goban.toString, replayed.goban.toString)

  @Test def testFillingTheLastLibertyOfAnOwnGroupIsNoSuicideIfItCaptures(): Unit =
    // as above, but the white stone at 3 1 1 has its last liberty at 2 1 1 too
    val moves = List[Move | Pass](
      Move(1, 1, 1, Black),
      Move(1, 2, 1, White),
      Move(4, 1, 1, Black),
      Move(1, 1, 2, White),
      Move(3, 2, 1, Black),
      Move(2, 2, 1, White),
      Move(3, 1, 2, Black),
      Move(2, 1, 2, White),
      Pass(Black),
      Move(3, 1, 1, White)
    )
    val after = playListOfMoves(5, moves).makeMove(Move(2, 1, 1, Black)).success.value
    Assertions.assertEquals(Empty, after.at(3, 1, 1))
    Assertions.assertTrue(after.groupHasLiberties(Move(1, 1, 1, Black)))

  @Test def testSettingStoneIntoNotEncircledEyeIsSuicide(): Unit =
    val game = buildEye()
    Assertions.assertInstanceOf(
      classOf[Suicide],
      game.checkValid(Move(1, 1, 1, White)).failure.exception
    )

  @Test def testDetectKo(): Unit =
    val goban = fromStrings(eyeSituation)
    val game = fromGoban(goban).makeMove(Move(2, 2, 2, White)).success.value
    Assertions.assertEquals(Empty, game.at(2, 2, 3))
    Assertions.assertEquals(1, game.captures(White))
    Assertions.assertEquals(Move(2, 2, 3, Black), game.lastCapture(0))
    Assertions.assertInstanceOf(
      classOf[Ko],
      game.checkValid(Move(2, 2, 3, Black)).failure.exception
    )

  /** Twelve moves on opposite faces of a size 5 board, Black on x = 1 and White on x = 5, with
    * White's moves capturing one Black stone each at `capturedAt`: more than four captures, so
    * `captures` is a HashMap.
    */
  private def gameWithSingleCaptures(capturedAt: List[Move]): Game =
    val moves = List.tabulate[Move | Pass](12) { i =>
      val (y, z) = (i / 2 % 5 + 1, 1 + 2 * (i / 10))
      if i % 2 == 0 then Move(1, y, z, Black) else Move(5, y, z, White)
    }
    val played = playListOfMoves(5, moves)
    val captures = capturedAt.zipWithIndex.map((stone, i) => (2 * i + 1) -> Array(stone)).toMap
    Game(5, played.goban, played.moves, captures, played.komi)

  @Test def testAnOldSingleStoneCaptureDoesNotForbidRetakingThePoint(): Unit =
    val game = gameWithSingleCaptures(List(Move(3, 3, 3, Black)))
    game.checkValid(Move(3, 3, 3, Black)).success.value
    Assertions.assertTrue(game.possibleMoves(Black).contains(Move(3, 3, 3, Black).position))

  private val KoPoint = Move(1, 1, 1, White)

  /** Black's last move at 2 1 1 has just captured the White stone in the corner, `KoPoint`. The
    * capturing stone has no other liberty. With `secondInAtari`, the Black stone at 1 2 1 has
    * the corner as its only liberty too, so White retaking the corner captures two stones.
    * `oldCaptures` adds single-stone captures of earlier moves, which make `captures` a HashMap.
    */
  private def cornerKo(secondInAtari: Boolean, oldCaptures: Boolean): Game =
    val moves = List[Move | Pass](
      Move(1, 1, 2, Black),
      KoPoint,
      Move(1, 2, 1, Black),
      Move(3, 1, 1, White),
      Pass(Black),
      Move(2, 2, 1, White),
      Pass(Black),
      Move(2, 1, 2, White),
      Pass(Black),
      Move(1, 2, 2, White),
      Pass(Black),
      if secondInAtari then Move(1, 3, 1, White) else Move(5, 5, 5, White),
      Move(2, 1, 1, Black)
    )
    val goban = setListOfStones(5, moves.filterNot(_ == KoPoint))
    val old =
      if oldCaptures then List(3, 5, 7, 9, 11).map(i => i -> Array(Move(4, 4, i % 5 + 1, Black)))
      else Nil
    Game(5, goban, moves.toArray, (old :+ (12 -> Array(KoPoint))).toMap, Komi.Default)

  @Test def testKoFollowsTheCaptureOfThePreviousMoveWhateverTheMapOrder(): Unit =
    for oldCaptures <- List(false, true) do
      val game = cornerKo(secondInAtari = false, oldCaptures)
      Assertions.assertInstanceOf(classOf[Ko], game.checkValid(KoPoint).failure.exception)
      Assertions.assertFalse(game.possibleMoves(White).contains(KoPoint.position))

  @Test def testARetakeThatCapturesMoreThanTheCapturingStoneIsNotKo(): Unit =
    val game = cornerKo(secondInAtari = true, oldCaptures = true)
    Assertions.assertTrue(game.possibleMoves(White).contains(KoPoint.position))
    val retaken = game.makeMove(KoPoint).success.value
    Assertions.assertEquals(Empty, retaken.at(2, 1, 1))
    Assertions.assertEquals(Empty, retaken.at(1, 2, 1))
    Assertions.assertEquals(2, retaken.lastCapture.length)

  @Test def testPossibleMovesForBlackEmptyBoard(): Unit =
    val empty = Game.start(TestSize).success.value
    Assertions.assertEquals(TestSize * TestSize * TestSize, empty.possibleMoves(Black).length)

  @Test def testPossibleMovesForWhiteEmptyBoard(): Unit =
    val empty = Game.start(TestSize).success.value
    Assertions.assertTrue(empty.possibleMoves(White).isEmpty)

  @Test def testPossibleMovesAfterOneMove(): Unit =
    val board = Game.start(TestSize).success.value.makeMove(Move(1, 1, 1, Black)).success.value
    Assertions.assertEquals(TestSize * TestSize * TestSize - 1, board.possibleMoves(White).length)
    Assertions.assertEquals(0, board.possibleMoves(Black).length)

  @Test def testPossibleMovesWithSuicide(): Unit =
    val game = buildEye()
    Assertions.assertEquals(TestSize * TestSize * TestSize - 6, game.possibleMoves(White).length)
    Assertions.assertEquals(0, game.possibleMoves(Black).length)

  @Test def testPossibleMovesWithKo(): Unit =
    val moves = List[Move | Pass](
      Move(2, 2, 3, Black),
      Move(2, 2, 4, White),
      Move(2, 3, 2, Black),
      Move(2, 3, 3, White),
      Move(2, 1, 2, Black),
      Move(2, 1, 3, White),
      Move(3, 2, 2, Black),
      Move(3, 2, 3, White),
      Move(1, 2, 2, Black),
      Move(1, 2, 3, White),
      Move(2, 2, 1, Black),
      Move(2, 2, 2, White)
    )
    val game = playListOfMoves(5, moves)
    Assertions.assertEquals(5 * 5 * 5 - moves.length, game.possibleMoves(Black).length)
    Assertions.assertFalse(game.possibleMoves(Black).contains(Move(2, 2, 3, Black)))
    Assertions.assertEquals(5 * 5 * 5 - moves.length, game.possibleMoves(Black).length)
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

  @Test def testScoringDoesNotCountCaptures(): Unit =
    val game = buildAndCaptureEye()
    Assertions.assertEquals(0, game.goban.allPositions.count(game.at(_) == Black))
    Assertions.assertEquals(0, game.score(Black))
    Assertions.assertEquals(BLACK_AREA_SIZE, game.captures(White))
    Assertions.assertEquals(0, game.captures(Black))
    val whiteStones = game.goban.allPositions.count(game.at(_) == White)
    val whiteTerritory = game.goban.emptyPositions.size
    Assertions.assertEquals(10, whiteStones)
    Assertions.assertEquals(17, whiteTerritory)
    Assertions.assertEquals(whiteStones + whiteTerritory, game.score(White))
    Assertions.assertEquals(game.size * game.size * game.size, game.score(White))

  @Test def testScoringPositionReachedThroughACapture(): Unit =
    val moves = GamePositions.BeforeKo.zipWithIndex.map { case ((x, y, z), i) =>
      Move(x, y, z, if i % 2 == 0 then Black else White)
    }
    val game = playListOfMoves(5, moves)
    Assertions.assertEquals(1, game.captures(White))
    Assertions.assertEquals(0, game.captures(Black))
    val whiteStones = game.goban.allPositions.count(game.at(_) == White)
    Assertions.assertEquals(6, whiteStones)
    Assertions.assertEquals(5, game.score(Black))
    Assertions.assertEquals(whiteStones + 1, game.score(White))

  @Test def testToStringEndsWithTheScoreMapOfBothColors(): Unit =
    val empty = Game.start(TestSize).success.value
    Assertions.assertEquals("Map(@ -> 0, O -> 0)", empty.toString.linesIterator.toList.last)
    val captured = buildAndCaptureEye()
    Assertions.assertEquals("Map(@ -> 0, O -> 27)", captured.toString.linesIterator.toList.last)

  @Test def testToStringContainsCaptures(): Unit =
    val game = buildAndCaptureEye()
    Assertions.assertTrue(game.toString.contains(Black.toString * BLACK_AREA_SIZE))

  @Test def testIsOverNewGame(): Unit =
    val game = Game.start(TestSize).success.value
    Assertions.assertFalse(game.isOver)

  @Test def testIsOverAfterSinglePass(): Unit =
    val game = Game.start(TestSize).success.value.makeMove(Pass(Black)).success.value
    Assertions.assertFalse(game.isOver)

  @Test def testIsOverAfterDoublePass(): Unit =
    val game = Game.start(TestSize).success.value
      .makeMove(Pass(Black)).success.value
      .makeMove(Pass(White)).success.value
    Assertions.assertTrue(game.isOver)

  @Test def testIsTurnEmptyBoard(): Unit =
    val game = Game.start(TestSize).success.value
    Assertions.assertTrue(game.isTurn(Black))
    Assertions.assertFalse(game.isTurn(White))

  @Test def testIsTurnAfterBlackSet(): Unit =
    val game = Game.start(TestSize).success.value.makeMove(Pass(Black)).success.value
    Assertions.assertFalse(game.isTurn(Black))
    Assertions.assertTrue(game.isTurn(White))

  @Test def testIsTurnAfterWhiteSet(): Unit =
    val game = Game.start(TestSize).success.value
      .makeMove(Pass(Black)).success.value
      .makeMove(Pass(White)).success.value
    Assertions.assertTrue(game.isTurn(Black))
    Assertions.assertFalse(game.isTurn(White))

  @Test def testAtMoveZeroReturnsEmptyBoard(): Unit =
    val game = Game.start(TestSize).success.value.makeMove(Move(2, 2, 2, Black)).success.value
    val result = game.atMove(0).success.value
    Assertions.assertEquals(0, result.moves.length)
    for p <- result.goban.allPositions do
      Assertions.assertEquals(Empty, result.at(p))

  @Test def testAtMoveOneReturnsFirstStone(): Unit =
    val game = Game.start(TestSize).success.value.makeMove(Move(2, 2, 2, Black)).success.value
    val result = game.atMove(1).success.value
    Assertions.assertEquals(1, result.moves.length)
    Assertions.assertEquals(Black, result.at(Position(2, 2, 2)))

  @Test def testAtMoveFullLengthMatchesFinalGame(): Unit =
    val game = Game.start(TestSize).success.value
      .makeMove(Move(2, 2, 2, Black)).success.value
      .makeMove(Move(3, 3, 3, White)).success.value
    val result = game.atMove(game.moves.length).success.value
    Assertions.assertEquals(game.goban, result.goban)

  private val Komi65 = 6.5

  @Test def testGameStartsWithDefaultKomi(): Unit =
    Assertions.assertEquals(Komi.Default, Game.start(TestSize).success.value.komi)

  @Test def testGameStartsWithGivenKomi(): Unit =
    val game = Game.start(TestSize, Komi65).success.value
    Assertions.assertEquals(Komi65, game.komi)
    Assertions.assertEquals(TestSize, game.size)
    Assertions.assertTrue(game.moves.isEmpty)

  @Test def testGameStartsWithNegativeKomi(): Unit =
    Assertions.assertEquals(-0.5, Game.start(TestSize, -0.5).success.value.komi)

  @Test def testGameStartWithIntegerKomiFails(): Unit =
    for komi <- List(0.0, 1.0, 7.0, -7.0) do
      Assertions.assertInstanceOf(classOf[BadKomi], Game.start(TestSize, komi).failure.exception)

  @Test def testGameStartWithUnusableKomiFails(): Unit =
    val unusable =
      List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity, 6.500000000000001)
    for komi <- unusable do
      Assertions.assertInstanceOf(classOf[BadKomi], Game.start(TestSize, komi).failure.exception)

  @Test def testGameStartWithKomiBeyondBoardPointsFails(): Unit =
    Assertions.assertEquals(26.5, Game.start(3, 26.5).success.value.komi)
    Assertions.assertEquals(-26.5, Game.start(3, -26.5).success.value.komi)
    Assertions.assertInstanceOf(classOf[BadKomi], Game.start(3, 27.5).failure.exception)
    Assertions.assertInstanceOf(classOf[BadKomi], Game.start(3, -27.5).failure.exception)

  @Test def testGameStartChecksSizeBeforeKomi(): Unit =
    Assertions.assertInstanceOf(classOf[BadBoardSize], Game.start(4, 7.0).failure.exception)
    Assertions.assertInstanceOf(classOf[BadBoardSize], Game.start(1, 99.5).failure.exception)

  @Test def testKomiSurvivesPass(): Unit =
    val game = Game.start(TestSize, Komi65).success.value.makeMove(Pass(Black)).success.value
    Assertions.assertEquals(Komi65, game.komi)

  @Test def testKomiSurvivesMove(): Unit =
    val game = playListOfMovesWithKomi(TestSize, Komi65, List(Move(2, 2, 2, Black)))
    Assertions.assertEquals(Komi65, game.komi)

  @Test def testKomiSurvivesCapture(): Unit =
    val game = playListOfMovesWithKomi(TestSize, Komi65, CaptureMoves)
    Assertions.assertTrue(game.captures.nonEmpty)
    Assertions.assertEquals(Komi65, game.komi)

  @Test def testKomiSurvivesAtMove(): Unit =
    val game = playListOfMovesWithKomi(TestSize, Komi65, CaptureMoves)
    for count <- 0 to game.moves.length do
      Assertions.assertEquals(Komi65, game.atMove(count).success.value.komi)

  @Test def testAtMoveOfGameWithKomiEqualsGameBuiltFromItsMoves(): Unit =
    val game = playListOfMovesWithKomi(TestSize, Komi65, CaptureMoves)
    Assertions.assertEquals(game, game.atMove(game.moves.length).success.value)

  @Test def testGamesWithDifferentKomiAreNotEqual(): Unit =
    Assertions.assertNotEquals(
      Game.start(TestSize, 0.5).success.value,
      Game.start(TestSize, Komi65).success.value
    )
    Assertions.assertEquals(
      Game.start(TestSize, Komi65).success.value,
      Game.start(TestSize, Komi65).success.value
    )

  @Test def testEqualGamesHaveEqualHashCodes(): Unit =
    val game = playListOfMovesWithKomi(TestSize, Komi65, CaptureMoves)
    val same = playListOfMovesWithKomi(TestSize, Komi65, CaptureMoves)
    Assertions.assertEquals(game, same)
    Assertions.assertEquals(game.hashCode, same.hashCode)
    Assertions.assertEquals(1, Set(game, same).size)

  @Test def testGameIsNotEqualToItsBoard(): Unit =
    val game = playListOfMoves(TestSize, CaptureMoves)
    Assertions.assertNotEquals(game, game.goban)

  @Test def testScoreListsBlackFirst(): Unit =
    val game = playListOfMovesWithKomi(TestSize, Komi65, CaptureMoves)
    Assertions.assertEquals(List(Black, White), game.score.keys.toList)

  @Test def testWhiteNeedsTheKomiToWinATie(): Unit =
    Assertions.assertEquals(White, Game.winner(10, 10, 0.5))
    Assertions.assertEquals(Black, Game.winner(11, 10, 0.5))
    Assertions.assertEquals(White, Game.winner(10, 10, 6.5))
    Assertions.assertEquals(Black, Game.winner(17, 10, 6.5))
    Assertions.assertEquals(White, Game.winner(16, 10, 6.5))

  @Test def testKomiDoesNotChangeScoreOrItsToString(): Unit =
    val withKomi = playListOfMovesWithKomi(TestSize, Komi65, BlackLeadsByOneMoves)
    val withDefault = playListOfMovesWithKomi(TestSize, Komi.Default, BlackLeadsByOneMoves)
    Assertions.assertEquals(withDefault.score, withKomi.score)
    Assertions.assertEquals(withDefault.toString, withKomi.toString)

  @Test def testWinnerIsNoneWhileGameIsNotOver(): Unit =
    val empty = Game.start(TestSize).success.value
    Assertions.assertEquals(None, empty.winner)
    val oneMove = empty.makeMove(Move(2, 2, 2, Black)).success.value
    Assertions.assertEquals(None, oneMove.winner)
    val onePass = oneMove.makeMove(Pass(White)).success.value
    Assertions.assertEquals(None, onePass.winner)

  @Test def testWinnerWithSmallKomiIsBlackWhenBlackLeads(): Unit =
    val game = playListOfMovesWithKomi(TestSize, Komi.Default, BlackLeadsByOneMoves)
    Assertions.assertEquals(2, game.score(Black))
    Assertions.assertEquals(1, game.score(White))
    Assertions.assertEquals(Some(Black), game.winner)

  @Test def testWinnerWithLargeKomiIsWhiteWhenBlackLeadsByOne(): Unit =
    Assertions.assertEquals(
      Some(White),
      playListOfMovesWithKomi(TestSize, Komi65, BlackLeadsByOneMoves).winner
    )

  @Test def testKomiAboveLeadFlipsWinnerAndKomiBelowLeadDoesNot(): Unit =
    val sixAhead = playListOfMovesWithKomi(TestSize, Komi65, BlackLeadsBySixMoves)
    Assertions.assertEquals(6, sixAhead.score(Black) - sixAhead.score(White))
    Assertions.assertEquals(Some(White), sixAhead.winner)
    Assertions.assertEquals(
      Some(Black),
      playListOfMovesWithKomi(TestSize, 5.5, BlackLeadsBySixMoves).winner
    )

  @Test def testDefaultKomiMakesWhiteWinAnEmptyBoard(): Unit =
    val game = playListOfMovesWithKomi(TestSize, Komi.Default, List(Pass(Black), Pass(White)))
    Assertions.assertEquals(0, game.score(Black))
    Assertions.assertEquals(0, game.score(White))
    Assertions.assertEquals(Some(White), game.winner)

  @Test def testNegativeKomiMakesBlackWinEqualScores(): Unit =
    val game = playListOfMovesWithKomi(TestSize, -0.5, List(Pass(Black), Pass(White)))
    Assertions.assertEquals(game.score(Black), game.score(White))
    Assertions.assertEquals(Some(Black), game.winner)

  @Test def testEmptyCaptureArrayCountsNoCaptures(): Unit =
    val over = playListOfMovesWithKomi(TestSize, 6.5, List(Pass(Black), Pass(White)))
    val game = Game(TestSize, over.goban, over.moves, Map(0 -> Array[Move]()), 6.5)
    Assertions.assertEquals(0, game.captures(Black))
    Assertions.assertEquals(0, game.captures(White))
    Assertions.assertEquals((0, 0), (game.score(Black), game.score(White)))
    Assertions.assertEquals(Some(White), game.winner)
