package go3d.server

import go3d.Black
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestGames:

  @Test def testAddedGameIsStored(): Unit =
    val gameId = Games.register(3).success.value
    Assertions.assertTrue(Games.contains(gameId))

  @Test def testDeleteGameRemovesFromActive(): Unit =
    val gameId = Games.register(3).success.value
    Games.registerPlayer(gameId, Black).success.value
    Games.deleteGame(gameId)
    Assertions.assertFalse(Games.activeGameIds.exists(_ == gameId))

  @Test def testDeleteGameNotContained(): Unit =
    val gameId = Games.register(3).success.value
    Games.registerPlayer(gameId, Black).success.value
    Games.deleteGame(gameId)
    Assertions.assertFalse(Games.contains(gameId))

  @Test def testExpireStaleGamesKeepsActiveGames(): Unit =
    val gameId = Games.register(3).success.value
    Games.registerPlayer(gameId, Black).success.value
    Games.expireStaleGames(Long.MaxValue, shortInactiveMs = Long.MaxValue)
    Assertions.assertTrue(Games.contains(gameId))
