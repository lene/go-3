package go3d.server

import go3d.{Game, newGame, Black, White}
import scala.io.Source
import scala.collection.mutable
import io.circe.parser._
import com.typesafe.scalalogging.Logger


object Games:
  private val activeGames: mutable.Map[String, Game] = mutable.Map()
  private val archivedGames: mutable.Map[String, Game] = mutable.Map()

  def apply(gameId: String): Game =
    if activeGames contains gameId then activeGames(gameId) else archivedGames(gameId)

  def register(boardSize: Int): String =
    val gameId = IdGenerator.getId
    val game = newGame(boardSize)
    activeGames += (gameId -> game)
    gameId

  def add(gameId: String, game: Game): Unit =
    activeGames += (gameId -> game)
    Io.saveGame(gameId)
    if game.isOver then archive(gameId)

  def contains(gameId: String): Boolean = activeGames.contains(gameId) || archivedGames.contains(gameId)
  def numActiveGames: Int = activeGames.size
  def activeGameIds: Iterable[String] = activeGames.keys
  def numArchivedGames: Int = archivedGames.size

  private def archive(gameId: String): Unit =
    // logger declared inline to avoid conflict with slf4j.Logger.ROOT_LOGGER_NAME in TestServer
    Logger(Games.getClass).info(s"Archiving $gameId")
    archivedGames += (gameId -> activeGames(gameId))
    activeGames -= gameId
    Io.archiveGame(gameId)

def readGame(saveFile: java.io.File): SaveGame =
  val source = Source.fromFile(saveFile)
  val fileContents = source.getLines.mkString
  source.close()
  val result = decode[SaveGame](fileContents)
  if result.isLeft then throw ReadSaveGameError(result.left.getOrElse(null).getMessage)
  result.getOrElse(null)

def restoreGame(saveGame: SaveGame): Unit =
  val gameId = saveGame.players.last._2.gameId
  Players(gameId) = saveGame.players
  Games.add(gameId, saveGame.game)

def openGames(): Array[String] =
  Players.filter(p => p._2.contains(Black) && !p._2.contains(White)).keys.toArray
