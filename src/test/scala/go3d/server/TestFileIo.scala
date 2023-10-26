package go3d.server

import io.circe.parser._
import java.nio.file.{Files, Paths, StandardCopyOption}
import org.junit.jupiter.api.{Assertions, Test, BeforeAll}
import scala.io.Source

import go3d._

object TestFileIo:
  var fileIO: Option[FileIO] = None
  @BeforeAll def initIo(): Unit =
    Games.init(Files.createTempDirectory("go3d").toString)
    fileIO = Games.fileIO

class TestFileIo:

  @Test def testFileIOFailsOnNonexistingBaseFolder(): Unit =
    Assertions.assertThrows(
      classOf[IllegalArgumentException], () => FileIO("/tmp/this-folder-should-not-exist")
    )

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
    TestFileIo.fileIO.get.writeFile("test.json", "{}")
    Assertions.assertTrue(IOForTests.exists("test.json"))
    Assertions.assertFalse(IOForTests.exists("this file should not exist"))

  @Test def testGetListOfJsonFiles(): Unit =
    TestFileIo.fileIO.get.writeFile("test.json", "{}")
    Assertions.assertTrue(IOForTests.exists("test.json"))
    val matchingFiles = TestFileIo.fileIO.get.getListOfFiles(".json").map(f => f.getName)
    Assertions.assertTrue(
      matchingFiles.contains("test.json"),
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

  @Test def testGetFileContents(): Unit =
    val randomContent = IdGenerator.getId
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.get.writeFile(s"$gameId.json", s"{$randomContent}")
    val file = Paths.get(TestFileIo.fileIO.get.baseFolder, s"$gameId.json").toString
    val writtenContent = TestFileIo.fileIO.get.getFileContents(file)
    Assertions.assertEquals(1, writtenContent.length)
    Assertions.assertEquals(s"{$randomContent}", writtenContent(0))

  @Test def testArchivedFileIsFineIfExistsWithSameContent(): Unit =
    val randomContent = IdGenerator.getId
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.get.writeFile(s"$gameId.json", s"{$randomContent}")
    val originalPath = Paths.get(TestFileIo.fileIO.get.baseFolder, s"$gameId.json")
    val archivePath = Paths.get(TestFileIo.fileIO.get.baseFolder, "archived")
    val archivedPath = Paths.get(archivePath.toString, s"$gameId.json")
    if !Files.exists(archivePath) then Files.createDirectory(archivePath)
    Files.copy(originalPath, archivedPath, StandardCopyOption.REPLACE_EXISTING)
    TestFileIo.fileIO.get.archiveGame(gameId)

  @Test def testArchivedFileRemovesOriginalFileIfExistsWithSameContent(): Unit =
    val randomContent = IdGenerator.getId
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.get.writeFile(s"$gameId.json", s"{$randomContent}")
    val originalPath = Paths.get(TestFileIo.fileIO.get.baseFolder, s"$gameId.json")
    val archivePath = Paths.get(TestFileIo.fileIO.get.baseFolder, "archived")
    val archivedPath = Paths.get(archivePath.toString, s"$gameId.json")
    if !Files.exists(archivePath) then Files.createDirectory(archivePath)
    Files.copy(originalPath, archivedPath)
    TestFileIo.fileIO.get.archiveGame(gameId)
    Assertions.assertFalse(Files.exists(originalPath))

  @Test def testArchivedFileThrowsExceptionIfExistsWithDifferentContent(): Unit =
    val randomContent = IdGenerator.getId
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.get.writeFile(s"$gameId.json", s"{$randomContent}")
    TestFileIo.fileIO.get.archiveGame(gameId)
    val differentRandomContent = IdGenerator.getId
    TestFileIo.fileIO.get.writeFile(s"$gameId.json", s"{$differentRandomContent}")
    Assertions.assertThrows(
      classOf[IllegalArgumentException], () => TestFileIo.fileIO.get.archiveGame(gameId)
    )
