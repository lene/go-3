package go3d.client

import org.junit.jupiter.api.{Assertions, Disabled, Test}
import go3d.*


class TestSetStrategy:

  @Test def testBestBy(): Unit =
    checkBestBy(SetStrategy(3, Array("random")))

  @Test def testParallelBestBy(): Unit =
    checkBestBy(ParallelSetStrategy(3, Array("random")))

  private def checkBestBy(strategy: SetStrategy): Unit =
    Assertions.assertEquals(List(1, 1), strategy.bestBy(Seq(1, 1, 2, 3, 4), _.abs))
    Assertions.assertEquals(List(4), strategy.bestBy(Seq(1, 1, 2, 3, 4), -_.abs))

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
    checkMaximizeLiberties(SetStrategy(3, Array("maximizeOwnLiberties")))

  @Test def testMaximizeLibertiesParallel(): Unit =
    checkMaximizeLiberties(ParallelSetStrategy(3, Array("maximizeOwnLiberties")))

  private def checkMaximizeLiberties(strategy: SetStrategy): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    val check = checkStrategyResults.curried(strategy.maximizeOwnLiberties(_, game))
    check(List((2, 2, 2), (2, 3, 3), (3, 2, 3), (3, 3, 2)))(List((2, 2, 2)))
    check(
      List((2, 2, 3), (2, 3, 2), (3, 2, 2), (2, 3, 3), (3, 2, 3), (3, 3, 2)))(
      List((2, 2, 3), (2, 3, 2), (3, 2, 2))
    )
    check(game.goban.emptyPositions.map(p => (p.x, p.y, p.z)))(List((2, 2, 2)))

  @Test def testMaximizeLiberties2(): Unit =
    checkMaximizeLiberties2(SetStrategy(3, Array("maximizeOwnLiberties")))

  @Test def testMaximizeLibertiesParallel2(): Unit =
    checkMaximizeLiberties2(ParallelSetStrategy(3, Array("maximizeOwnLiberties")))

  private def checkMaximizeLiberties2(strategy: SetStrategy): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    Assertions.assertFalse(strategy.maximizeOwnLiberties(game.goban.emptyPositions, game).contains(Position(2, 2, 2)))
    Assertions.assertFalse(strategy.maximizeOwnLiberties(game.goban.emptyPositions, game).contains(Position(1, 1, 1)))

  @Test def testMinimizeLiberties(): Unit =
    checkMinimizeLiberties(SetStrategy(3, Array("minimizeOpponentLiberties")))

  @Test def testMinimizeLibertiesParallel(): Unit =
    checkMinimizeLiberties(ParallelSetStrategy(3, Array("minimizeOpponentLiberties")))

  private def checkMinimizeLiberties(strategy: SetStrategy): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
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
    checkMinimizeLibertiesEmptyBoard3(SetStrategy(3, Array("minimizeOpponentLiberties")))

  @Test def testMinimizeLibertiesEmptyBoard3Parallel(): Unit =
    checkMinimizeLibertiesEmptyBoard3(ParallelSetStrategy(3, Array("minimizeOpponentLiberties")))

  private def checkMinimizeLibertiesEmptyBoard3(strategy: SetStrategy): Unit =
    val check = checkStrategyResults.curried(strategy.minimizeOpponentLiberties(_, Game.start(3)))
    val starPoints = StarPoints(3).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMinimizeLibertiesEmptyBoard7(): Unit =
    checkMinimizeLibertiesEmptyBoard7(SetStrategy(7, Array("minimizeOpponentLiberties")))

  @Test def testMinimizeLibertiesEmptyBoard7Parallel(): Unit =
    checkMinimizeLibertiesEmptyBoard7(ParallelSetStrategy(7, Array("minimizeOpponentLiberties")))

  private def checkMinimizeLibertiesEmptyBoard7(strategy: SetStrategy): Unit =
    val check = checkStrategyResults.curried(strategy.minimizeOpponentLiberties(_, Game.start(7)))
    val starPoints = StarPoints(7).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistance(): Unit =
    checkMaximizeDistance(SetStrategy(3, Array("maximizeDistance")))

  @Test def testMaximizeDistanceParallel(): Unit =
    checkMaximizeDistance(ParallelSetStrategy(3, Array("maximizeDistance")))

  private def checkMaximizeDistance(strategy: SetStrategy): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, game))
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((1, 1, 1)))

  @Test def testMaximizeDistanceEmptyBoard3(): Unit =
    checkMaximizeDistanceEmptyBoard3(SetStrategy(3, Array("maximizeDistance")))

  @Test def testMaximizeDistanceEmptyBoard3Parallel(): Unit =
    checkMaximizeDistanceEmptyBoard3(ParallelSetStrategy(3, Array("maximizeDistance")))

  private def checkMaximizeDistanceEmptyBoard3(strategy: SetStrategy): Unit =
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, Game.start(7)))
    val starPoints = StarPoints(3).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistanceEmptyBoard7(): Unit =
    checkMaximizeDistanceEmptyBoard7(SetStrategy(7, Array("maximizeDistance")))

  @Test def testMaximizeDistanceEmptyBoard7Parallel(): Unit =
    checkMaximizeDistanceEmptyBoard7(ParallelSetStrategy(7, Array("maximizeDistance")))

  private def checkMaximizeDistanceEmptyBoard7(strategy: SetStrategy): Unit =
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, Game.start(7)))
    val starPoints = StarPoints(7).all.map(p => (p.x, p.y, p.z))
    check(starPoints)(starPoints)

  @Test def testMaximizeDistance2(): Unit =
    checkMaximizeDistance2(SetStrategy(3, Array("maximizeDistance")))

  @Test def testMaximizeDistance2Parallel(): Unit =
    checkMaximizeDistance2(ParallelSetStrategy(3, Array("maximizeDistance")))

  private def checkMaximizeDistance2(strategy: SetStrategy): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black), Pass(White), Move(1, 1, 1, Black)))
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, game))
    check(List((1, 1, 1), (2, 2, 2), (3, 3, 3)))(List((2, 2, 2)))

  @Test def testMaximizeDistance3(): Unit =
    checkMaximizeDistance3(SetStrategy(3, Array("maximizeDistance")))

  @Test def testMaximizeDistance3Parallel(): Unit =
    checkMaximizeDistance3(ParallelSetStrategy(3, Array("maximizeDistance")))

  private def checkMaximizeDistance3(strategy: SetStrategy): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    val check = checkStrategyResults.curried(strategy.maximizeDistance(_, game))
    check(List((1, 1, 1), (2, 2, 1), (3, 3, 3)))(List((1, 1, 1), (3, 3, 3)))

  @Test def testPrioritiseCapture6Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(2, 2, 2, Black)),
      List((1, 2, 2), (2, 1, 2), (2, 2, 1), (3, 2, 2), (2, 3, 2), (2, 2, 3)),
      false
    )

  @Test def testPrioritiseCapture6LibertiesParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(2, 2, 2, Black)),
      List((1, 2, 2), (2, 1, 2), (2, 2, 1), (3, 2, 2), (2, 3, 2), (2, 2, 3)),
      true
    )

  @Test def testPrioritiseCapture5Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 2, 2, Black)),
      List((1, 2, 1), (1, 1, 2), (2, 2, 2), (1, 2, 3), (1, 3, 2)),
      false
    )

  @Test def testPrioritiseCapture5LibertiesParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 2, 2, Black)),
      List((1, 2, 1), (1, 1, 2), (2, 2, 2), (1, 2, 3), (1, 3, 2)),
      true
    )

  @Test def testPrioritiseCapture4Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 2, Black)),
      List((1, 1, 1), (1, 1, 3), (1, 2, 2), (2, 1, 2)),
      false
    )

  @Test def testPrioritiseCapture4LibertiesParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 2, Black)),
      List((1, 1, 1), (1, 1, 3), (1, 2, 2), (2, 1, 2)),
      true
    )

  @Test def testPrioritiseCapture3Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black)),
      List((1, 1, 2), (1, 2, 1), (2, 1, 1)),
      false
    )

  @Test def testPrioritiseCapture3LibertiesParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black)),
      List((1, 1, 2), (1, 2, 1), (2, 1, 1)),
      true
    )

  @Test def testPrioritiseCapture2Stones3Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White)),
      List((1, 1, 3), (1, 2, 2), (2, 1, 2)),
      false
    )

  @Test def testPrioritiseCapture2Stones3LibertiesParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White)),
      List((1, 1, 3), (1, 2, 2), (2, 1, 2)),
      true
    )

  @Test def testPrioritiseCapture2Stones2Liberties(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White), Pass(Black)),
      List((1, 2, 1), (2, 1, 1)),
      false
    )

  @Test def testPrioritiseCapture2Stones2LibertiesParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White), Pass(Black)),
      List((1, 2, 1), (2, 1, 1)),
      true
    )

  @Test def testPrioritiseCapture2Stones1Liberty(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White), Pass(Black), Move(1, 2, 1, White), Pass(Black)),
      List((2, 1, 1)),
      false
    )

  @Test def testPrioritiseCapture2Stones1LibertyParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Move(1, 1, 2, White), Pass(Black), Move(1, 2, 1, White), Pass(Black)),
      List((2, 1, 1)),
      true
    )

  @Test def testPrioritiseCapture2DifferentAreasSameSizeChoosesBoth(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Pass(White), Move(3, 3, 3, Black)),
      List((1, 1, 2), (1, 2, 1), (2, 1, 1), (2, 3, 3), (3, 2, 3), (3, 3, 2)),
      false
    )

  @Test def testPrioritiseCapture2DifferentAreasSameSizeChoosesBothParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Pass(White), Move(3, 3, 3, Black)),
      List((1, 1, 2), (1, 2, 1), (2, 1, 1), (2, 3, 3), (3, 2, 3), (3, 3, 2)),
      true
    )

  @Test def testPrioritiseCapture2DifferentAreasDifferentSizeChoosesSmaller(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Pass(White), Move(1, 1, 2, Black), Pass(White), Move(3, 3, 3, Black)),
      List((2, 3, 3), (3, 2, 3), (3, 3, 2)),
      false
    )

  @Test def testPrioritiseCapture2DifferentAreasDifferentSizeChoosesSmallerParallel(): Unit =
    check3BoardForPossibleMoves(
      List(Move(1, 1, 1, Black), Pass(White), Move(1, 1, 2, Black), Pass(White), Move(3, 3, 3, Black)),
      List((2, 3, 3), (3, 2, 3), (3, 3, 2)),
      true
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

  @Disabled("Only run this manually (it's a hack)")
  @Test def testParallelization(): Unit =

    def time[R](block: => R): Long =
      val t0 = System.nanoTime()
      val result = block
      System.nanoTime() - t0

    val gameSize = 9
    val strategy = SetStrategy(gameSize, Array("prioritiseCapture"))
    for _ <- 1 to 10 do
      var game = Game.start(gameSize)
      var result: Seq[Position] = Seq()
      var times = Seq[Long]()
      while !game.isOver do
        val color = game.moveColor
        val moves = game.possibleMoves(color)
        times = times.appended(time {
          result = strategy.narrowDown(moves, game)
        })
        game = game.makeMove(Move(result.head, color))
      println(s"Total: ${times.sum / 1000000}ms Average: ${times.sum / times.size / 1000}us")

def defaultStrategy(size: Int): SetStrategy = SetStrategy(size, Array("random"))

def checkStrategyResults(
  strategy: Seq[Position] => Seq[Position],
  toCheck: Seq[(Int, Int, Int)], expected: Seq[(Int, Int, Int)]
): Unit =
    assertPositionsEqual(expected, strategy(toCheck.map(e => Position(e))))

def check3BoardForPossibleMoves(
  moves: List[Move|Pass], expected: List[(Int, Int, Int)], parallel: Boolean
): Unit =
  val strategy = SetStrategy.create(3, Array("prioritiseCapture"), 0, parallel)
  val game = playListOfMoves(3, moves)
  val check = checkStrategyResults.curried(strategy.prioritiseCapture(_, game))
  val starPoints = StarPoints(3).all.map(p => (p.x, p.y, p.z)).toSet -- moves.collect { case m: Move => m }.map(m => (m.x, m.y, m.z))
  check(starPoints.toList)(expected)
