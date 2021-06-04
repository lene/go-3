package go3d.server

import go3d.{Color, Game}

import scala.collection.mutable
import java.nio.file.{Path, Paths, Files}
import java.nio.charset.StandardCharsets

case class SaveGame(val game: Game, val players: Map[Color, Player])

class Io(baseFolder: String):
  val basePath = Paths.get(baseFolder)
  def saveGame(gameId: String): Path =
    if !Files.exists(basePath) then Files.createDirectory(basePath)
    val savepath = Paths.get(baseFolder, s"$gameId.json")
    writeFile(savepath, Jsonify.toJson(SaveGame(Games(gameId), Players(gameId))))
    return savepath


  def writeFile(saveFile: Path, content: String): Unit =
    Files.write(saveFile, content.getBytes(StandardCharsets.UTF_8))

  def exists(filename: String): Boolean = Files.exists(Paths.get(baseFolder, filename))