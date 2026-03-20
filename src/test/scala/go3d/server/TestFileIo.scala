package go3d.server

import io.circe.parser._
import java.nio.file.{Files, Paths, StandardCopyOption}
import org.junit.jupiter.api.{Assertions, Test, BeforeAll}
import scala.io.Source

import go3d._

object TestFileIo:
  var fileIO: FileIO = scala.compiletime.uninitialized
  @BeforeAll def initIo(): Unit =
    Games.init(Files.createTempDirectory("go3d").toString)
    Games.fileIO.foreach(fileIO = _)

class TestFileIo:

  @Test def testFileIOFailsOnNonexistingBaseFolder(): Unit =
    Assertions.assertInstanceOf(
      classOf[IllegalArgumentException], FileIO("/tmp/this-folder-should-not-exist").failed.get
    )

  @Test def testSaveGameFailsNonexistentGame(): Unit =
    val gameId = "mock"
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => TestFileIo.fileIO.saveGame(gameId)
    )

  @Test def testSaveGameFailsNonexistentPlayers(): Unit =
    val gameId = Games.register(TestSize).get
    Assertions.assertThrows(
      classOf[RuntimeException], () => TestFileIo.fileIO.saveGame(gameId)
    )

  @Test def testSaveGameWritesFile(): Unit =
    val gameId = Games.register(TestSize).get
    Games.registerPlayer(gameId, Black).get
    TestFileIo.fileIO.saveGame(gameId)
    Assertions.assertTrue(
      Files.exists(Paths.get(TestFileIo.fileIO.baseFolder, s"$gameId.json")),
      s"$gameId in ${IOForTests.files}?"
    )

  @Test def testSaveGameContents(): Unit =
    val gameId = Games.register(TestSize).get
    Games.registerPlayer(gameId, Black).get
    val path = TestFileIo.fileIO.saveGame(gameId)

    val source = Source.fromFile(path.toFile)
    val fileContents = source.getLines.mkString
    source.close()

    val restored = decode[SaveGame](fileContents)
    Assertions.assertTrue(restored.isRight)
    restored match
      case Right(value) =>
        Assertions.assertEquals(TestSize, value.game.size)
        Assertions.assertTrue(value.players.nonEmpty)
        Assertions.assertTrue(value.players.contains(Black))
      case Left(e) => Assertions.fail(e.getMessage)

  @Test def testExistsToGainTrustInTestsThatUseIt(): Unit =
    Games.fileIO.foreach(_.writeFile("test.json", "{}"))
    Assertions.assertTrue(IOForTests.exists("test.json"))
    Assertions.assertFalse(IOForTests.exists("this file should not exist"))

  @Test def testGetListOfJsonFiles(): Unit =
    val matchingFiles = TestFileIo.fileIO.getListOfFiles(".json").map(f => f.getName)
    Assertions.assertTrue(
      matchingFiles.contains("test.json"),
      java.io.File(TestFileIo.fileIO.baseFolder).listFiles.toList.toString
    )

  @Test def testGuardAgainstPathTraversal(): Unit =
    Assertions.assertThrows(
      classOf[RuntimeException],
      () => TestFileIo.fileIO.writeFile("../test.json", "{}")
    )
    Assertions.assertThrows(
      classOf[RuntimeException],
      () => TestFileIo.fileIO.writeFile("/tmp/test.json", "{}")
    )

  @Test def testGetFileContents(): Unit =
    val randomContent = IdGenerator.getId
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.writeFile(s"$gameId.json", s"{$randomContent}")
    val file = Paths.get(TestFileIo.fileIO.baseFolder, s"$gameId.json").toString
    val writtenContent = TestFileIo.fileIO.getFileContents(file)
    Assertions.assertEquals(1, writtenContent.length)
    Assertions.assertEquals(s"{$randomContent}", writtenContent(0))

  @Test def testArchivedFileIsFineIfExistsWithSameContent(): Unit =
    val randomContent = IdGenerator.getId
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.writeFile(s"$gameId.json", s"{$randomContent}")
    val originalPath = Paths.get(TestFileIo.fileIO.baseFolder, s"$gameId.json")
    val archivePath = Paths.get(TestFileIo.fileIO.baseFolder, "archived")
    val archivedPath = Paths.get(archivePath.toString, s"$gameId.json")
    if !Files.exists(archivePath) then Files.createDirectory(archivePath)
    Files.copy(originalPath, archivedPath, StandardCopyOption.REPLACE_EXISTING)
    TestFileIo.fileIO.archiveGame(gameId)

  @Test def testArchivedFileRemovesOriginalFileIfExistsWithSameContent(): Unit =
    val randomContent = IdGenerator.getId
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.writeFile(s"$gameId.json", s"{$randomContent}")
    val originalPath = Paths.get(TestFileIo.fileIO.baseFolder, s"$gameId.json")
    val archivePath = Paths.get(TestFileIo.fileIO.baseFolder, "archived")
    val archivedPath = Paths.get(archivePath.toString, s"$gameId.json")
    if !Files.exists(archivePath) then Files.createDirectory(archivePath)
    Files.copy(originalPath, archivedPath)
    TestFileIo.fileIO.archiveGame(gameId)
    Assertions.assertFalse(Files.exists(originalPath))

  @Test def testArchivedFileThrowsExceptionIfExistsWithDifferentContent(): Unit =
    val randomContent = IdGenerator.getId
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.writeFile(s"$gameId.json", s"{$randomContent}")
    TestFileIo.fileIO.archiveGame(gameId)
    val differentRandomContent = IdGenerator.getId
    TestFileIo.fileIO.writeFile(s"$gameId.json", s"{$differentRandomContent}")
    Assertions.assertThrows(
      classOf[RuntimeException], () => TestFileIo.fileIO.archiveGame(gameId)
    )
