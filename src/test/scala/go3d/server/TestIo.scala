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
    val gameId = Games.register(TestSize)
    assertThrows[NoSuchElementException]({Io.saveGame(gameId)})

  @Test def testSaveGameWritesFile(): Unit =
    val gameId = Games.register(TestSize)
    registerPlayer(Black, gameId, "mock@")
    Io.saveGame(gameId)
    Assert.assertTrue(XIO.exists(s"$gameId.json"))

  @Test def testSaveGameContents(): Unit =
    val gameId = Games.register(TestSize)
    registerPlayer(Black, gameId, "mock@")
    val path = Io.saveGame(gameId)
    val restored = decode[SaveGame](Source.fromFile(path.toFile).getLines.mkString)
    Assert.assertTrue(restored.isRight)
    val value = restored.getOrElse(null)
    Assert.assertEquals(TestSize, value.game.size)
    Assert.assertTrue(value.players.nonEmpty)
    Assert.assertTrue(value.players.contains(Black))

  @Test def testExistsToGainTrustInTestsThatUseIt(): Unit =
    Io.writeFile("test", "{}")
    Assert.assertTrue(XIO.exists("test"))
    Assert.assertFalse(XIO.exists("this file should not exist"))

  @Test def testGetListOfJsonFiles(): Unit =
    Io.writeFile("test.json", "{}")
    Assert.assertTrue(XIO.exists("test.json"))
    val matchingFiles = Io.getListOfFiles(".json").map(f => f.getName)
    Assert.assertEquals(
      java.io.File(Io.baseFolder).listFiles.toList.toString, List("test.json"), matchingFiles
    )

  @Test def testGuardAgainstPathTraversal(): Unit =
    assertThrows[IllegalArgumentException]({Io.writeFile("../test.json", "{}")})
    assertThrows[IllegalArgumentException]({Io.writeFile("/tmp/test.json", "{}")})