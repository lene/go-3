package go3d

import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestLiberties:

  @Test def testLibertiesFailIfWrongColor(): Unit =
    var goban = Goban.start(TestSize).success.value
    goban = goban.setStone(Move(2, 2, 2, Black))
    Assertions.assertThrows(
      classOf[RuntimeException],
      () => goban.groupHasLiberties(Move(2, 2, 2, Empty))
    )
    Assertions.assertThrows(
      classOf[RuntimeException],
      () => goban.groupHasLiberties(Move(2, 2, 2, Sentinel))
    )

  @Test def testLibertiesOneStone(): Unit =
    val goban = Goban.start(TestSize).success.value
    val _ = setStonesCheckingLiberties(goban, List((2, 2, 2)), Black, Move(2, 2, 2, Black))

  @Test def testLibertiesTwoDifferentStones(): Unit =
    val goban = Goban.start(TestSize).success.value
    val black = setStonesCheckingLiberties(goban, List((2, 2, 2)), Black, Move(2, 2, 2, Black))
    val _ = setStonesCheckingLiberties(black, List((2, 2, 1)), White, Move(2, 2, 2, Black))

  @Test def testLibertiesInCenter(): Unit =
    val goban = Goban.start(TestSize).success.value
    val black = setStonesCheckingLiberties(goban, List((2, 2, 2)), Black, Move(2, 2, 2, Black))
    val surrounded = setStonesCheckingLiberties(
      black,
      List((2, 2, 1), (2, 2, 3), (2, 1, 2), (2, 3, 2), (1, 2, 2)),
      White,
      Move(2, 2, 2, Black)
    ).setStone(3, 2, 2, White)
    Assertions.assertFalse(
      surrounded.groupHasLiberties(Move(2, 2, 2, Black)),
      "\n" + surrounded.toString
    )

  @Test def testLibertiesOnFace(): Unit =
    val goban = Goban.start(TestSize).success.value
    val black = setStonesCheckingLiberties(goban, List((2, 2, 1)), Black, Move(2, 2, 1, Black))
    val surrounded = setStonesCheckingLiberties(
      black,
      List((2, 2, 2), (2, 1, 1), (2, 3, 1), (1, 2, 1)),
      White,
      Move(2, 2, 1, Black)
    ).setStone(3, 2, 1, White)
    Assertions.assertFalse(
      surrounded.groupHasLiberties(Move(2, 2, 1, Black)),
      "\n" + surrounded.toString
    )

  @Test def testLibertiesOnEdge(): Unit =
    val goban = Goban.start(TestSize).success.value
    val black = setStonesCheckingLiberties(goban, List((2, 1, 1)), Black, Move(2, 1, 1, Black))
    val surrounded = setStonesCheckingLiberties(
      black,
      List((1, 1, 1), (3, 1, 1), (2, 2, 1)),
      White,
      Move(2, 1, 1, Black)
    ).setStone(2, 1, 2, White)
    Assertions.assertFalse(
      surrounded.groupHasLiberties(Move(2, 1, 1, Black)),
      "\n" + surrounded.toString
    )

  @Test def testLibertiesInCorner(): Unit =
    val goban = Goban.start(TestSize).success.value
    val black = setStonesCheckingLiberties(goban, List((1, 1, 1)), Black, Move(1, 1, 1, Black))
    val surrounded = setStonesCheckingLiberties(
      black,
      List((2, 1, 1), (1, 2, 1)),
      White,
      Move(1, 1, 1, Black)
    ).setStone(1, 1, 2, White)
    Assertions.assertFalse(
      surrounded.groupHasLiberties(Move(1, 1, 1, Black)),
      "\n" + surrounded.toString
    )

  @Test def testLibertiesWithNeighbor(): Unit =
    val goban = setStones(
      setStones(Goban.start(TestSize).success.value, (2, 1, 1) :: (2, 1, 2) :: Nil, Black),
      (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) :: Nil,
      White
    )
    checkHasLiberties(goban, Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Nil)

  @Test def testLibertiesWithNeighborCaptured(): Unit =
    val goban = setStones(
      setStones(Goban.start(TestSize).success.value, (2, 1, 1) :: (2, 1, 2) :: Nil, Black),
      (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) :: (1, 1, 2) :: (3, 1, 2) :: (2, 2, 2) :: Nil,
      White
    )
    checkHasLiberties(goban, Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Nil)
    val captured = goban.setStone(2, 1, 3, White)
    checkNoLiberties(captured, Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Nil)

  @Test def testLargerAreaCaptured(): Unit =
    val goban = setStones(
      setStones(
        Goban.start(TestSize).success.value,
        (2, 1, 1) :: (2, 1, 2) :: (2, 1, 3) :: Nil,
        Black
      ),
      (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) :: (1, 1, 2) :: (3, 1, 2) :: (2, 2, 2) :: (1, 1, 3) ::
        (3, 1, 3) :: Nil,
      White
    )
    val group = Move(2, 1, 1, Black) :: Move(2, 1, 2, Black) :: Move(2, 1, 3, Black) :: Nil
    checkHasLiberties(goban, group)
    checkNoLiberties(goban.setStone(2, 2, 3, White), group)

  @Test def testOneEye(): Unit =
    // eye in the (1, 1, 1) corner
    val eye = setStones(
      Goban.start(TestSize).success.value,
      (2, 1, 1) :: (1, 2, 1) :: (1, 1, 2) :: (2, 1, 2) :: (1, 2, 2) :: Nil,
      Black
    )
    // encircle it from outside only
    val goban = setStones(
      eye,
      (1, 3, 1) :: (2, 2, 1) :: (3, 1, 1) :: (1, 3, 2) :: (2, 2, 2) :: (3, 1, 2) :: (1, 1, 3) ::
        (2, 1, 3) :: (1, 2, 3) :: Nil,
      White
    )
    Assertions.assertTrue(goban.groupHasLiberties(Move(2, 1, 1, Black)))
    checkNoLiberties(
      goban.setStone(1, 1, 1, White),
      Move(2, 1, 1, Black) :: Move(1, 2, 1, Black) :: Move(1, 1, 2, Black) ::
        Move(2, 1, 2, Black) :: Move(1, 2, 2, Black) :: Nil
    )

/** `goban` with stones of `color` on `positions`, set without checking the rules. */
def setStones(goban: Goban, positions: List[(Int, Int, Int)], color: Color): Goban =
  positions.foldLeft(goban)((board, pos) => board.setStone(pos(0), pos(1), pos(2), color))

/** [[setStones]], checking after each stone that `toCheck` still has liberties. */
def setStonesCheckingLiberties(
    goban: Goban,
    positions: List[(Int, Int, Int)],
    color: Color,
    toCheck: Move
): Goban =
  positions.foldLeft(goban) { (board, pos) =>
    val next = board.setStone(pos(0), pos(1), pos(2), color)
    Assertions.assertTrue(next.groupHasLiberties(toCheck), next.toString)
    next
  }

def checkHasLiberties(goban: Goban, stones: List[Move], message: String = ""): Unit =
  for stone <- stones do Assertions.assertTrue(goban.groupHasLiberties(stone), message)

def checkNoLiberties(goban: Goban, stones: List[Move], message: String = ""): Unit =
  for stone <- stones do Assertions.assertFalse(goban.groupHasLiberties(stone), message)
