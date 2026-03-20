package go3d.server

import io.circe.syntax.EncoderOps

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.util.{Failure, Success, Try}

object FileIO:
  def apply(baseFolder: String): Try[FileIO] =
    val basePath = Paths.get(baseFolder)
    if !Files.exists(basePath) || !Files.isDirectory(basePath)
    then Failure(IllegalArgumentException(s"$baseFolder not a directory"))
    else Success(new FileIO(baseFolder))

class FileIO private(val baseFolder: String):

  private val basePath: Path = Paths.get(baseFolder)

  override def toString: String = s"FileIO($baseFolder, $archiveFolder)"

  def saveGame(gameId: String): Path =
    if !Files.exists(basePath) then Files.createDirectory(basePath)
    val game = Games(gameId)  // Will throw NoSuchElementException if game doesn't exist
    val players = Players(gameId)
    if players.isEmpty then
      sys.error(s"Cannot save game $gameId with no players")
    writeFile(s"$gameId.json", SaveGame(game, players).asJson.noSpaces)

  def writeFile(saveFile: String, content: String): Path =
    guardAgainstAbsolutePath(saveFile)
    val path = Paths.get(baseFolder, saveFile)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))

  def getListOfFiles(extension: String): List[File] = getListOfFiles(baseFolder, extension)
  def getListOfFiles(dir: String, extension: String): List[File] =
    File(dir).listFiles.filter(_.getName.endsWith(extension)).toList

  def getActiveGames: List[String] =
    getListOfFiles(".json").map(_.getName).map(_.stripSuffix(".json"))

  def archivePath: Path = Paths.get(baseFolder, "archive")
  def archiveFolder: String = archivePath.toString

  def getArchivedGames: List[String] =
    if !Files.exists(archivePath) then List()
    else File(archiveFolder).listFiles().map(_.getName).map(_.stripSuffix(".json")).toList

  def archiveGame(gameId: String): Unit =
    val sourcePath = Paths.get(baseFolder, s"$gameId.json")
    if !Files.exists(sourcePath) then sys.error(s"$gameId.json does not exist")
    if !Files.exists(archivePath) then Files.createDirectory(archivePath)
    val archivedPath = archivePath.resolve(sourcePath.getFileName)
    if Files.exists(archivedPath) then deleteFileIfArchivedVersionIsSame(sourcePath, archivedPath)
    else Files.move(sourcePath, archivedPath)

  private def deleteFileIfArchivedVersionIsSame(sourcePath: Path, archivedPath: Path): Unit =
    if !filesAreEqual(sourcePath, archivedPath) then
      sys.error(s"$sourcePath and $archivedPath have different content")
    Files.delete(sourcePath)

  private def filesAreEqual(sourcePath: Path, targetPath: Path): Boolean =
    getFileContents(sourcePath.toString).sameElements(getFileContents(targetPath.toString))

  def getFileContents(filepath: String): Array[String] =
    val path = Paths.get(filepath)
    Files.readAllLines(path, StandardCharsets.UTF_8).toArray(Array.empty[String])

  private def guardAgainstAbsolutePath(path: String): Unit =
    val pathAsFile: File = File(path)
    if pathAsFile.isAbsolute then sys.error(s"Path traversal attempt? $path")
    Try(pathAsFile.getCanonicalPath).fold(
      _ => sys.error(s"Path traversal attempt? $path"),
      canonicalPath =>
        if canonicalPath != pathAsFile.getAbsolutePath
        then sys.error(s"Path traversal attempt? $path")
    )
