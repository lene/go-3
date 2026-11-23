package go3d.probe

import org.junit.jupiter.api.{Assertions, Test}

class TestCsv:

  private val Rows: List[GameResult] = List(
    BlackWinsResult,
    CaptureFlipsResult.copy(
      black = "onStarPoints",
      white = "random",
      game = 1,
      record = Some("00abcd")
    ),
    BlackWinsResult.copy(size = 9, game = 2, moves = 729, ending = Ending.Passes)
  )

  @Test def testTheFirstLineIsTheHeader(): Unit =
    val lines = Csv.render(Rows).linesIterator.toList
    Assertions.assertEquals(Csv.Header, lines.head)
    Assertions.assertEquals(
      "size,black,white,game,moves,ending,black_stones,black_area,black_captures," +
        "white_stones,white_area,white_captures,record",
      lines.head
    )
    Assertions.assertEquals(Rows.length + 1, lines.length)

  @Test def testARowHasTheFieldsOfTheGame(): Unit =
    Assertions.assertEquals("5,random,random,0,125,move-limit,60,70,0,40,55,0,", Csv.line(Rows(0)))
    Assertions.assertEquals(
      "5,onStarPoints,random,1,125,move-limit,60,70,0,40,69,4,00abcd",
      Csv.line(Rows(1))
    )

  @Test def testRowsRoundTrip(): Unit =
    Assertions.assertEquals(Right(Rows), Csv.parse(Csv.render(Rows)))

  @Test def testNoRowsRoundTrip(): Unit =
    Assertions.assertEquals(Right(Nil), Csv.parse(Csv.render(Nil)))

  @Test def testTheSameRowsGiveTheSameText(): Unit =
    Assertions.assertEquals(Csv.render(Rows), Csv.render(Rows.map(_.copy())))

  @Test def testAWrongHeaderIsRejected(): Unit =
    Assertions.assertTrue(Csv.parse("size,black\n5,random").isLeft)
    Assertions.assertTrue(Csv.parse("").isLeft)

  @Test def testABadRowIsRejectedWithItsLineNumber(): Unit =
    val text =
      Csv.render(Rows).replace("5,onStarPoints,random,1,125", "5,onStarPoints,random,x,125")
    val message = Csv.parse(text).swap.getOrElse("it parsed")
    Assertions.assertTrue(message.startsWith("line 3:"), message)
    Assertions.assertTrue(message.contains("'x' is not a number"), message)

  @Test def testTheFirstBadRowIsReported(): Unit =
    val text = s"${Csv.Header}\nbad\nworse\n"
    Assertions.assertTrue(Csv.parse(text).swap.getOrElse("it parsed").startsWith("line 2:"))

  @Test def testARowWithTheWrongNumberOfFieldsIsRejected(): Unit =
    Assertions.assertEquals(Left("3 fields instead of 13"), Csv.parseLine("5,random,random"))

  @Test def testAnUnknownEndingIsRejected(): Unit =
    val line = Csv.line(Rows(0)).replace("move-limit", "resigned")
    Assertions.assertEquals(Left("unknown ending 'resigned'"), Csv.parseLine(line))
