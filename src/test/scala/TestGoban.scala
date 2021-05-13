import go3d.*
import org.junit.{Assert, Test}

class TestGoban:

  val TestSize = MinBoardSize

  @Test def testGobanCtorBasic(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(TestSize, goban.size)
    Assert.assertEquals(DefaultPlayers, goban.numPlayers)

  @Test def testMemoryAllocation(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(TestSize+2, goban.stones.length)
    Assert.assertEquals(TestSize+2, goban.stones(0).length)
    Assert.assertEquals(TestSize+2, goban.stones(TestSize+1).length)
    Assert.assertEquals(TestSize+2, goban.stones(0)(0).length)
    Assert.assertEquals(TestSize+2, goban.stones(TestSize+1)(TestSize+1).length)

  @Test def testSentinels(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(Color.Sentinel, goban.stones(0)(0)(0))
    Assert.assertEquals(Color.Sentinel, goban.stones(TestSize+1)(TestSize+1)(TestSize+1))
    for x <- 1 to TestSize do
      Assert.assertEquals(Color.Sentinel, goban.stones(x)(0)(0))
      Assert.assertEquals(Color.Sentinel, goban.stones(x)(TestSize+1)(TestSize+1))
      for y <- 1 to TestSize do
        Assert.assertEquals(Color.Sentinel, goban.stones(x)(y)(0))
        Assert.assertEquals(Color.Sentinel, goban.stones(x)(y)(TestSize+1))
        for z <- 1 to TestSize do Assert.assertEquals(Color.Empty, goban.stones(x)(y)(z))

  @Test def testBoardSizeTooSmall(): Unit = assertThrowsIllegalArgument({Goban(1)})

  @Test def testBoardSizeTooBig(): Unit = assertThrowsIllegalArgument({Goban(MaxBoardSize+2)})

  @Test def testBoardSizeEven(): Unit = assertThrowsIllegalArgument({Goban(4)})

  @Test def testPlayersTooSmall(): Unit = assertThrowsIllegalArgument({Goban(TestSize, 1)})

  @Test def testPlayersTooBig(): Unit = assertThrowsIllegalArgument({Goban(TestSize, 3)})

  @Test def testEmptyBoardToStringEmptyPlaces(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(Math.pow(TestSize, 3).toInt, goban.toString.count(_ == ' '))

  @Test def testEmptyBoardToStringSentinels(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(
      2*(TestSize+2)*TestSize + 2*TestSize*TestSize,
      goban.toString.count(_ == '·')
    )

  @Test def testEmptyBoardToStringNewlines(): Unit =
    val goban = Goban(TestSize)
    Assert.assertEquals(TestSize*(TestSize+2), goban.toString.count(_ == '\n'))

  @Test def testPosition(): Unit =
    val goban = Goban(TestSize)
    for x <- 1 to TestSize do
      for y <- 1 to TestSize do
        for z <- 1 to TestSize do
          goban.Position(x, y, z)

  @Test def testPositionTooBig(): Unit =
    val goban = Goban(TestSize)
    assertThrowsIllegalArgument({goban.Position(1, 1, TestSize+1)})

  @Test def testPositionTooSmall(): Unit =
    val goban = Goban(TestSize)
    assertThrowsIllegalArgument({goban.Position(1, 1, 0)})

  @Test def testEmptyBoardAt(): Unit =
    val empty = Goban(TestSize)
    for x <- 1 to TestSize do
      for y <- 1 to TestSize do
        for z <- 1 to TestSize do
          Assert.assertEquals(Color.Empty, empty.at(empty.Position(x, y, z)))

  @Test def testSetStone(): Unit =
    val empty = Goban(TestSize)
    val newBoard = empty.newBoard(empty.Move(2, 2, 2, Color.Black))
    Assert.assertEquals(newBoard.at(newBoard.Position(2, 2, 2)), Color.Black)

  @Test def testSetStoneAtOccupiedPositionFails(): Unit =
    val empty = Goban(TestSize)
    val newBoard = empty.newBoard(empty.Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({empty.newBoard(empty.Move(2, 2, 2, Color.White))})

  @Test def testSetTwoSubsequentStonesOfDifferentColorSucceeds(): Unit =
    val empty = Goban(TestSize)
    val firstMove = empty.newBoard(empty.Move(2, 2, 2, Color.Black))
    val secondMove = firstMove.newBoard(firstMove.Move(2, 2, 1, Color.White))
    Assert.assertEquals(secondMove.at(secondMove.Position(2, 2, 2)), Color.Black)
    Assert.assertEquals(secondMove.at(secondMove.Position(2, 2, 1)), Color.White)

  @Test def testSetTwoSubsequentStonesOfSameColorFails(): Unit =
    val empty = Goban(TestSize)
    val firstMove = empty.newBoard(empty.Move(2, 2, 2, Color.Black))
    assertThrowsIllegalMove({firstMove.newBoard(firstMove.Move(2, 2, 1, Color.Black))})

  @Test def testSetAndPassSucceeds(): Unit =
    val empty = Goban(TestSize)
    val firstMove = empty.newBoard(empty.Move(2, 2, 2, Color.Black))
    val secondMove = firstMove.newBoard(firstMove.Pass(Color.White))
    Assert.assertEquals(secondMove.at(secondMove.Position(2, 2, 2)), Color.Black)
    Assert.assertEquals(secondMove.at(secondMove.Position(2, 2, 1)), Color.Empty)

  @Test def testGameOverAfterTwoConsecutivePasses(): Unit =
    val empty = Goban(TestSize)
    val firstMove = empty.newBoard(empty.Pass(Color.Black))
    assertThrowsGameOver({firstMove.newBoard(firstMove.Pass(Color.White))})

def assertThrowsIllegalArgument(f: => Unit): Unit =
  try f
  catch
    case e: IllegalArgumentException => return
    case e: _ => Assert.fail("Expected IllegalArgumentException, got "+e.getClass)
  Assert.fail("Expected IllegalArgumentException")

def assertThrowsIllegalMove(f: => Unit): Unit =
  try f
  catch
    case e: IllegalMove => return
    case e: _ => Assert.fail("Expected IllegalMove, got "+e.getClass)
  Assert.fail("Expected IllegalMove")

def assertThrowsGameOver(f: => Unit): Unit =
  try f
  catch
    case e: GameOver => return
    case e: _ => Assert.fail("Expected GameOver, got "+e.getClass)
  Assert.fail("Expected GameOver")
