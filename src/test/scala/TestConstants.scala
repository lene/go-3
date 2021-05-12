import org.junit.Test
import org.junit.Assert.*

class TestConstants:
  @Test def testMinBoardSize(): Unit = {
    assertEquals(MinBoardSize, 3)
  }

  @Test def testMaxBoardSize(): Unit = {
    assertEquals(MaxBoardSize, 25)
  }

  @Test def testMaxHandicaps(): Unit = {
    assertEquals(MaxHandicaps, 27)
  }

  @Test def testMaxPlayers(): Unit = {
    assertEquals(MaxPlayers, 2)
  }

  @Test def testDefaultPlayers(): Unit = {
    assertEquals(DefaultPlayers, 2)
  }
