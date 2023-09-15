package go3d.server

import org.junit.{Assert, Before, Test}

import java.nio.file.Files

class TestGames:
  @Before def initIo(): Unit = Io.init(Files.createTempDirectory("go3d").toString)

  @Test def testAddedGameIsStored(): Unit =
    val gameId = Games.register(3)
    Assert.assertTrue(Games.contains(gameId))
