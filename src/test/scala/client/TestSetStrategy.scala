package go3d.testing

import go3d.{Position, newGame, Black, Move, White, Pass}
import go3d.client.{SetStrategy, totalNumLiberties, BotClient}
import org.junit.{Test, Assert}

class TestSetStrategy:

  @Test def testClosestToCenterStrategy(): Unit =
    val strategy = getStrategy(3)
    val check = checkStrategyResults.curried(strategy.closestToCenter)
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))
    check(
      List((1, 1, 1),(1, 2, 2), (2, 1, 2), (2, 2, 2), (3, 3, 3)))(
      List((2, 2, 2))
    )
    check(List((1, 1, 1), (1, 2, 2), (3, 3, 3)))(List((1, 2, 2)))
    check(
      List((1, 1, 1), (1, 2, 2), (2, 1, 2), (3, 3, 3)))(
      List((1, 2, 2), (2, 1, 2))
    )
    check(
      List((1, 1, 1), (1, 1, 2), (2, 1, 1), (3, 3, 3)))(
      List((1, 1, 2), (2, 1, 1))
    )
    val allCornerPoints = List((1, 1, 1), (1, 1, 3), (1, 3, 1), (1, 3, 3), (3, 1, 1), (3, 1, 3), (3, 3, 1), (3, 3, 3))
    check(allCornerPoints)(allCornerPoints)

  @Test def testClosestToStarPointsStrategyCornerStarPointsSmallBoard(): Unit =
    val strategy = getStrategy(3)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(
      List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(
      List((1, 1, 1), (3, 3, 3))
    )
    check(
      List((1, 1, 1), (1, 1, 3), (2, 2, 2), (3, 3, 1), (3, 3, 3)))(
      List((1, 1, 1), (1, 1, 3), (3, 3, 1), (3, 3, 3))
    )

  @Test def testClosestToStarPointsStrategyCornerStarPoints(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(
      List((2, 2, 2), (4, 4, 4), (6, 6, 6)))(
      List((2, 2, 2), (6, 6, 6))
    )
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))

  @Test def testClosestToStarPointsStrategyMidLines(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(List((1, 1, 1), (2, 6, 2), (7, 7, 7)))(List((2, 6, 2)))

  @Test def testClosestToStarPointsStrategyMidFaces(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(List((1, 1, 1), (2, 6, 6), (7, 7, 7)))(List((2, 6, 6)))

  @Test def testClosestToStarPointsStrategyCenter(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(List((1, 1, 1), (4, 4, 4), (7, 7, 7)))(List((4, 4, 4)))

  @Test def testClosestToStarPointsStrategyOffStarPoints(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((2, 2, 1)))

  @Test def testMaximizeLiberties(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    val strategy = SetStrategy(game, Array())
    val check = checkStrategyResults.curried(strategy.maximizeOwnLiberties)
    check(List((2, 2, 2), (2, 3, 3), (3, 2, 3), (3, 3, 2)))(List((2, 2, 2)))
    check(
      List((2, 2, 3), (2, 3, 2), (3, 2, 2), (2, 3, 3), (3, 2, 3), (3, 3, 2)))(
      List((2, 2, 3), (2, 3, 2), (3, 2, 2))
    )
    check(game.goban.emptyPositions.map(p => (p.x, p.y, p.z)))(List((2, 2, 2)))

  @Test def testMaximizeLiberties2(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    val strategy = SetStrategy(game, Array())
    Assert.assertFalse(strategy.maximizeOwnLiberties(game.goban.emptyPositions).contains(Position(2, 2, 2)))
    Assert.assertFalse(strategy.maximizeOwnLiberties(game.goban.emptyPositions).contains(Position(1, 1, 1)))


def getStrategy(size: Int): SetStrategy =
  SetStrategy(newGame(size), Array())


def checkStrategyResults(
  strategy: Seq[Position] => Seq[Position],
  toCheck: Seq[(Int, Int, Int)], expected: Seq[(Int, Int, Int)]
): Unit =
    val found = strategy(toCheck.map(e => Position(e)))
    assertPositionsEqual(expected, found)
