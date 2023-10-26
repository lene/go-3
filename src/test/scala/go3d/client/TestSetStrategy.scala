package go3d.client

import org.junit.jupiter.api.{Assertions, Test}

import go3d._


class TestSetStrategy:

  @Test def testBestBy(): Unit =
    Assertions.assertEquals(List(1,1), bestBy(Seq(1, 1, 2, 3, 4), _.abs))
    Assertions.assertEquals(List(4), bestBy(Seq(1, 1, 2, 3, 4), -_.abs))

  @Test def testClosestToCenterStrategy(): Unit =
    val strategy = SetStrategy(3, Array("closestToCenter"))
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
    val strategy = defaultStrategy(3)
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
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(
      List((2, 2, 2), (4, 4, 4), (6, 6, 6)))(
      List((2, 2, 2), (6, 6, 6))
    )
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))

  @Test def testOnStarPointsStrategyMidLines(): Unit =
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(List((1, 1, 1), (2, 6, 2), (7, 7, 7)))(List((2, 6, 2)))

  @Test def testOnStarPointsStrategyMidFaces(): Unit =
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(List((1, 1, 1), (2, 6, 6), (7, 7, 7)))(List((2, 6, 6)))

  @Test def testOnStarPointsStrategyCenter(): Unit =
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(List((1, 1, 1), (4, 4, 4), (7, 7, 7)))(List((4, 4, 4)))

  @Test def testOnStarPointsStrategyOffStarPoints(): Unit =
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.onStarPoints)
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))

  @Test def testClosestToStarPointsStrategyCornerStarPointsSmallBoard(): Unit =
    val strategy = defaultStrategy(3)
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
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(
      List((2, 2, 2), (4, 4, 4), (6, 6, 6)))(
      List((2, 2, 2), (6, 6, 6))
    )
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))

  @Test def testClosestToStarPointsStrategyMidLines(): Unit =
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(List((1, 1, 1), (2, 6, 2), (7, 7, 7)))(List((2, 6, 2)))

  @Test def testClosestToStarPointsStrategyMidFaces(): Unit =
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(List((1, 1, 1), (2, 6, 6), (7, 7, 7)))(List((2, 6, 6)))

  @Test def testClosestToStarPointsStrategyCenter(): Unit =
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(List((1, 1, 1), (4, 4, 4), (7, 7, 7)))(List((4, 4, 4)))

  @Test def testClosestToStarPointsStrategyOffStarPoints(): Unit =
    val strategy = defaultStrategy(7)
    val check = checkStrategyResults.curried(strategy.closestToStarPoints)
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((2, 2, 1)))

  @Test def testMaximizeLiberties(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    val strategy = SetStrategy(3, Array("maximizeOwnLiberties"))
    val check = checkStrategyResults.curried(strategy.maximizeOwnLiberties(_, game))
    check(List((2, 2, 2), (2, 3, 3), (3, 2, 3), (3, 3, 2)))(List((2, 2, 2)))
    check(
      List((2, 2, 3), (2, 3, 2), (3, 2, 2), (2, 3, 3), (3, 2, 3), (3, 3, 2)))(
      List((2, 2, 3), (2, 3, 2), (3, 2, 2))
    )
    check(game.goban.emptyPositions.map(p => (p.x, p.y, p.z)))(List((2, 2, 2)))

  @Test def testMaximizeLiberties2(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    val strategy = SetStrategy(3, Array("maximizeOwnLiberties"))
    Assertions.assertFalse(strategy.maximizeOwnLiberties(game.goban.emptyPositions, game).contains(Position(2, 2, 2)))
    Assertions.assertFalse(strategy.maximizeOwnLiberties(game.goban.emptyPositions, game).contains(Position(1, 1, 1)))

  @Test def testMinimizeLiberties(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    val strategy = SetStrategy(3, Array("minimizeOpponentLiberties"))
    val check = checkStrategyResults.curried(strategy.minimizeOpponentLiberties(_, game))
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
    val strategy = SetStrategy(3, Array("minimizeOpponentLiberties"))
    val check = checkStrategyResults.curried(strategy.minimizeOpponentLiberties(_, Game.start(3)))
    val starPoints = StarPoints(3).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMinimizeLibertiesEmptyBoard7(): Unit =
    val strategy = SetStrategy(7, Array("minimizeOpponentLiberties"))
    val check = checkStrategyResults.curried(strategy.minimizeOpponentLiberties(_, Game.start(7)))
    val starPoints = StarPoints(7).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistance(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    val strategy = SetStrategy(3, Array("maximizeDistance"))
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, game))
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((1, 1, 1)))

  @Test def testMaximizeDistanceEmptyBoard3(): Unit =
    val strategy = SetStrategy(3, Array("maximizeDistance"))
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, Game.start(7)))
    val starPoints = StarPoints(3).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistanceEmptyBoard7(): Unit =
    val strategy = SetStrategy(7, Array("maximizeDistance"))
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, Game.start(7)))
    val starPoints = StarPoints(7).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistance2(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black), Pass(White), Move(1, 1, 1, Black)))
    val strategy = SetStrategy(3, Array("maximizeDistance"))
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, game))
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))

  @Test def testMaximizeDistance3(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    val strategy = SetStrategy(3, Array("maximizeDistance"))
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, game))
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((1, 1, 1), (3, 3, 3)))

  @Test def testPrioritiseCapture6Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(2, 2, 2, Black)),
      List((1, 2, 2), (2, 1, 2), (2, 2, 1), (3, 2, 2), (2, 3, 2), (2, 2, 3))
    )

  @Test def testPrioritiseCapture5Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 2, 2, Black)),
      List((1, 2, 1), (1, 1, 2), (2, 2, 2), (1, 2, 3), (1, 3, 2))
    )

  @Test def testPrioritiseCapture4Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 2, Black)),
      List((1, 1, 1), (1, 1, 3), (1, 2, 2), (2, 1, 2))
    )

  @Test def testPrioritiseCapture3Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black)),
      List((1, 1, 2), (1, 2, 1), (2, 1, 1))
    )

  @Test def testPrioritiseCapture2Stones3Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White)),
      List((1, 1, 3), (1, 2, 2), (2, 1, 2))
    )

  @Test def testPrioritiseCapture2Stones2Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White), Pass(Black)),
      List((1, 2, 1), (2, 1, 1))
    )

  @Test def testPrioritiseCapture2Stones1Liberty(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White), Pass(Black), Move(1, 2, 1, White), Pass(Black)),
      List((2, 1, 1))
    )

  @Test def testPrioritiseCapture2DifferentAreasSameSizeChoosesBoth(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Pass(White), Move(3, 3, 3, Black)),
      List((1, 1, 2), (1, 2, 1), (2, 1, 1), (2, 3, 3), (3, 2, 3), (3, 3, 2))
    )

  @Test def testPrioritiseCapture2DifferentAreasDifferentSizeChoosesSmaller(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Pass(White), Move(1, 1, 2, Black), Pass(White), Move(3, 3, 3, Black)),
      List((2, 3, 3), (3, 2, 3), (3, 3, 2))
    )

  @Test def testNarrowDownRandom(): Unit =
    val game = Game.start(3)
    val strategy = SetStrategy(3, Array("random"))
    val check = checkStrategyResults.curried(strategy.narrowDown(_, game))
    check(
      List((1, 1, 1), (1, 1, 2), (1, 2, 1), (1, 2, 2), (2, 1, 1), (2, 1, 2), (2, 2, 1), (2, 2, 2))
    )(
      List((1, 1, 1), (1, 1, 2), (1, 2, 1), (1, 2, 2), (2, 1, 1), (2, 1, 2), (2, 2, 1), (2, 2, 2))
    )
def defaultStrategy(size: Int): SetStrategy = SetStrategy(size, Array("random"))

def checkStrategyResults(
  strategy: Seq[Position] => Seq[Position],
  toCheck: Seq[(Int, Int, Int)], expected: Seq[(Int, Int, Int)]
): Unit =
    assertPositionsEqual(expected, strategy(toCheck.map(e => Position(e))))

def check3BoardForPossibleMoves(moves: List[Move|Pass], expected: List[(Int, Int, Int)]): Unit =
  val game = playListOfMoves(3, moves)
  val strategy = SetStrategy(3, Array("prioritiseCapture"))
  val check = checkStrategyResults.curried(strategy.prioritiseCapture(_, game))
  val starPoints = StarPoints(3).all.map(p => (p.x, p.y, p.z)).toSet -- moves.collect { case m: Move => m }.map(m => (m.x, m.y, m.z))
  check(starPoints.toList)(expected)
