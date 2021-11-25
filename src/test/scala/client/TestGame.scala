package go3d.testing

import go3d.client.{totalNumLiberties, hasNeighborOfColor}
import go3d.*
import org.junit.{Assert, Test}

class TestGameExtension:

  @Test def testHasNeighborsOfColor(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    game.goban.neighbors(Position(3, 3, 3)).map(p => Assert.assertTrue(game.hasNeighborOfColor(p, Black)))
    game.goban.neighbors(Position(3, 3, 3)).map(p => Assert.assertFalse(game.hasNeighborOfColor(p, White)))
    game.goban.allPositions.map(p => Assert.assertFalse(game.hasNeighborOfColor(p, White)))

  @Test def testTotalNumLibertiesCenter(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    Assert.assertEquals(6, game.totalNumLiberties(Black))
    Assert.assertEquals(0, game.totalNumLiberties(White))

  @Test def testTotalNumLibertiesCorner(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    Assert.assertEquals(3, game.totalNumLiberties(Black))
    Assert.assertEquals(0, game.totalNumLiberties(White))

  @Test def testTotalNumLibertiesTwoStonesTogether(): Unit =
    val game = playListOfMoves(5, List[Move|Pass](Move(3, 3, 3, Black), Pass(White), Move(3, 2, 3, Black)))
    Assert.assertEquals(10, game.totalNumLiberties(Black))
    Assert.assertEquals(0, game.totalNumLiberties(White))

  @Test def testTotalNumLibertiesTwoStonesApart(): Unit =
    val game = playListOfMoves(5, List[Move|Pass](Move(3, 3, 3, Black), Pass(White), Move(2, 2, 2, Black)))
    Assert.assertEquals(12, game.totalNumLiberties(Black))
    Assert.assertEquals(0, game.totalNumLiberties(White))

