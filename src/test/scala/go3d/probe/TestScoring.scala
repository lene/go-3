package go3d.probe

import go3d.*
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestScoring:

  @Test def testAGameWithoutCapturesIsScoredTwiceTheSame(): Unit =
    Assertions.assertEquals(
      BlackWinsResult.blackSide.area,
      BlackWinsResult.blackSide.areaWithCaptures
    )
    Assertions.assertEquals(Black, BlackWinsResult.winnerByArea(0.5))
    Assertions.assertEquals(Black, BlackWinsResult.winnerByAreaWithCaptures(0.5))
    Assertions.assertFalse(BlackWinsResult.flips(0.5))

  @Test def testCapturesCanDecideTheWinner(): Unit =
    Assertions.assertEquals(Black, CaptureFlipsResult.winnerByArea(0.5))
    Assertions.assertEquals(White, CaptureFlipsResult.winnerByAreaWithCaptures(0.5))
    Assertions.assertTrue(CaptureFlipsResult.flips(0.5))

  @Test def testAFlipDependsOnTheKomi(): Unit =
    Assertions.assertFalse(CaptureFlipsResult.flips(-5.5))
    Assertions.assertFalse(CaptureFlipsResult.flips(40.5))

  @Test def testDerivedValues(): Unit =
    Assertions.assertEquals(15, BlackWinsResult.areaMargin)
    Assertions.assertEquals(4, CaptureFlipsResult.captures)
    Assertions.assertTrue(BlackWinsResult.hasTerritory)
    Assertions.assertFalse(
      BlackWinsResult.copy(
        blackSide = SideScore(60, 60, 0),
        whiteSide = SideScore(40, 40, 0)
      ).hasTerritory
    )
    Assertions.assertTrue(BlackWinsResult.isSelfPlay)
    Assertions.assertFalse(BlackWinsResult.copy(white = "onStarPoints").isSelfPlay)

  @Test def testResultOfAGameThatEndedWithPasses(): Unit =
    val game = playListOfMoves(3, BlackLeadsByOneMoves)
    val result = GameResult.of(Task(3, "random", "random", 7), game, None)
    Assertions.assertEquals(Ending.Passes, result.ending)
    Assertions.assertEquals(BlackLeadsByOneMoves.length, result.moves)
    Assertions.assertEquals(SideScore(stones = 2, area = 2, captures = 0), result.blackSide)
    Assertions.assertEquals(SideScore(stones = 1, area = 1, captures = 0), result.whiteSide)
    Assertions.assertEquals(7, result.game)

  @Test def testResultOfAGameThatReachedTheMoveLimit(): Unit =
    val moves: Array[Move | Pass] =
      Array.tabulate[Move | Pass](27)(i => if i % 2 == 0 then Move(1, 1, 1, Black) else Pass(White))
    val game = Game(3, Goban.start(3).success.value, moves, Map(), Komi.Default)
    Assertions.assertTrue(game.isOver)
    Assertions.assertEquals(Ending.MoveLimit, Ending.of(game))

  @Test def testEndingsAreNamedInTheCsv(): Unit =
    Assertions.assertEquals(Some(Ending.Passes), Ending.fromText("passes"))
    Assertions.assertEquals(Some(Ending.MoveLimit), Ending.fromText("move-limit"))
    Assertions.assertEquals(None, Ending.fromText("resigned"))

  @Test def testTheWinnerOfAResultIsTheWinnerOfTheGame(): Unit =
    for moves <- List(BlackLeadsByOneMoves, BlackLeadsBySixMoves) do
      val game = playListOfMoves(3, moves)
      val result = GameResult.of(Task(3, "random", "random", 0), game, None)
      Assertions.assertEquals(game.winner, Some(result.winnerByArea(game.komi)))

  @Test def testCapturesAreCountedForTheCapturingPlayer(): Unit =
    val captureMove = Move(3, 1, 1, White)
    val game = playListOfMoves(5, TwoDisjointStonesCaptureSetup :+ captureMove)
    val result = GameResult.of(Task(5, "random", "random", 0), game, None)
    Assertions.assertEquals(2, result.whiteSide.captures)
    Assertions.assertEquals(0, result.blackSide.captures)
    Assertions.assertEquals(game.score(White), result.whiteSide.area)
    Assertions.assertEquals(game.score(White) + 2, result.whiteSide.areaWithCaptures)
    Assertions.assertEquals(game.score(Black), result.blackSide.area)
