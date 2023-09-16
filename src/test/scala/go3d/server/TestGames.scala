package go3d.server

import org.junit.jupiter.api.{Assertions, Test, BeforeAll}

import java.nio.file.Files

object TestGames:
  @BeforeAll def initIo(): Unit = Games.init(Files.createTempDirectory("go3d").toString)

class TestGames:

  @Test def testAddedGameIsStored(): Unit =
    val gameId = Games.register(3)
    Assertions.assertTrue(Games.contains(gameId))
