package go3d.server

import scala.io.Source
import io.circe.parser._

var Games: Map[String, go3d.Game] = Map()

def registerGame(boardSize: Int): String =
  val gameId = IdGenerator.getId
  val game = go3d.newGame(boardSize)
  Games = Games + (gameId -> game)
  return gameId

def readGame(saveFile: java.io.File): SaveGame =
  val source = Source.fromFile(saveFile)
  val fileContents = source.getLines.mkString
  source.close()
  val result = decode[SaveGame](fileContents)
  if result.isLeft then throw ReadSaveGameError(result.left.getOrElse(null).getMessage)
  return result.getOrElse(null)

def restoreGame(saveGame: SaveGame): Unit =
  val gameId = saveGame.players.last._2.gameId
  Players(gameId) = saveGame.players
  Games = Games + (gameId -> saveGame.game)
