package go3d.server

import go3d.{Color, Game}

import scala.collection.mutable
import collection.convert.ImplicitConversions._
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import io.circe.syntax.EncoderOps

import java.io.File

case class SaveGame(val game: Game, val players: Map[Color, Player])

object Io:
  var baseFolder: String = null
  var basePath: Path = null

  def init(dir: String): Unit =
//    if baseFolder != null then throw IllegalArgumentException("called Io.init() twice")
    baseFolder = dir
    basePath = Paths.get(baseFolder)
    if !(Files.exists(basePath) && Files.isDirectory(basePath))
    then throw IllegalArgumentException(s"$dir not a directory")

  def saveGame(gameId: String): Path =
    if basePath == null then throw IllegalArgumentException("call Io.init() before using Io")
    if !Files.exists(basePath) then Files.createDirectory(basePath)
    writeFile(s"$gameId.json", SaveGame(Games(gameId), Players(gameId)).asJson.noSpaces)

  def writeFile(saveFile: String, content: String): Path =
    val path = Paths.get(baseFolder, saveFile)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    return path

  def exists(filename: String): Boolean =
    if basePath == null then throw IllegalArgumentException("call Io.init() before using Io")
    Files.exists(Paths.get(baseFolder, filename))

  def open(filename: String): File = Paths.get(baseFolder, filename).toFile

  def getListOfFiles(extension: String): List[File] =
    if basePath == null then throw IllegalArgumentException("call Io.init() before using Io")
    new java.io.File(baseFolder).listFiles.toList.filter(_.getName.endsWith(extension))
