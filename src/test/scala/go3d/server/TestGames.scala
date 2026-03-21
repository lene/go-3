package go3d.server

import go3d.{Black, White}
import org.junit.jupiter.api.{Assertions, Test, BeforeAll}

import java.nio.file.Files

object TestGames:
  @BeforeAll def initIo(): Unit = Games.init(Files.createTempDirectory("go3d").toString)

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

  @Test def testExpireStaleGamesDeletesZeroMoveGames(): Unit =
    val gameId = Games.register(3).get
    Games.registerPlayer(gameId, Black).get
    Games.expireStaleGames(Long.MaxValue, shortInactiveMs = 0)
    Assertions.assertFalse(Games.activeGameIds.exists(_ == gameId))

  @Test def testExpireStaleGamesKeepsActiveGames(): Unit =
    val gameId = Games.register(3).get
    Games.registerPlayer(gameId, Black).get
    Games.expireStaleGames(Long.MaxValue, shortInactiveMs = Long.MaxValue)
    Assertions.assertTrue(Games.contains(gameId))

  @Test def testArchivedGameStillAccessibleViaContains(): Unit =
    val gameId = Games.register(3).get
    Games.registerPlayer(gameId, Black).get
    Games.registerPlayer(gameId, White).get
    // make a move so the game has moves and gets archived (not deleted) on expiry
    Games.update(gameId)(game => game.makeMove(go3d.Move(go3d.Position(1, 1, 1), Black)))
    Games.expireStaleGames(0, shortInactiveMs = Long.MaxValue)
    Assertions.assertFalse(Games.activeGameIds.exists(_ == gameId))
    Assertions.assertTrue(Games.contains(gameId))
