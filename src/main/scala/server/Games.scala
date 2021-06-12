package go3d.server

import go3d.Color

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

def getGameId(pathInfo: String): String =
  if pathInfo == null || pathInfo.isEmpty then throw MalformedRequest(pathInfo)
  val parts = pathInfo.stripPrefix("/").split('/')
  if parts.isEmpty then throw MalformedRequest(pathInfo)
  val gameId = parts(0)
  if !(Games contains gameId) then throw NonexistentGame(gameId, Games.keys.toList)
  return gameId

def getColor(pathInfo: String): go3d.Color =
  if pathInfo == null || pathInfo.isEmpty then throw MalformedRequest(pathInfo)
  val parts = pathInfo.stripPrefix("/").split('/')
  if parts.length < 2 then throw MalformedRequest(pathInfo)
  val color = Color(parts(1)(0))
  val gameId = getGameId(pathInfo)
  if Players.contains(gameId) && Players(gameId).contains(color) then
    throw DuplicateColor(gameId, color)
  return color
