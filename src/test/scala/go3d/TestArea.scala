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
    assertThrows[BadColorsForArea](Area(Set(Move(1, 1, 1, Black), Move(1, 1, 2, White)), 1))

  @Test def testAreaFailsIfFieldEmpty(): Unit =
    assertThrows[BadColorsForArea](Area(Set(Move(1, 1, 1, Empty)), 1))

  @Test def testAreaFailsIfAreaEmpty(): Unit =
    assertThrows[BadColorsForArea](Area(Set(), 1))

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


def gobanWithAreasFromStrings(levels: Map[Int, String]): Goban =
  val from = fromStrings(levels)
  Goban(from.size, from.stones)
