package go3d.testing

import go3d.{Black, White, newGame}
import go3d.server.{Games, Io, Jsonify, registerGame, registerPlayer, SaveGame}
import org.junit.{Assert, Ignore, Test}

import java.util.NoSuchElementException
import java.nio.file.{Files, Paths}
import scala.io.Source

class TestIo:
  val io = Io(Files.createTempDirectory("go3d").toString)
  @Test def testSaveGameFailsNonexistentGame(): Unit =
    val gameId = "mock"
    assertThrows[NoSuchElementException]({io.saveGame(gameId)})

  @Test def testSaveGameFailsNonexistentPlayers(): Unit =
    val gameId = registerGame(TestSize)
    assertThrows[NoSuchElementException]({io.saveGame(gameId)})

  @Test def testSaveGameWritesFile(): Unit =
    val gameId = registerGame(TestSize)
    val player = registerPlayer(Black, gameId, "mock@")
    io.saveGame(gameId)
    Assert.assertTrue(io.exists(s"$gameId.json"))

  @Ignore
  @Test def testSaveGameContents(): Unit =
    val gameId = registerGame(TestSize)
    val player = registerPlayer(Black, gameId, "mock@")
    val path = io.saveGame(gameId)
    val restored = Jsonify.fromJson[SaveGame](Source.fromFile(path.toFile).getLines.mkString)
    println(restored.game)
    Assert.assertEquals(TestSize, restored.game.size)
    println(restored.players)
    Assert.assertTrue(restored.players.nonEmpty)
    Assert.assertTrue(restored.players.contains(Black))
