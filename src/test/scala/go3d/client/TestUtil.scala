package go3d.client

import org.junit.{Assert, Test, Ignore}

class TestUtil:

  @Test def testGameWithCornerStones(): Unit =
    Assert.assertEquals(
      Util.gameWithCornerStones(3).goban,
      Util.fromStrings(Map(
        1 ->
          """@ @|
            |   |
            |@ @""",
        2 ->
          """   |
            |   |
            |   """,
        3 ->
          """O O|
            |   |
            |O O""",
      ))
    )

