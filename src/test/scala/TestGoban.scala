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

  @Test def testBoardSizeTooSmall(): Unit =
    try
      Goban(1)
    catch
      case e: IllegalArgumentException => return
      case e: _ => Assert.fail("Expected IllegalArgumentException, got "+e.getClass)
    Assert.fail("Expected IllegalArgumentException")

  @Test def testBoardSizeTooBig(): Unit =
    try
      Goban(MaxBoardSize+2)
    catch
      case e: IllegalArgumentException => return
      case e: _ => Assert.fail("Expected IllegalArgumentException, got "+e.getClass)
    Assert.fail("Expected IllegalArgumentException")

  @Test def testBoardSizeEven(): Unit =
    try
      Goban(4)
    catch
      case e: IllegalArgumentException => return
      case e: _ => Assert.fail("Expected IllegalArgumentException, got "+e.getClass)
    Assert.fail("Expected IllegalArgumentException")

  @Test def testPlayersTooSmall(): Unit =
    try
      Goban(TestSize, 1)
    catch
      case e: IllegalArgumentException => return
      case e: _ => Assert.fail("Expected IllegalArgumentException, got "+e.getClass)
    Assert.fail("Expected IllegalArgumentException")

  @Test def testPlayersTooBig(): Unit =
    try
      Goban(TestSize, 3)
    catch
      case e: IllegalArgumentException => return
      case e: _ => Assert.fail("Expected IllegalArgumentException, got "+e.getClass)
    Assert.fail("Expected IllegalArgumentException")

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
