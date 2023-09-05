package go3d.server

import go3d.{Color, Game}

import scala.collection.mutable
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import io.circe.syntax.EncoderOps

import java.io.File
import java.io.IOException

case class SaveGame(game: Game, players: Map[Color, Player])

object Io:
  var baseFolder: String = null
  var basePath: Path = null

  def init(dir: String): Unit =
    baseFolder = dir
    basePath = Paths.get(baseFolder)
    if !(Files.exists(basePath) && Files.isDirectory(basePath))
    then throw IllegalArgumentException(s"$dir not a directory")

  def saveGame(gameId: String): Path =
    if basePath == null then throw IllegalArgumentException("call Io.init() before using Io")
    if !Files.exists(basePath) then Files.createDirectory(basePath)
    writeFile(s"$gameId.json", SaveGame(Games(gameId), Players(gameId)).asJson.noSpaces)

  def writeFile(saveFile: String, content: String): Path =
    guardAgainstAbsolutePath(saveFile)
    val path = Paths.get(baseFolder, saveFile)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path

  def getListOfFiles(extension: String): List[File] =
    if basePath == null then throw IllegalArgumentException("call Io.init() before using Io")
    new java.io.File(baseFolder).listFiles.toList.filter(_.getName.endsWith(extension))

  private def guardAgainstAbsolutePath(path: String): Unit =
    val pathAsFile: File = File(path)
    if pathAsFile.isAbsolute
    then throw IllegalArgumentException(s"Path traversal attempt? $path")
    try
      if pathAsFile.getCanonicalPath != pathAsFile.getAbsolutePath
      then throw IllegalArgumentException(s"Path traversal attempt? $path")
    catch case e: IOException => throw IllegalArgumentException(s"Path traversal attempt? $path", e)
