package go3d.server

import go3d._
import io.circe.parser.*
import org.junit.{Assert, Before, Ignore, Test}

import scala.io.Source
import java.nio.file.Files

class TestIo:

  @Before def initIo(): Unit = Io.init(Files.createTempDirectory("go3d").toString)

  @Test def testSaveGameFailsNonexistentGame(): Unit =
    val gameId = "mock"
    assertThrows[NoSuchElementException]({Io.saveGame(gameId)})

  @Test def testSaveGameFailsNonexistentPlayers(): Unit =
    val gameId = registerGame(TestSize)
    assertThrows[NoSuchElementException]({Io.saveGame(gameId)})

  @Test def testSaveGameWritesFile(): Unit =
    val gameId = registerGame(TestSize)
    val player = registerPlayer(Black, gameId, "mock@")
    Io.saveGame(gameId)
    Assert.assertTrue(Io.exists(s"$gameId.json"))

  @Test def testSaveGameContents(): Unit =
    val gameId = registerGame(TestSize)
    val player = registerPlayer(Black, gameId, "mock@")
    val path = Io.saveGame(gameId)
    val restored = decode[SaveGame](Source.fromFile(path.toFile).getLines.mkString)
    Assert.assertTrue(restored.isRight)
    val value = restored.getOrElse(null)
    Assert.assertEquals(TestSize, value.game.size)
    Assert.assertTrue(value.players.nonEmpty)
    Assert.assertTrue(value.players.contains(Black))

  @Test def testExists(): Unit =
    Io.writeFile("test", "{}")
    Assert.assertTrue(Io.exists("test"))
    Assert.assertFalse(Io.exists("this file should not exist"))

  @Ignore  
  @Test def testGetListOfJsonFiles(): Unit =
    Io.writeFile("test.json", "{}")
    Assert.assertTrue(Io.exists("test.json"))
    val matchingFiles = Io.getListOfFiles(".json").map(f => f.getName)
    Assert.assertEquals(
      java.io.File(Io.baseFolder).listFiles.toList.toString, List("test.json"), matchingFiles
    )
