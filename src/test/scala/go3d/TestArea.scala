package go3d

import org.junit.{Assert, Test}

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

  @Test def testAreas5ConnectedStones(): Unit =
    val goban = gobanWithAreasFromStrings(Map(
      1 -> """@@ |
             |@ @
             | @@""",
      2 -> """   |
             |@@ |
             | @ |"""
    ))
    Assert.assertEquals(1, goban.areas.size)

  @Test def testAreas5ConnectedStonesLibertiesCorrect(): Unit =
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

def gobanWithAreasFromStrings(levels: Map[Int, String]): Goban =
  val from = fromStrings(levels)
  Goban(from.size, from.stones)
