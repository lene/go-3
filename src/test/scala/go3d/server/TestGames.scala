package go3d.server

import org.junit.{Assert, BeforeClass, Test}

import java.nio.file.Files

object TestGames:
  @BeforeClass def initIo(): Unit = Games.init(Files.createTempDirectory("go3d").toString)

class TestGames:

  @Test def testAddedGameIsStored(): Unit =
    val gameId = Games.register(3)
    Assert.assertTrue(Games.contains(gameId))
