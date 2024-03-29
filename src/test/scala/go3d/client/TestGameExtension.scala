package go3d.client

import go3d._
import org.junit.jupiter.api.{Assertions, Test}

class TestGameExtension:

  @Test def testHasNeighborsOfColor(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    game.goban.neighbors(Position(3, 3, 3)).foreach(p => Assertions.assertTrue(game.hasNeighborOfColor(p, Black)))
    game.goban.neighbors(Position(3, 3, 3)).foreach(p => Assertions.assertFalse(game.hasNeighborOfColor(p, White)))
    game.goban.allPositions.foreach(p => Assertions.assertFalse(game.hasNeighborOfColor(p, White)))

  @Test def testTotalNumLibertiesCenter(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    Assertions.assertEquals(6, game.totalNumLiberties(Black))
    Assertions.assertEquals(0, game.totalNumLiberties(White))

  @Test def testTotalNumLibertiesCorner(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black)))
    Assertions.assertEquals(3, game.totalNumLiberties(Black))
    Assertions.assertEquals(0, game.totalNumLiberties(White))

  @Test def testTotalNumLibertiesTwoStonesTogether(): Unit =
    val game = playListOfMoves(5, List[Move|Pass](Move(3, 3, 3, Black), Pass(White), Move(3, 2, 3, Black)))
    Assertions.assertEquals(10, game.totalNumLiberties(Black))
    Assertions.assertEquals(0, game.totalNumLiberties(White))

  @Test def testTotalNumLibertiesTwoStonesApart(): Unit =
    val game = playListOfMoves(5, List[Move|Pass](Move(3, 3, 3, Black), Pass(White), Move(2, 2, 2, Black)))
    Assertions.assertEquals(12, game.totalNumLiberties(Black))
    Assertions.assertEquals(0, game.totalNumLiberties(White))

  @Test def testTotalNumLibertiesEmptyBoard(): Unit =
    val game = playListOfMoves(5, List[Move|Pass]())
    Assertions.assertEquals(0, game.totalNumLiberties(Black))
    Assertions.assertEquals(0, game.totalNumLiberties(White))

  @Test def testGetAreasEmpty(): Unit =
    val game = playListOfMoves(3, List())
    Assertions.assertEquals(Set(), game.getAreas(Black))
    Assertions.assertEquals(Set(), game.getAreas(White))

  @Test def testGetAreasOneStone(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    Assertions.assertEquals(Set(Set(Position(2, 2, 2))), game.getAreas(Black))
    Assertions.assertEquals(Set(),                       game.getAreas(White))

  @Test def testGetAreasTwoDifferentStones(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black), Move(2, 2, 1, White)))
    Assertions.assertEquals(Set(Set(Position(2, 2, 2))), game.getAreas(Black))
    Assertions.assertEquals(Set(Set(Position(2, 2, 1))), game.getAreas(White))

  @Test def testGetAreasTwoSameStonesDisconnected(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black), Pass(White), Move(1, 1, 1, Black)))
    Assertions.assertEquals(Set(Set(Position(3, 3, 3)), Set(Position(1, 1, 1))), game.getAreas(Black))
    Assertions.assertEquals(Set(),                                               game.getAreas(White))

  @Test def testGetAreasTwoSameStonesConnected(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black), Pass(White), Move(3, 3, 2, Black)))
    Assertions.assertEquals(Set(Set(Position(3, 3, 3), Position(3, 3, 2))), game.getAreas(Black))
    Assertions.assertEquals(Set(),                                          game.getAreas(White))

  @Test def testGetAreasTwoSameStonesAlmostConnected(): Unit =
    val game = playListOfMoves(3, List(Move(3, 3, 3, Black), Pass(White), Move(3, 2, 2, Black)))
    Assertions.assertEquals(Set(Set(Position(3, 3, 3)), Set(Position(3, 2, 2))), game.getAreas(Black))
    Assertions.assertEquals(Set(),                                               game.getAreas(White))

  @Test def testLibertiesSingleStone6Liberties(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    Assertions.assertEquals(6, game.liberties(Black, Set(Position(2, 2, 2))))

  @Test def testLibertiesSingleStone5Liberties(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 1, Black)))
    Assertions.assertEquals(5, game.liberties(Black, Set(Position(2, 2, 1))))

  @Test def testLibertiesSingleStone4Liberties(): Unit =
    val game = playListOfMoves(3, List(Move(2, 1, 1, Black)))
    Assertions.assertEquals(4, game.liberties(Black, Set(Position(2, 1, 1))))

  @Test def testLibertiesSingleStone3Liberties(): Unit =
    val game = playListOfMoves(3, List(Move(1, 1, 1, Black)))
    Assertions.assertEquals(3, game.liberties(Black, Set(Position(1, 1, 1))))

  @Test def testLibertiesTwoSameStones10Liberties(): Unit =
    val game = playListOfMoves(5, List(Move(2, 2, 2, Black), Pass(White), Move(2, 2, 3, Black)))
    Assertions.assertEquals(10, game.liberties(Black, Set(Position(2, 2, 2), Position(2, 2, 3))))

  @Test def testLibertiesThreeDifferentStones(): Unit =
    val game = playListOfMoves(
      5, List(Move(2, 2, 2, Black), Move(2, 2, 1, White), Move(2, 2, 3, Black))
    )
    Assertions.assertEquals(9, game.liberties(Black, Set(Position(2, 2, 2), Position(2, 2, 3))))

  @Test def testLibertiesFourDifferentStones(): Unit =
    val game = playListOfMoves(
      5, List(Move(2, 2, 2, Black), Move(2, 2, 1, White), Move(2, 2, 3, Black), Move(2, 2, 4, White))
    )
    Assertions.assertEquals(8, game.liberties(Black, Set(Position(2, 2, 2), Position(2, 2, 3))))

  @Test def testLibertiesFailsIfCalledWithWrongColor(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black)))
    Assertions.assertThrows(
      classOf[AssertionError], () => game.liberties(White, Set(Position(2, 2, 2)))
    )

  @Test def testLibertiesFailsIfCalledWithMixedColor(): Unit =
    val game = playListOfMoves(3, List(Move(2, 2, 2, Black), Move(2, 2, 1, White)))
    Assertions.assertThrows(
      classOf[AssertionError],
      () => game.liberties(White, Set(Position(2, 2, 2), Position(2, 2, 1)))
    )
