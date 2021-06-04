package go3d.testing

import go3d.{Black, White, newGame}
import go3d.server.{Games, Io, registerGame, registerPlayer, SaveGame, decodeSaveGame}
import org.junit.{Assert, Ignore, Test}

import java.util.NoSuchElementException
import java.nio.file.{Files, Paths}
import scala.io.Source
import io.circe.parser._

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

  @Test def testSaveGameContents(): Unit =
    val gameId = registerGame(TestSize)
    val player = registerPlayer(Black, gameId, "mock@")
    val path = io.saveGame(gameId)
    val restored = decode[SaveGame](Source.fromFile(path.toFile).getLines.mkString)
    Assert.assertTrue(restored.isRight)
    val value = restored.getOrElse(null)
    Assert.assertEquals(TestSize, value.game.size)
    Assert.assertTrue(value.players.nonEmpty)
    Assert.assertTrue(value.players.contains(Black))
