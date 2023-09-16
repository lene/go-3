package go3d.client

import org.junit.jupiter.api.{Assertions, Test}

class TestUtil:

  @Test def testGameWithCornerStones(): Unit =
    Assertions.assertEquals(
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

