package go3d.server

import go3d.{Game, newGame, Black, White}
import scala.io.Source
import io.circe.parser._

var Games: Map[String, Game] = Map()

def registerGame(boardSize: Int): String =
  val gameId = IdGenerator.getId
  val game = newGame(boardSize)
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

def openGames(): Array[String] =
  Players.filter(p => p._2.contains(Black) && !p._2.contains(White)).map(_._1).toArray
