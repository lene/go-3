package go3d.server

import go3d.*
import io.circe.parser.*
import org.junit.{Assert, BeforeClass, Test}

import scala.io.Source
import java.nio.file.{Files, Paths}

object TestFileIo:
  var fileIO: Option[FileIO] = None
  @BeforeClass def initIo(): Unit =
    Games.init(Files.createTempDirectory("go3d").toString)
    fileIO = Games.fileIO

class TestFileIo:

  @Test def testSaveGameFailsNonexistentGame(): Unit =
    val gameId = "mock"
    assertThrows[NoSuchElementException]({TestFileIo.fileIO.get.saveGame(gameId)})

  @Test def testSaveGameFailsNonexistentPlayers(): Unit =
    val gameId = Games.register(TestSize)
    assertThrows[NoSuchElementException]({TestFileIo.fileIO.get.saveGame(gameId)})

  @Test def testSaveGameWritesFile(): Unit =
    val gameId = Games.register(TestSize)
    registerPlayer(Black, gameId, "mock@")
    TestFileIo.fileIO.get.saveGame(gameId)
    Assert.assertTrue(
      s"$gameId in ${IOForTests.files}?",
      Files.exists(Paths.get(TestFileIo.fileIO.get.baseFolder, s"$gameId.json"))
    )

  @Test def testSaveGameContents(): Unit =
    val gameId = Games.register(TestSize)
    registerPlayer(Black, gameId, "mock@")
    val path = TestFileIo.fileIO.get.saveGame(gameId)

    val source = Source.fromFile(path.toFile)
    val fileContents = source.getLines.mkString
    source.close()

    val restored = decode[SaveGame](fileContents)
    Assert.assertTrue(restored.isRight)
    val value = restored.getOrElse(null)
    Assert.assertEquals(TestSize, value.game.size)
    Assert.assertTrue(value.players.nonEmpty)
    Assert.assertTrue(value.players.contains(Black))

  @Test def testExistsToGainTrustInTestsThatUseIt(): Unit =
    TestFileIo.fileIO.get.writeFile("test", "{}")
    Assert.assertTrue(IOForTests.exists("test"))
    Assert.assertFalse(IOForTests.exists("this file should not exist"))

  @Test def testGetListOfJsonFiles(): Unit =
    TestFileIo.fileIO.get.writeFile("test.json", "{}")
    Assert.assertTrue(IOForTests.exists("test.json"))
    val matchingFiles = TestFileIo.fileIO.get.getListOfFiles(".json").map(f => f.getName)
    Assert.assertEquals(
      java.io.File(TestFileIo.fileIO.get.baseFolder).listFiles.toList.toString,
      List("test.json"), matchingFiles
    )

  @Test def testGuardAgainstPathTraversal(): Unit =
    assertThrows[IllegalArgumentException](
      {TestFileIo.fileIO.get.writeFile("../test.json", "{}")}
    )
    assertThrows[IllegalArgumentException](
      {TestFileIo.fileIO.get.writeFile("/tmp/test.json", "{}")}
    )