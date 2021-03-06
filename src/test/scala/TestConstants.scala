package go3d.testing

import org.junit.{Assert, Test}
import go3d.*

class TestConstants:
  @Test def testMinBoardSize(): Unit =
    Assert.assertEquals(MinBoardSize, 3)

  @Test def testMaxBoardSize(): Unit =
    Assert.assertEquals(MaxBoardSize, 25)

  @Test def testMaxHandicaps(): Unit =
    Assert.assertEquals(MaxHandicaps, 27)

  @Test def testMaxPlayers(): Unit =
    Assert.assertEquals(MaxPlayers, 2)

  @Test def testDefaultPlayers(): Unit =
    Assert.assertEquals(DefaultPlayers, 2)
