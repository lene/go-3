package go3d

import org.junit.{Assert, Ignore, Test}

class TestArea:

  @Test def testAreasEmptyBoard(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """   |
             |   |
             |   """,
    ))
    Assert.assertEquals(0, goban.areas.size)

  @Test def testAreasOneStone(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """   |
             | @ |
             |   """,
    ))
    Assert.assertEquals(1, goban.areas.size)

  @Test def testAreasOneStoneAreaStoneCorrect(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """   |
             | @ |
             |   """,
    ))
    Assert.assertEquals(Set(Move(2, 2, 1, Black)), goban.areas.head.stones)

  @Test def testAreasOneStoneAreaLibertiesCorrect(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """   |
             | @ |
             |   """,
    ))
    val liberties = goban.areas.head.liberties
    Assert.assertEquals(5, liberties)

  @Test def testAreas5DisconnectedStones(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
    ))
    Assert.assertEquals(5, goban.areas.size)

  @Test def testAreas9ConnectedStones(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """@@ |
             |@ @
             | @@""",
      2 -> """   |
             |@@ |
             | @ |"""
    ))
    Assert.assertEquals(1, goban.areas.size)

  @Test def testAreas9ConnectedStonesLibertiesCorrect(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """@@ |
             |@ @
             | @@""",
      2 -> """   |
             |@@ |
             | @ |"""
    ))
    Assert.assertEquals(11, goban.areas.head.liberties)

  @Test def testColor(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   |
          | @ |
          |   """,
    ))
    Assert.assertEquals(Black, goban.areas.head.color)

  @Test def testColors5DisconnectedStones(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @ |
          |@ @|
          | @ |""",
      2 ->
        """   |
          | @ |
          |   |"""
    ))
    goban.areas.foreach(area => Assert.assertEquals(Black, area.color))

  @Test def testBothColors(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ O |
          |@ @|
          | O |"""
    ))
    Assert.assertEquals(
      Set(Black, White),
      goban.areas.foldLeft(Set[Color]())((colors, area) => colors + area.color),
  )

  @Test def testAreaFailsIfMultipleColors(): Unit =
    assertThrows[BadColorsForArea](
      Area(Set(Move(1, 1, 1, Black), Move(1, 1, 2, White)), 1, defaultGoban)
    )

  @Test def testAreaFailsIfFieldEmpty(): Unit =
    assertThrows[BadColorsForArea](Area(Set(Move(1, 1, 1, Empty)), 1, defaultGoban))

  @Test def testAreaFailsIfAreaEmpty(): Unit =
    assertThrows[BadColorsForArea](Area(Set(), 1, defaultGoban))

  @Test def testAreasOneStoneAreaSize(): Unit =
      val goban = gobanWithAreasFromStrings(Map(
        1 ->
          """   |
            | @ |
            |   """,
      ))
      Assert.assertEquals(1, goban.areas.head.size)

  @Test def testAreas5DisconnectedStonesSizes(): Unit =
      val goban = gobanWithAreasFromStrings(Map(
        1 ->
          """ @ |
            |@ @
            | @ """,
        2 ->
          """   |
            | @ |
            |   |"""
      ))
      goban.areas.foreach(a => Assert.assertEquals(1, a.size))

  @Test def testBothColorsSizes(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ O |
          |@ @|
          | O |"""
    ))
    goban.areas.foreach(a => Assert.assertEquals(1, a.size))

  @Test def testAreas9ConnectedStonesSize(): Unit =
      val goban = gobanWithAreasFromStrings(Map(
        1 ->
          """@@ |
            |@ @
            | @@""",
        2 ->
          """   |
            |@@ |
            | @ |"""
      ))
      Assert.assertEquals(9, goban.areas.head.size)

  @Test def testAreasOneStoneOuterHull(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      2 ->
        """   |
          | @ |
          |   """,
    ))
    Assert.assertEquals((Position(2, 2, 2), Position(2, 2, 2)), goban.areas.head.outerHull)

  @Test def testAreasTwoStonesOuterHull(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   |
          | @ |
          |   """,
      2 ->
        """   |
          | @ |
          |   """,
    ))
    Assert.assertEquals((Position(2, 2, 1), Position(2, 2, 2)), goban.areas.head.outerHull)

  @Test def testAreasOuterHullEmptySidePieces(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @ |
          | @ |
          |   """,
      2 ->
        """   |
          | @@|
          |   """,
    ))
    Assert.assertEquals(1, goban.areas.size)
    Assert.assertEquals((Position(2, 1, 1), Position(3, 2, 2)), goban.areas.head.outerHull)

  @Ignore
  @Test def testInsideNoInside(): Unit =
      val goban = gobanWithAreasFromStrings(Map(
        1 ->
          """   |
            | @ |
            |   |""",
      ))
      Assert.assertEquals(Set(), goban.areas.head.inside)

  @Ignore
  @Test def testInside10ConnectedStones(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   |
          |@@ |
          |   |""",
      2 ->
        """@@ |
          |@ @|
          | @@|""",
      3 ->
        """   |
          |@@ |
          |   |"""
    ))
    Assert.assertEquals(1, goban.areas.head.inside.size)
    Assert.assertEquals(Set(Position(2, 2, 2)), goban.areas.head.inside)

  @Ignore
  @Test def testInside8ConnectedStonesWithFaceBoundary(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """@@ |
          |@ @
          | @@""",
      2 ->
        """   |
          |@@ |
          |   |"""
    ))
    Assert.assertEquals(1, goban.areas.head.inside.size)
    Assert.assertEquals(Set(Position(2, 2, 1)), goban.areas.head.inside)

  @Test def testInsideAreaThrowsExceptionIfRequestedForAreaColor(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @ |
          |@@ |
          |   """,
      2 ->
        """ @@|
          |@ @|
          | @@""",
      3 ->
        """   |
          | @@|
          |   """,
    ))

    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    val blackPositions = goban.allPositions.filter(p => goban.at(p) == Black)
    blackPositions.foreach(p => assertThrows[BadColorsForArea](blackAreas.head.insideArea(p)))

  @Test def testInsideAreaOneEmpty(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @ |
          |@@ |
          |   """,
      2 ->
        """ @@|
          |@ @|
          | @@""",
      3 ->
        """   |
          | @@|
          |   """,
    ))

    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    Assert.assertEquals(1, blackAreas.head.insideArea(Position(2, 2, 2)).size)
    Assert.assertTrue(blackAreas.head.insideArea(Position(2, 2, 2)).contains(Position(2, 2, 2)))

  @Test def testInsideAreaOneOpponent(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @ |
          |@@ |
          |   """,
      2 ->
        """ @@|
          |@O@|
          | @@""",
      3 ->
        """   |
          | @@|
          |   """,
    ))

    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    Assert.assertEquals(1, blackAreas.head.insideArea(Position(2, 2, 2)).size)
    Assert.assertTrue(blackAreas.head.insideArea(Position(2, 2, 2)).contains(Position(2, 2, 2)))

  @Test def testInsideAreaTwoEmpty(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @ |
          |@@@|
          |   """,
      2 ->
        """ @@|
          |@  |
          | @@""",
      3 ->
        """   |
          |@@@|
          | @ """,
    ))
    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    Assert.assertEquals(2, blackAreas.head.insideArea(Position(2, 2, 2)).size)
    Assert.assertEquals(
      Set(Position(2, 2, 2), Position(3, 2, 2)),
      blackAreas.head.insideArea(Position(2, 2, 2))
    )

  @Test def testInsideAreaOneEmptyOneOpponent(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @ |
          |@@@|
          |   """,
      2 ->
        """ @@|
          |@ O|
          | @@""",
      3 ->
        """   |
          |@@@|
          | @ """,
    ))
    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    Assert.assertEquals(2, blackAreas.head.insideArea(Position(2, 2, 2)).size)
    Assert.assertEquals(
      Set(Position(2, 2, 2), Position(3, 2, 2)),
      blackAreas.head.insideArea(Position(2, 2, 2))
    )

  @Test def testInsideAreaThreeEmpty(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @@|
          |@@ |
          | @@""",
      2 ->
        """ @@|
          |@  |
          | @@""",
      3 ->
        """   |
          |@@@|
          | @ """,
    ))
    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    Assert.assertEquals(3, blackAreas.head.insideArea(Position(2, 2, 2)).size)
    Assert.assertEquals(
      Set(Position(2, 2, 2), Position(3, 2, 2), Position(3, 2, 1)),
      blackAreas.head.insideArea(Position(2, 2, 2))
    )

  @Test def testInsideAreaTwoEmptyOneOpponent(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @@|
          |@@ |
          | @@""",
      2 ->
        """ @@|
          |@ O|
          | @@""",
      3 ->
        """   |
          |@@@|
          | @ """,
    ))
    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    Assert.assertEquals(3, blackAreas.head.insideArea(Position(2, 2, 2)).size)
    Assert.assertEquals(
      Set(Position(2, 2, 2), Position(3, 2, 2), Position(3, 2, 1)),
      blackAreas.head.insideArea(Position(2, 2, 2))
    )

  @Test def testInsideAreaOutsideArea(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   @@|
          |  @@ |
          |   @@|
          |     |
          |     """,
      2 ->
        """   @@|
          |  @ O|
          |   @@
          |     |
          |     """,
      3 ->
        """     |
          |  @@@|
          |    @
          |     |
          |     """,
    ))
    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    Assert.assertTrue(blackAreas.head.insideArea(Position(1, 1, 1)).isEmpty)

  @Test def testOnBorderOfOuterHullButNotBoardOuterHullEqualsBoard(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ @@|
          |@@ |
          | @@""",
      2 ->
        """ @@|
          |@ O|
          | @@""",
      3 ->
        """   |
          |@@@|
          | @ """,
    ))
    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    goban.allPositions.foreach(
      pos => Assert.assertFalse(s"pos: $pos", blackAreas.head.onBorderOfAreaButNotBoard(pos))
    )

  @Test def testOnBorderOfOuterHullButNotBoardOuterHullSmallerThanBoard(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   |
          |@@ |
          | @ """,
      2 ->
        """   |
          |@@ |
          |@@ """,
    ))
    val blackAreas = goban.areas.filter(_.color == Black)
    Assert.assertEquals(1, blackAreas.size)
    Seq(  // on border of board
      Position(1, 2, 1), Position(1, 3, 1), Position(2, 3, 1),
      Position(1, 2, 2), Position(1, 3, 2), Position(2, 3, 2)).foreach(
        pos => Assert.assertFalse(blackAreas.head.onBorderOfAreaButNotBoard(pos))
    )
    Seq(  // not on outer hull
      Position(1, 1, 1), Position(2, 1, 1), Position(3, 1, 1), Position(3, 2, 1), Position(3, 3, 1),
      Position(1, 1, 2), Position(2, 1, 2), Position(3, 1, 2), Position(3, 2, 2), Position(3, 3, 2),
      Position(1, 1, 3), Position(1, 2, 3), Position(1, 3, 3)).foreach(
        pos => Assert.assertFalse(blackAreas.head.onBorderOfAreaButNotBoard(pos))
    )
    // on outer hull, inside board boundary
    Assert.assertTrue(blackAreas.head.onBorderOfAreaButNotBoard(Position(2, 2, 2)))

def gobanWithAreasFromStrings(levels: Map[Int, String]): Goban =
  val from = fromStrings(levels)
  Goban(from.size, from.stones)

def defaultGoban: Goban = newGoban(3)
