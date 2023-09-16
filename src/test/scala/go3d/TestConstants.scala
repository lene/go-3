package go3d

import org.junit.jupiter.api.{Assertions, Test}

class TestConstants:
  @Test def testMinBoardSize(): Unit =
    Assertions.assertEquals(MinBoardSize, 3)

  @Test def testMaxBoardSize(): Unit =
    Assertions.assertEquals(MaxBoardSize, 25)

  @Test def testMaxHandicaps(): Unit =
    Assertions.assertEquals(MaxHandicaps, 27)

  @Test def testMaxPlayers(): Unit =
    Assertions.assertEquals(MaxPlayers, 2)

  @Test def testDefaultPlayers(): Unit =
    Assertions.assertEquals(DefaultPlayers, 2)
