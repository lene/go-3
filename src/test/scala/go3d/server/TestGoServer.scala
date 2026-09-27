package go3d.server

import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestGoServer:

  @Test def testRandomGamePlaysMoves(): Unit =
    val game = GoServer.randomGame(3, 5).success.value
    Assertions.assertTrue(game.moves.nonEmpty)
    Assertions.assertEquals(3, game.size)
