package go3d.testing

import go3d.{Black, Move, Pass, Position, White, newGame}
import go3d.client.{BotClient, SetStrategy, StarPoints, bestBy, totalNumLiberties}
import org.junit.{Assert, BeforeClass, Test}


class TestSetStrategy:

  @Test def testBestBy(): Unit =
    assertCollectionEqual(List(1,1), bestBy(Array(1, 1, 2, 3, 4), _.abs))
    assertCollectionEqual(List(4), bestBy(Array(1, 1, 2, 3, 4), -_.abs))

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

  @Test def testOnStarPointsStrategyCornerStarPointsSmallBoard(): Unit =
    val strategy = getStrategy(3)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(
      List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(
      List((1, 1, 1), (3, 3, 3))
    )
    check(
      List((1, 1, 1), (1, 1, 3), (2, 2, 2), (3, 3, 1), (3, 3, 3)))(
      List((1, 1, 1), (1, 1, 3), (3, 3, 1), (3, 3, 3))
    )

  @Test def testOnStarPointsStrategyCornerStarPoints(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(
      List((2, 2, 2), (4, 4, 4), (6, 6, 6)))(
      List((2, 2, 2), (6, 6, 6))
    )
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))

  @Test def testOnStarPointsStrategyMidLines(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(List((1, 1, 1), (2, 6, 2), (7, 7, 7)))(List((2, 6, 2)))

  @Test def testOnStarPointsStrategyMidFaces(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(List((1, 1, 1), (2, 6, 6), (7, 7, 7)))(List((2, 6, 6)))

  @Test def testOnStarPointsStrategyCenter(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(List((1, 1, 1), (4, 4, 4), (7, 7, 7)))(List((4, 4, 4)))

  @Test def testOnStarPointsStrategyOffStarPoints(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))

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

  @Test def testMinimizeLiberties(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    val strategy = SetStrategy(game, Array())
    val check = checkStrategyResults.curried(strategy.minimizeOpponentLiberties)
    check(List((2, 2, 2), (2, 3, 3), (3, 2, 3), (3, 3, 2)))(List((2, 3, 3), (3, 2, 3), (3, 3, 2)))
    check(
      List((2, 2, 3), (2, 3, 2), (3, 2, 2), (2, 3, 3), (3, 2, 3), (3, 3, 2)))(
      List((2, 3, 3), (3, 2, 3), (3, 3, 2))
    )
    check(
      game.goban.emptyPositions.map(p => (p.x, p.y, p.z)))(
      game.goban.neighbors(Position(3, 3, 3)).map(p => (p.x, p.y, p.z))
    )

  @Test def testMinimizeLibertiesEmptyBoard3(): Unit =
    val strategy = getStrategy(3)
    val check = checkStrategyResults.curried(strategy.minimizeOpponentLiberties)
    val starPoints = StarPoints(3).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMinimizeLibertiesEmptyBoard7(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.minimizeOpponentLiberties)
    val starPoints = StarPoints(7).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistance(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    val strategy = SetStrategy(game, Array())
    val check = checkStrategyResults.curried(strategy.maximizeDistance)
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((1, 1, 1)))

  @Test def testMaximizeDistanceEmptyBoard3(): Unit =
    val strategy = getStrategy(3)
    val check = checkStrategyResults.curried(strategy.maximizeDistance)
    val starPoints = StarPoints(3).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistanceEmptyBoard7(): Unit =
    val strategy = getStrategy(7)
    val check = checkStrategyResults.curried(strategy.maximizeDistance)
    val starPoints = StarPoints(7).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistance2(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black), Pass(White), Move(1, 1, 1, Black)))
    val strategy = SetStrategy(game, Array())
    val check = checkStrategyResults.curried(strategy.maximizeDistance)
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))

  @Test def testMaximizeDistance3(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    val strategy = SetStrategy(game, Array())
    val check = checkStrategyResults.curried(strategy.maximizeDistance)
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((1, 1, 1), (3, 3, 3)))

def getStrategy(size: Int): SetStrategy =
  SetStrategy(newGame(size), Array())


def checkStrategyResults(
  strategy: Seq[Position] => Seq[Position],
  toCheck: Seq[(Int, Int, Int)], expected: Seq[(Int, Int, Int)]
): Unit =
    assertPositionsEqual(expected, strategy(toCheck.map(e => Position(e))))
