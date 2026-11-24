package go3d

import org.junit.jupiter.api.{Assertions, Test}

class TestEngineBenchmark:

  @Test def testTheReportHasARowPerOperationAndPosition(): Unit =
    val lines = EngineBenchmark.report(List(3), 1).linesIterator.toList
    // the header, then the playout and 4 operations at each of 2 positions
    Assertions.assertEquals(2 + 1 + 2 * 4, lines.length, lines.mkString("\n"))
    Assertions.assertTrue(lines.drop(2).forall(_.startsWith("| 3 | ")), lines.mkString("\n"))
