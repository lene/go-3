package go3d.server

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import io.circe.syntax.EncoderOps

class FileIO(val baseFolder: String):

  private val basePath: Path = Paths.get(baseFolder)
  if !Files.exists(basePath) || !Files.isDirectory(basePath)
  then throw IllegalArgumentException(s"$baseFolder not a directory")

  override def toString: String = s"FileIO($baseFolder, $archiveFolder)"

  def saveGame(gameId: String): Path =
    if !Files.exists(basePath) then Files.createDirectory(basePath)
    writeFile(s"$gameId.json", SaveGame(Games(gameId), Players(gameId)).asJson.noSpaces)

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
    if !Files.exists(sourcePath) then throw IllegalArgumentException(s"$gameId.json does not exist")
    if !Files.exists(archivePath) then Files.createDirectory(archivePath)
    val archivedPath = archivePath.resolve(sourcePath.getFileName)
    if Files.exists(archivedPath) then deleteFileIfArchivedVersionIsSame(sourcePath, archivedPath)
    else Files.move(sourcePath, archivedPath)

  private def deleteFileIfArchivedVersionIsSame(sourcePath: Path, archivedPath: Path): Unit =
    if !filesAreEqual(sourcePath, archivedPath) then
      throw IllegalArgumentException(s"$sourcePath and $archivedPath have different content")
    Files.delete(sourcePath)

  private def filesAreEqual(sourcePath: Path, targetPath: Path): Boolean =
    getFileContents(sourcePath.toString).sameElements(getFileContents(targetPath.toString))

  def getFileContents(filepath: String): Array[String] =
    val path = Paths.get(filepath)
    Files.readAllLines(path, StandardCharsets.UTF_8).toArray(Array.empty[String])

  private def guardAgainstAbsolutePath(path: String): Unit =
    val pathAsFile: File = File(path)
    if pathAsFile.isAbsolute then throw IllegalArgumentException(s"Path traversal attempt? $path")
    try
      if pathAsFile.getCanonicalPath != pathAsFile.getAbsolutePath
      then throw IllegalArgumentException(s"Path traversal attempt? $path")
    catch case e: IOException => throw IllegalArgumentException(s"Path traversal attempt? $path", e)

