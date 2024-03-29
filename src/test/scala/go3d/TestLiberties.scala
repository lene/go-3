package go3d

import org.junit.jupiter.api.{Assertions, Test}

class TestLiberties:

  @Test def testLibertiesFailIfWrongColor(): Unit =
    var goban = Goban.start(TestSize)
    goban = goban.setStone(Move(2, 2, 2, Black))
    Assertions.assertThrows(classOf[ColorMismatch], () => goban.hasLiberties(Move(2, 2, 2, Empty)))
    Assertions.assertThrows(classOf[ColorMismatch], () => goban.hasLiberties(Move(2, 2, 2, Sentinel)))

  @Test def testLibertiesOneStone(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStonesAndCheckLiberties(goban, List((2, 2, 2)), Black, Move(2, 2, 2, Black))

  @Test def testLibertiesTwoDifferentStones(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStonesAndCheckLiberties(goban, List((2, 2, 2)), Black, Move(2, 2, 2, Black))
    directlySetListOfStonesAndCheckLiberties(goban, List((2, 2, 1)), White, Move(2, 2, 2, Black))

  @Test def testLibertiesInCenter(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStonesAndCheckLiberties(goban, List((2, 2, 2)), Black, Move(2, 2, 2, Black))
    directlySetListOfStonesAndCheckLiberties(
      goban, List((2, 2, 1), (2, 2, 3), (2, 1, 2), (2, 3, 2), (1, 2, 2)), White,
      Move(2, 2, 2, Black)
    )
    goban.stones(3)(2)(2) = White
    Assertions.assertFalse(goban.hasLiberties(Move(2, 2, 2, Black)), "\n"+goban.toString)

  @Test def testLibertiesOnFace(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStonesAndCheckLiberties(goban, List((2, 2, 1)), Black, Move(2, 2, 1, Black))
    directlySetListOfStonesAndCheckLiberties(
      goban, List((2, 2, 2), (2, 1, 1), (2, 3, 1), (1, 2, 1)), White,
      Move(2, 2, 1, Black)
    )
    goban.stones(3)(2)(1) = White
    Assertions.assertFalse(goban.hasLiberties(Move(2, 2, 1, Black)), "\n"+goban.toString)

  @Test def testLibertiesOnEdge(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStonesAndCheckLiberties(goban, List((2, 1, 1)), Black, Move(2, 1, 1, Black))
    directlySetListOfStonesAndCheckLiberties(
      goban, List((1, 1, 1), (3, 1, 1), (2, 2, 1)), White,
      Move(2, 1, 1, Black)
    )
    goban.stones(2)(1)(2) = White
    Assertions.assertFalse(goban.hasLiberties(Move(2, 1, 1, Black)), "\n"+goban.toString)

  @Test def testLibertiesInCorner(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStonesAndCheckLiberties(goban, List((1, 1, 1)), Black, Move(1, 1, 1, Black))
    directlySetListOfStonesAndCheckLiberties(
      goban, List((2, 1, 1), (1, 2, 1)), White,
      Move(1, 1, 1, Black)
    )
    goban.stones(1)(1)(2) = White
    Assertions.assertFalse(goban.hasLiberties(Move(1, 1, 1, Black)), "\n"+goban.toString)

  @Test def testLibertiesWithNeighbor(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStones(goban, (2, 1, 1) :: (2, 1, 2) :: Nil, Black)
    directlySetListOfStones(goban, (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) :: Nil, White)
    checkHasLiberties(goban, Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Nil)

  @Test def testLibertiesWithNeighborCaptured(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStones(goban, (2, 1, 1) :: (2, 1, 2) :: Nil, Black)
    directlySetListOfStones(
      goban, (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) :: (1, 1, 2) :: (3, 1, 2) :: (2, 2, 2) :: Nil,
      White
    )
    checkHasLiberties(goban, Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Nil)
    directlySetListOfStones(goban, (2, 1, 3) :: Nil, White)
    checkNoLiberties(goban, Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Nil)

  @Test def testLargerAreaCaptured(): Unit =
    val goban = Goban.start(TestSize)
    directlySetListOfStones(goban, (2, 1, 1) :: (2, 1, 2) :: (2, 1, 3) :: Nil, Black)
    directlySetListOfStones(goban,
      (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) :: (1, 1, 2) :: (3, 1, 2) :: (2, 2, 2) :: (1, 1, 3) ::
        (3, 1, 3) :: Nil,
      White
    )
    checkHasLiberties(
      goban, Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Move(2, 1, 3, Black) :: Nil
    )
    directlySetListOfStones(goban, (2, 2, 3) :: Nil, White)
    checkNoLiberties(
      goban, Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Move(2, 1, 3, Black) :: Nil
    )

  @Test def testOneEye(): Unit =
    val goban = Goban.start(TestSize)
    // eye in the (1, 1, 1) corner
    directlySetListOfStones(
      goban, (2, 1, 1) :: (1, 2, 1) :: (1, 1, 2) :: (2, 1, 2) :: (1, 2, 2) :: Nil, Black
    )
    // encircle it from outside only
    directlySetListOfStones(goban,
      (1, 3, 1) :: (2, 2, 1) :: (3, 1, 1) :: (1, 3, 2) :: (2, 2, 2) :: (3, 1, 2) :: (1, 1, 3) ::
        (2, 1, 3) :: (1, 2, 3) :: Nil, White
    )
    Assertions.assertTrue(goban.hasLiberties(Move(2, 1, 1, Black)))
    goban.stones(1)(1)(1) = White
    checkNoLiberties(goban,
      Move(2, 1, 1, Black) :: Move(1, 2, 1, Black) :: Move(1, 1, 2, Black) ::
        Move(2, 1, 2, Black) :: Move(1, 2, 2, Black) :: Nil
    )

def directlySetListOfStones(goban: Goban, positions: List[(Int, Int, Int)], color: Color): Unit =
  for pos <- positions do goban.stones(pos(0))(pos(1))(pos(2)) = color

def directlySetListOfStonesAndCheckLiberties(goban: Goban, positions: List[(Int, Int, Int)],
                                             color: Color, toCheck: Move): Unit =
  for pos <- positions do
    goban.stones(pos(0))(pos(1))(pos(2)) = color
    Assertions.assertTrue(goban.hasLiberties(toCheck))

def checkHasLiberties(goban: Goban, stones: List[Move], message: String = ""): Unit =
  for stone <- stones do Assertions.assertTrue(goban.hasLiberties(stone), message)

def checkNoLiberties(goban: Goban, stones: List[Move], message: String = ""): Unit =
  for stone <- stones do Assertions.assertFalse(goban.hasLiberties(stone), message)
