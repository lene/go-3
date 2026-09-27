package go3d.server

import io.circe.parser._
import java.nio.file.{Files, Paths, StandardCopyOption}
import org.junit.jupiter.api.{Assertions, Test, BeforeAll}
import scala.io.Source

import go3d._
import org.scalatest.TryValues.*

object TestFileIo:
  // assigned in @BeforeAll, which JUnit runs before any test reads it
  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
  var fileIO: FileIO = scala.compiletime.uninitialized
  @BeforeAll def initIo(): Unit =
    Games.init(Files.createTempDirectory("go3d").toString)
    Games.fileIO.foreach(fileIO = _)

  // Check this suite's own folder: other suites running in parallel may re-init the shared
  // Games singleton with a different folder, so IOForTests (which uses Games.fileIO) can look
  // in the wrong place.
  def exists(filename: String): Boolean = Files.exists(Paths.get(fileIO.baseFolder, filename))

class TestFileIo:

  @Test def testFileIOFailsOnNonexistingBaseFolder(): Unit =
    Assertions.assertInstanceOf(
      classOf[IllegalArgumentException],
      FileIO("/tmp/this-folder-should-not-exist").failure.exception
    )

  @Test def testSaveGameFailsNonexistentGame(): Unit =
    val gameId = "mock"
    Assertions.assertThrows(
      classOf[NoSuchElementException], () => TestFileIo.fileIO.saveGame(gameId)
    )

  @Test def testSaveGameFailsNonexistentPlayers(): Unit =
    val gameId = Games.register(TestSize).success.value
    Assertions.assertThrows(
      classOf[RuntimeException], () => TestFileIo.fileIO.saveGame(gameId)
    )

  @Test def testSaveGameWritesFile(): Unit =
    val gameId = Games.register(TestSize).success.value
    Games.registerPlayer(gameId, Black).success.value
    TestFileIo.fileIO.saveGame(gameId)
    Assertions.assertTrue(
      Files.exists(Paths.get(TestFileIo.fileIO.baseFolder, s"$gameId.json")),
      s"$gameId in ${IOForTests.files}?"
    )

  @Test def testSaveGameContents(): Unit =
    val gameId = Games.register(TestSize).success.value
    Games.registerPlayer(gameId, Black).success.value
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
    TestFileIo.fileIO.writeFile("test.json", "{}")
    Assertions.assertTrue(TestFileIo.exists("test.json"))
    Assertions.assertFalse(TestFileIo.exists("this file should not exist"))

  @Test def testGetListOfJsonFiles(): Unit =
    val fileName = s"${IdGenerator.getId}.json"
    TestFileIo.fileIO.writeFile(fileName, "{}")
    val matchingFiles = TestFileIo.fileIO.getListOfFiles(".json").map(f => f.getName)
    Assertions.assertTrue(
      matchingFiles.contains(fileName),
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

  @Test def testDeleteGameRemovesFile(): Unit =
    val gameId = Games.register(TestSize).success.value
    Games.registerPlayer(gameId, Black).success.value
    TestFileIo.fileIO.saveGame(gameId)
    Assertions.assertTrue(TestFileIo.exists(s"$gameId.json"))
    TestFileIo.fileIO.deleteGame(gameId)
    Assertions.assertFalse(TestFileIo.exists(s"$gameId.json"))

  @Test def testDeleteGameIsIdempotent(): Unit =
    val gameId = IdGenerator.getId
    TestFileIo.fileIO.deleteGame(gameId) // should not throw

  @Test def testArchivedExistsReturnsFalseForMissingGame(): Unit =
    Assertions.assertFalse(TestFileIo.fileIO.archivedExists(IdGenerator.getId))

  @Test def testArchivedExistsReturnsTrueAfterArchive(): Unit =
    val gameId = Games.register(TestSize).success.value
    Games.registerPlayer(gameId, Black).success.value
    TestFileIo.fileIO.saveGame(gameId)
    TestFileIo.fileIO.archiveGame(gameId)
    Assertions.assertTrue(TestFileIo.fileIO.archivedExists(gameId))

  @Test def testLoadArchivedGameFailsForMissingGame(): Unit =
    Assertions.assertTrue(TestFileIo.fileIO.loadArchivedGame(IdGenerator.getId).isFailure)

  @Test def testLoadArchivedGameSucceeds(): Unit =
    val gameId = Games.register(TestSize).success.value
    Games.registerPlayer(gameId, Black).success.value
    TestFileIo.fileIO.saveGame(gameId)
    TestFileIo.fileIO.archiveGame(gameId)
    val result = TestFileIo.fileIO.loadArchivedGame(gameId)
    Assertions.assertTrue(result.isSuccess)
    Assertions.assertEquals(TestSize, result.success.value.game.size)
