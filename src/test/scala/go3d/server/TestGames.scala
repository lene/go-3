package go3d.server

import go3d.Black
import org.junit.jupiter.api.{Assertions, Test}

class TestGames:

  @Test def testAddedGameIsStored(): Unit =
    val gameId = Games.register(3).get
    Assertions.assertTrue(Games.contains(gameId))

  @Test def testDeleteGameRemovesFromActive(): Unit =
    val gameId = Games.register(3).get
    Games.registerPlayer(gameId, Black).get
    Games.deleteGame(gameId)
    Assertions.assertFalse(Games.activeGameIds.exists(_ == gameId))

  @Test def testDeleteGameNotContained(): Unit =
    val gameId = Games.register(3).get
    Games.registerPlayer(gameId, Black).get
    Games.deleteGame(gameId)
    Assertions.assertFalse(Games.contains(gameId))

  @Test def testExpireStaleGamesKeepsActiveGames(): Unit =
    val gameId = Games.register(3).get
    Games.registerPlayer(gameId, Black).get
    Games.expireStaleGames(Long.MaxValue, shortInactiveMs = Long.MaxValue)
    Assertions.assertTrue(Games.contains(gameId))
