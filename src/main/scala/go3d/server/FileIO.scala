package go3d.server

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import io.circe.syntax.EncoderOps

class FileIO(val baseFolder: String):

  private val basePath: Path = Paths.get(baseFolder)
  if !Files.exists(basePath) || !Files.isDirectory(basePath)
  then throw IllegalArgumentException(s"$baseFolder not a directory")

  def saveGame(gameId: String): Path =
    if !Files.exists(basePath) then Files.createDirectory(basePath)
    writeFile(s"$gameId.json", SaveGame(Games(gameId), Players(gameId)).asJson.noSpaces)

  def writeFile(saveFile: String, content: String): Path =
    guardAgainstAbsolutePath(saveFile)
    val path = Paths.get(baseFolder, saveFile)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))

  def getListOfFiles(extension: String): List[File] =
    File(baseFolder).listFiles.filter(_.getName.endsWith(extension)).toList

  def getActiveGames: List[String] =
    getListOfFiles(".json").map(_.getName).map(_.stripSuffix(".json"))

  def archivePath: Path = Paths.get(baseFolder, "archive")
  def archiveFolder: String = archivePath.toString

  def getArchivedGames: List[String] =
    File(archiveFolder).listFiles.map(_.getName).map(_.stripSuffix(".json")).toList

  def archiveGame(gameId: String): Unit =
    val file = Paths.get(baseFolder, s"$gameId.json").toFile
    if !Files.exists(archivePath) then Files.createDirectory(archivePath)
    Files.move(file.toPath, archivePath.resolve(file.getName))

  private def guardAgainstAbsolutePath(path: String): Unit =
    val pathAsFile: File = File(path)
    if pathAsFile.isAbsolute then throw IllegalArgumentException(s"Path traversal attempt? $path")
    try
      if pathAsFile.getCanonicalPath != pathAsFile.getAbsolutePath
      then throw IllegalArgumentException(s"Path traversal attempt? $path")
    catch case e: IOException => throw IllegalArgumentException(s"Path traversal attempt? $path", e)

