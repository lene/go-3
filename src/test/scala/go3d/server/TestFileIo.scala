package go3d.server

import go3d.*
import io.circe.parser.*
import org.junit.jupiter.api.{Assertions, Test, BeforeAll}

import scala.io.Source
import java.nio.file.{Files, Paths}

object TestFileIo:
  var fileIO: Option[FileIO] = None
  @BeforeAll def initIo(): Unit =
    Games.init(Files.createTempDirectory("go3d").toString)
    fileIO = Games.fileIO

class TestFileIo:

  @Test def testSaveGameFailsNonexistentGame(): Unit =
    val gameId = "mock"
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => TestFileIo.fileIO.get.saveGame(gameId)
    )

  @Test def testSaveGameFailsNonexistentPlayers(): Unit =
    val gameId = Games.register(TestSize)
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => TestFileIo.fileIO.get.saveGame(gameId)
    )

  @Test def testSaveGameWritesFile(): Unit =
    val gameId = Games.register(TestSize)
    Games.registerPlayer(gameId, Black, "mock@")
    TestFileIo.fileIO.get.saveGame(gameId)
    Assertions.assertTrue(
      Files.exists(Paths.get(TestFileIo.fileIO.get.baseFolder, s"$gameId.json")),
      s"$gameId in ${IOForTests.files}?"
    )

  @Test def testSaveGameContents(): Unit =
    val gameId = Games.register(TestSize)
    Games.registerPlayer(gameId, Black, "mock@")
    val path = TestFileIo.fileIO.get.saveGame(gameId)

    val source = Source.fromFile(path.toFile)
    val fileContents = source.getLines.mkString
    source.close()

    val restored = decode[SaveGame](fileContents)
    Assertions.assertTrue(restored.isRight)
    val value = restored.getOrElse(null)
    Assertions.assertEquals(TestSize, value.game.size)
    Assertions.assertTrue(value.players.nonEmpty)
    Assertions.assertTrue(value.players.contains(Black))

  @Test def testExistsToGainTrustInTestsThatUseIt(): Unit =
    TestFileIo.fileIO.get.writeFile("test", "{}")
    Assertions.assertTrue(IOForTests.exists("test"))
    Assertions.assertFalse(IOForTests.exists("this file should not exist"))

  @Test def testGetListOfJsonFiles(): Unit =
    TestFileIo.fileIO.get.writeFile("test.json", "{}")
    Assertions.assertTrue(IOForTests.exists("test.json"))
    val matchingFiles = TestFileIo.fileIO.get.getListOfFiles(".json").map(f => f.getName)
    Assertions.assertEquals(
      List("test.json"), matchingFiles,
      java.io.File(TestFileIo.fileIO.get.baseFolder).listFiles.toList.toString
    )

  @Test def testGuardAgainstPathTraversal(): Unit =
    Assertions.assertThrows(
      classOf[IllegalArgumentException],
      () => TestFileIo.fileIO.get.writeFile("../test.json", "{}")
    )
    Assertions.assertThrows(
      classOf[IllegalArgumentException],
      () => TestFileIo.fileIO.get.writeFile("/tmp/test.json", "{}")
    )