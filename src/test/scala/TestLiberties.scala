package go3d.testing

import go3d._
import org.junit.{Assert, Ignore, Test}

class TestLiberties:

  @Test def testLibertiesFailIfWrongColor(): Unit =
    var game = newGame(TestSize)
    game = game.makeMove(Move(2, 2, 2, Color.Black))
    assertThrows[IllegalArgumentException]({game.hasLiberties(Move(2, 2, 2, Color.Empty))})
    assertThrows[IllegalArgumentException]({game.hasLiberties(Move(2, 2, 2, Color.Sentinel))})

  @Test def testLibertiesOneStone(): Unit =
    val game = newGame(TestSize)
    game.goban.stones(2)(2)(2) = Color.Black
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 2, Color.Black)))

  @Test def testLibertiesTwoDifferentStones(): Unit =
    val game = newGame(TestSize)
    game.goban.stones(2)(2)(2) = Color.Black
    game.goban.stones(2)(2)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 2, Color.Black)))

  @Test def testLibertiesInCenter(): Unit =
    val game = newGame(TestSize)
    game.goban.stones(2)(2)(2) = Color.Black
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 2, Color.Black)))
    game.goban.stones(2)(2)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 2, Color.Black)))
    game.goban.stones(2)(2)(3) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 2, Color.Black)))
    game.goban.stones(2)(1)(2) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 2, Color.Black)))
    game.goban.stones(2)(3)(2) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 2, Color.Black)))
    game.goban.stones(1)(2)(2) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 2, Color.Black)))
    game.goban.stones(3)(2)(2) = Color.White
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 2, 2, Color.Black)))

  @Test def testLibertiesOnFace(): Unit =
    val game = newGame(TestSize)
    game.goban.stones(2)(2)(1) = Color.Black
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 1, Color.Black)))
    game.goban.stones(2)(2)(2) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 1, Color.Black)))
    game.goban.stones(2)(1)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 1, Color.Black)))
    game.goban.stones(2)(3)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 1, Color.Black)))
    game.goban.stones(1)(2)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 2, 1, Color.Black)))
    game.goban.stones(3)(2)(1) = Color.White
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 2, 1, Color.Black)))

  @Test def testLibertiesOnEdge(): Unit =
    val game = newGame(TestSize)
    game.goban.stones(2)(1)(1) = Color.Black
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    game.goban.stones(1)(1)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    game.goban.stones(3)(1)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    game.goban.stones(2)(2)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    game.goban.stones(2)(1)(2) = Color.White
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 1, 1, Color.Black)))

  @Test def testLibertiesInCorner(): Unit =
    val game = newGame(TestSize)
    game.goban.stones(1)(1)(1) = Color.Black
    Assert.assertTrue(game.hasLiberties(Move(1, 1, 1, Color.Black)))
    game.goban.stones(2)(1)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(1, 1, 1, Color.Black)))
    game.goban.stones(1)(2)(1) = Color.White
    Assert.assertTrue(game.hasLiberties(Move(1, 1, 1, Color.Black)))
    game.goban.stones(1)(1)(2) = Color.White
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(1, 1, 1, Color.Black)))

  @Test def testLibertiesWithNeighbor(): Unit =
    val game = newGame(TestSize)
    setListOfStones(game, (2, 1, 1) :: (2, 1, 2) :: Nil, Color.Black)
    setListOfStones(game, (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) :: Nil, Color.White)
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 2, Color.Black)))

  @Test def testLibertiesWithNeighborCaptured(): Unit =
    val game = newGame(TestSize)
    setListOfStones(game, (2, 1, 1) :: (2, 1, 2) :: Nil, Color.Black)
    setListOfStones(
      game,
      (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) ::
        (1, 1, 2) :: (3, 1, 2) :: (2, 2, 2) :: Nil,
      Color.White
    )
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 2, Color.Black)))
    setListOfStones(game, (2, 1, 3) :: Nil, Color.White)
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 1, 2, Color.Black)))

  @Test def testLargerAreaCaptured(): Unit =
    val game = newGame(TestSize)
    setListOfStones(game, (2, 1, 1) :: (2, 1, 2) :: (2, 1, 3) :: Nil, Color.Black)
    setListOfStones(
      game,
      (1, 1, 1) :: (3, 1, 1) :: (2, 2, 1) ::
        (1, 1, 2) :: (3, 1, 2) :: (2, 2, 2) ::
        (1, 1, 3) :: (3, 1, 3) :: Nil,
      Color.White
    )
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 2, Color.Black)))
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 3, Color.Black)))
    setListOfStones(game, (2, 2, 3) :: Nil, Color.White)
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 1, 2, Color.Black)))
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 1, 3, Color.Black)))

  @Test def testOneEye(): Unit =
    val game = newGame(TestSize)
    // a minimal eye in the (1, 1, 1) corner
    setListOfStones(
      game,
      (2, 1, 1) :: (1, 2, 1) ::
        (1, 1, 2) :: (2, 1, 2) :: (1, 2, 2) :: Nil,
      Color.Black
    )
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    // encircle it from outside only
    setListOfStones(
      game,
      (1, 3, 1) :: (2, 2, 1) :: (3, 1, 1) ::
        (1, 3, 2) :: (2, 2, 2) :: (3, 1, 2) ::
        (1, 1, 3) :: (2, 1, 3) :: (1, 2, 3) :: Nil,
      Color.White
    )
    Assert.assertTrue(game.hasLiberties(Move(2, 1, 1, Color.Black)))
    setListOfStones(game, (1, 1, 1) :: Nil, Color.White)
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 1, 1, Color.Black)))
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(1, 2, 1, Color.Black)))
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(1, 1, 2, Color.Black)))
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(2, 1, 2, Color.Black)))
    Assert.assertFalse("\n"+game.toString, game.hasLiberties(Move(1, 2, 2, Color.Black)))

def setListOfStones(game: Game, positions: List[(Int, Int, Int)], color: Color): Unit =
  for pos <- positions do game.goban.stones(pos(0))(pos(1))(pos(2)) = color
