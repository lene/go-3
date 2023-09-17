package go3d

import org.junit.jupiter.api.{Assertions, Test, Disabled}

class TestArea:

  @Test def testAreasEmptyBoard(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """   |
             |   |
             |   """,
    ))
    Assertions.assertEquals(0, goban.areas.size)

  @Test def testAreasOneStone(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """   |
             | @ |
             |   """,
    ))
    Assertions.assertEquals(1, goban.areas.size)

  @Test def testAreasOneStoneAreaStoneCorrect(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """   |
             | @ |
             |   """,
    ))
    Assertions.assertEquals(Set(Move(2, 2, 1, Black)), goban.areas.head.stones)

  @Test def testAreasOneStoneAreaLibertiesCorrect(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """   |
             | @ |
             |   """,
    ))
    val liberties = goban.areas.head.liberties
    Assertions.assertEquals(5, liberties)

  @Test def testAreas5DisconnectedStones(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """ @ |
             |@ @
             | @ """,
      2 -> """   |
             | @ |
             |   |"""
    ))
    Assertions.assertEquals(5, goban.areas.size)

  @Test def testAreas9ConnectedStones(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """@@ |
             |@ @
             | @@""",
      2 -> """   |
             |@@ |
             | @ |"""
    ))
    Assertions.assertEquals(1, goban.areas.size)

  @Test def testAreas9ConnectedStonesLibertiesCorrect(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """@@ |
             |@ @
             | @@""",
      2 -> """   |
             |@@ |
             | @ |"""
    ))
    Assertions.assertEquals(11, goban.areas.head.liberties)

  @Test def testColor(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   |
          | @ |
          |   """,
    ))
    Assertions.assertEquals(Black, goban.areas.head.color)

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
    goban.areas.foreach(area => Assertions.assertEquals(Black, area.color))

  @Test def testBothColors(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ O |
          |@ @|
          | O |"""
    ))
    Assertions.assertEquals(
      Set(Black, White),
      goban.areas.foldLeft(Set[Color]())((colors, area) => colors + area.color),
  )

  @Test def testAreaFailsIfMultipleColors(): Unit =
    Assertions.assertThrows(
      classOf[BadColorsForArea],
      () => Area(Set(Move(1, 1, 1, Black), Move(1, 1, 2, White)), 1, defaultGoban)
    )

  @Test def testAreaFailsIfFieldEmpty(): Unit =
    Assertions.assertThrows(
      classOf[BadColorsForArea], () => Area(Set(Move(1, 1, 1, Empty)), 1, defaultGoban)
    )

  @Test def testAreaFailsIfAreaEmpty(): Unit =
    Assertions.assertThrows(classOf[BadColorsForArea], () => Area(Set(), 1, defaultGoban))

  @Test def testAreasOneStoneAreaSize(): Unit =
      val goban = gobanWithAreasFromStrings(Map(
        1 ->
          """   |
            | @ |
            |   """,
      ))
      Assertions.assertEquals(1, goban.areas.head.size)

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
      goban.areas.foreach(a => Assertions.assertEquals(1, a.size))

  @Test def testBothColorsSizes(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """ O |
          |@ @|
          | O |"""
    ))
    goban.areas.foreach(a => Assertions.assertEquals(1, a.size))

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
      Assertions.assertEquals(9, goban.areas.head.size)

  @Test def testAreasOneStoneOuterHull(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      2 ->
        """   |
          | @ |
          |   """,
    ))
    Assertions.assertEquals((Position(2, 2, 2), Position(2, 2, 2)), goban.areas.head.outerHull)

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
    Assertions.assertEquals((Position(2, 2, 1), Position(2, 2, 2)), goban.areas.head.outerHull)

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
    Assertions.assertEquals(1, goban.areas.size)
    Assertions.assertEquals((Position(2, 1, 1), Position(3, 2, 2)), goban.areas.head.outerHull)

  @Test def testInsideNoInside(): Unit =
      val goban = gobanWithAreasFromStrings(Map(
        1 ->
          """   |
            | @ |
            |   |""",
      ))
      Assertions.assertEquals(Set(), goban.areas.head.inside)

  @Disabled("Needs investigation")
  @Test def testInside10ConnectedStones(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """     |
          |@@   |
          |     |
          |     |
          |     |""",
      2 ->
        """@@   |
          |@ @  |
          | @@  |
          |     |
          |     |""",
      3 ->
        """     |
          |@@   |
          |     |
          |     |
          |     |"""
    ))
    Assertions.assertEquals(1, goban.areas.head.inside.size, s"${goban.areas.head.inside}")
    Assertions.assertEquals(Set(Position(2, 2, 2)), goban.areas.head.inside)

  @Test def testInside8ConnectedStonesWithFaceBoundary(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """@@ |
          |@ @|
          | @@""",
      2 ->
        """   |
          |@@ |
          |   |"""
    ))
    Assertions.assertEquals(1, goban.areas.head.inside.size)
    Assertions.assertEquals(Set(Position(2, 2, 1)), goban.areas.head.inside)

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

    val blackArea = getOnlyAreaOfColor(goban, Black)
    val blackPositions = goban.allPositions.filter(p => goban.at(p) == Black)
    blackPositions.foreach(
      p => Assertions.assertThrows(classOf[BadColorsForArea], () => blackArea.insideArea(p))
    )

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

    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(1, blackArea.insideArea(Position(2, 2, 2)).size)
    Assertions.assertTrue(blackArea.insideArea(Position(2, 2, 2)).contains(Position(2, 2, 2)))

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

    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(1, blackArea.insideArea(Position(2, 2, 2)).size)
    Assertions.assertTrue(blackArea.insideArea(Position(2, 2, 2)).contains(Position(2, 2, 2)))

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
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(2, blackArea.insideArea(Position(2, 2, 2)).size)
    Assertions.assertEquals(
      Set(Position(2, 2, 2), Position(3, 2, 2)),
      blackArea.insideArea(Position(2, 2, 2))
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
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(2, blackArea.insideArea(Position(2, 2, 2)).size)
    Assertions.assertEquals(
      Set(Position(2, 2, 2), Position(3, 2, 2)),
      blackArea.insideArea(Position(2, 2, 2))
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
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(3, blackArea.insideArea(Position(2, 2, 2)).size)
    Assertions.assertEquals(
      Set(Position(2, 2, 2), Position(3, 2, 2), Position(3, 2, 1)),
      blackArea.insideArea(Position(2, 2, 2))
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
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(3, blackArea.insideArea(Position(2, 2, 2)).size)
    Assertions.assertEquals(
      Set(Position(2, 2, 2), Position(3, 2, 2), Position(3, 2, 1)),
      blackArea.insideArea(Position(2, 2, 2))
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
          |   @@|
          |     |
          |     """,
      3 ->
        """     |
          |  @@@|
          |    @|
          |     |
          |     """,
    ))
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertTrue(blackArea.insideArea(Position(1, 1, 1)).isEmpty)

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
    val blackArea = getOnlyAreaOfColor(goban, Black)
    goban.allPositions.foreach(
      pos => Assertions.assertFalse(blackArea.onBorderOfAreaButNotBoard(pos), s"pos: $pos")
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
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Seq(  // on border of board
      Position(1, 2, 1), Position(1, 3, 1), Position(2, 3, 1),
      Position(1, 2, 2), Position(1, 3, 2), Position(2, 3, 2)).foreach(
        pos => Assertions.assertFalse(blackArea.onBorderOfAreaButNotBoard(pos))
    )
    Seq(  // not on outer hull
      Position(1, 1, 1), Position(2, 1, 1), Position(3, 1, 1), Position(3, 2, 1), Position(3, 3, 1),
      Position(1, 1, 2), Position(2, 1, 2), Position(3, 1, 2), Position(3, 2, 2), Position(3, 3, 2),
      Position(1, 1, 3), Position(1, 2, 3), Position(1, 3, 3)).foreach(
        pos => Assertions.assertFalse(blackArea.onBorderOfAreaButNotBoard(pos))
    )
    // on outer hull, inside board boundary
    Assertions.assertTrue(blackArea.onBorderOfAreaButNotBoard(Position(2, 2, 2)))

  @Test def testAllInsideAreaOne(): Unit =
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
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(Set(Position(1, 3, 1)), blackArea.allInsideAreas)

  @Test def testAllInsideAreaEntireBoard(): Unit =
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
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(
      goban.allPositions.filter(p => goban.at(p) != Black).toSet,
      blackArea.allInsideAreas
    )

  @Test def testAllInsideAreaBiggerArea(): Unit =
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
          |   @@|
          |     |
          |     """,
      3 ->
        """     |
          |  @@@|
          |    @|
          |     |
          |     """,
    ))
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertEquals(Set(Position(5, 2, 1), Position(4, 2, 2),Position(5, 2, 2)), blackArea.allInsideAreas)

  @Test def testIsAliveThreeEmpty(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   @@|
          |  @@ |
          |   @@|
          |     |
          |     """,
      2 ->
        """   @@|
          |  @  |
          |   @@|
          |     |
          |     """,
      3 ->
        """     |
          |  @@@|
          |    @|
          |     |
          |     """,
    ))
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertTrue(blackArea.isAlive)

  @Test def testIsAliveTwoEmptyNotNeighboring(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   @@|
          |  @@ |
          |   @@|
          |     |
          |     """,
      2 ->
        """   @@|
          |  @ @|
          |   @@|
          |     |
          |     """,
      3 ->
        """     |
          |  @@@|
          |    @|
          |     |
          |     """,
    ))
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertTrue(blackArea.isAlive, s"${blackArea.isAlive}")

  @Test def testIsAliveTwoEmptyNeighboring(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   @@|
          |  @@ |
          |   @@|
          |     |
          |     """,
      2 ->
        """   @@|
          |  @@ |
          |   @@|
          |     |
          |     """,
      3 ->
        """     |
          |  @@@|
          |    @|
          |     |
          |     """,
    ))
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertFalse(blackArea.isAlive, s"${blackArea.isAlive}")

  @Test def testIsAliveOneEmpty(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 ->
        """   @@|
          |  @@@|
          |   @@|
          |     |
          |     """,
      2 ->
        """   @@|
          |  @@ |
          |   @@|
          |     |
          |     """,
      3 ->
        """     |
          |  @@@|
          |    @|
          |     |
          |     """,
    ))
    val blackArea = getOnlyAreaOfColor(goban, Black)
    Assertions.assertFalse(blackArea.isAlive, s"${blackArea.isAlive}")

  @Test def testAreNeighbors(): Unit =
    Assertions.assertTrue(areNeighbors(Position(1, 1, 1), Position(1, 1, 2)))
    Assertions.assertTrue(areNeighbors(Position(1, 1, 1), Position(1, 2, 1)))
    Assertions.assertTrue(areNeighbors(Position(1, 1, 1), Position(2, 1, 1)))
    Assertions.assertFalse(areNeighbors(Position(1, 1, 1), Position(1, 2, 2)))
    Assertions.assertFalse(areNeighbors(Position(1, 1, 1), Position(2, 1, 2)))
    Assertions.assertFalse(areNeighbors(Position(1, 1, 1), Position(2, 2, 1)))
    Assertions.assertFalse(areNeighbors(Position(1, 1, 1), Position(2, 2, 2)))

  private def getOnlyAreaOfColor(goban: Goban, color: Color) =
    val areas = goban.areas.filter(_.color == color)
    Assertions.assertEquals(1, areas.size)
    areas.head

def gobanWithAreasFromStrings(levels: Map[Int, String]): Goban =
  val from = fromStrings(levels)
  Goban(from.size, from.stones)

def defaultGoban: Goban = Goban.start(3)
