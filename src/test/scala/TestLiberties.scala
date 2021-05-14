package go3d.testing

import go3d._
import org.junit.{Assert, Ignore, Test}

class TestLiberties:

  @Test def testLibertiesFailIfWrongColor(): Unit =
    var goban = Goban(TestSize)
    goban = goban.makeMove(Move(2, 2, 2, Color.Black))
    assertThrowsIllegalArgument({goban.hasLiberties(Move(2, 2, 2, Color.Empty))})
    assertThrowsIllegalArgument({goban.hasLiberties(Move(2, 2, 2, Color.Sentinel))})

  @Test def testLibertiesOneStone(): Unit =
    val goban = Goban(TestSize)
    goban.stones(2)(2)(2) = Color.Black
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 2, Color.Black)))

  @Test def testLibertiesTwoDifferentStones(): Unit =
    val goban = Goban(TestSize)
    goban.stones(2)(2)(2) = Color.Black
    goban.stones(2)(2)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 2, Color.Black)))

  @Test def testLibertiesInCenter(): Unit =
    val goban = Goban(TestSize)
    goban.stones(2)(2)(2) = Color.Black
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 2, Color.Black)))
    goban.stones(2)(2)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 2, Color.Black)))
    goban.stones(2)(2)(3) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 2, Color.Black)))
    goban.stones(2)(1)(2) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 2, Color.Black)))
    goban.stones(2)(3)(2) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 2, Color.Black)))
    goban.stones(1)(2)(2) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 2, Color.Black)))
    goban.stones(3)(2)(2) = Color.White
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 2, 2, Color.Black)))

  @Test def testLibertiesOnFace(): Unit =
    val goban = Goban(TestSize)
    goban.stones(2)(2)(1) = Color.Black
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 1, Color.Black)))
    goban.stones(2)(2)(2) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 1, Color.Black)))
    goban.stones(2)(1)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 1, Color.Black)))
    goban.stones(2)(3)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 1, Color.Black)))
    goban.stones(1)(2)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 2, 1, Color.Black)))
    goban.stones(3)(2)(1) = Color.White
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 2, 1, Color.Black)))

  @Test def testLibertiesOnEdge(): Unit =
    val goban = Goban(TestSize)
    goban.stones(2)(1)(1) = Color.Black
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    goban.stones(1)(1)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    goban.stones(3)(1)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    goban.stones(2)(2)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    goban.stones(2)(1)(2) = Color.White
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 1, 1, Color.Black)))

  @Test def testLibertiesInCorner(): Unit =
    val goban = Goban(TestSize)
    goban.stones(1)(1)(1) = Color.Black
    Assert.assertTrue(goban.hasLiberties(Move(1, 1, 1, Color.Black)))
    goban.stones(2)(1)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(1, 1, 1, Color.Black)))
    goban.stones(1)(2)(1) = Color.White
    Assert.assertTrue(goban.hasLiberties(Move(1, 1, 1, Color.Black)))
    goban.stones(1)(1)(2) = Color.White
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(1, 1, 1, Color.Black)))

  @Test def testLibertiesWithNeighbor(): Unit =
    val goban = Goban(TestSize)
    setListOfStones(goban, (2, 1, 1) :: (2, 1, 2) :: Nil, Color.Black)
    setListOfStones(goban, (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) :: Nil, Color.White)
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 2, Color.Black)))

  @Test def testLibertiesWithNeighborCaptured(): Unit =
    val goban = Goban(TestSize, verbose = true)
    setListOfStones(goban, (2, 1, 1) :: (2, 1, 2) :: Nil, Color.Black)
    setListOfStones(
      goban,
      (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) ::
        (1, 1, 2) :: (3, 1, 2) :: (2, 2, 2) :: Nil,
      Color.White
    )
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 2, Color.Black)))
    setListOfStones(goban, (2, 1, 3) :: Nil, Color.White)
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 1, 2, Color.Black)))

  @Test def testLargerAreaCaptured(): Unit =
    val goban = Goban(TestSize, verbose = true)
    setListOfStones(goban, (2, 1, 1) :: (2, 1, 2) :: (2, 1, 3) :: Nil, Color.Black)
    setListOfStones(
      goban,
      (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) ::
        (1, 1, 2) :: (3, 1, 2) :: (2, 2, 2) ::
        (1, 1, 3) :: (3, 1, 3) :: Nil,
      Color.White
    )
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 2, Color.Black)))
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 3, Color.Black)))
    setListOfStones(goban, (2, 2, 3) :: Nil, Color.White)
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 1, 2, Color.Black)))
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 1, 3, Color.Black)))

  @Test def testOneEye(): Unit =
    val goban = Goban(TestSize, verbose = true)
    // a minimal eye in the (1, 1, 1) corner
    setListOfStones(
      goban,
      (2, 1, 1) :: (1, 2, 1) ::
        (1, 1, 2) :: (2, 1, 2) :: (1, 2, 2) :: Nil,
      Color.Black
    )
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    // encircle it from outside only
    setListOfStones(
      goban,
      (1, 3, 1) :: (2, 2, 1) :: (3, 1, 1) ::
        (1, 3, 2) :: (2, 2, 2) :: (3, 1, 2) ::
        (1, 1, 3) :: (2, 1, 3) :: (1, 2, 3) :: Nil,
      Color.White
    )
    Assert.assertTrue(goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    setListOfStones(goban, (1, 1, 1) :: Nil, Color.White)
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(1, 2, 1, Color.Black)))
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(1, 1, 2, Color.Black)))
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(2, 1, 2, Color.Black)))
    Assert.assertFalse("\n"+goban.toString, goban.hasLiberties(Move(1, 2, 2, Color.Black)))

def setListOfStones(goban: Goban, positions: List[(Int, Int, Int)], color: Color): Unit =
  for pos <- positions do goban.stones(pos(0))(pos(1))(pos(2)) = color
