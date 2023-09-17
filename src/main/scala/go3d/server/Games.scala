package go3d.server

import go3d.{Game, Color}
import scala.io.Source
import scala.collection.mutable
import io.circe.parser._
import com.typesafe.scalalogging.Logger


object Games:

  private val activeGames: mutable.Map[String, Game] = mutable.Map()
  private val archivedGames: mutable.Map[String, Game] = mutable.Map()
  var fileIO: Option[FileIO] = None

  def init(baseDir: String): Unit =
    fileIO = Some(FileIO(baseDir))

  def checkInitialized(): Unit =
    if fileIO.isEmpty then throw new IllegalStateException("Games not initialized")

  def apply(gameId: String): Game =
    if activeGames contains gameId then activeGames(gameId) else archivedGames(gameId)

  def loadGames(baseDir: String): Unit =
    // logger declared locally to avoid conflict with slf4j.Logger.ROOT_LOGGER_NAME in TestServer
    val logger = Logger(Games.getClass)
    init(baseDir)
    for saveFile <- fileIO.fold(List())(_.getListOfFiles(".json").sorted) do
      try
        restoreGame(readGame(saveFile))
        logger.debug(s"Loaded ${saveFile.getName}")
      catch
        case e: ReadSaveGameError => logger.warn(s"${saveFile.getName}: ${e.message}")
        case e: JsonDecodeError => logger.warn(s"${saveFile.getName}: ${e.message}")
    logger.info(s"${Games.numActiveGames} active games loaded, ${Games.numArchivedGames} archived")

  def register(boardSize: Int): String =
    val gameId = IdGenerator.getId
    val game = Game.start(boardSize)
    activeGames += (gameId -> game)
    gameId

  def registerPlayer(gameId: String, color: Color, token: String): Unit =
    Players.register(gameId, color, token)

  def add(gameId: String, game: Game): Unit =
    activeGames += (gameId -> game)
    fileIO.foreach(_.saveGame(gameId))
    if game.isOver then archive(gameId)

  def contains(gameId: String): Boolean =
    activeGames.contains(gameId) || archivedGames.contains(gameId)
  def numActiveGames: Int = activeGames.size
  def activeGameIds: Iterable[String] = activeGames.keys
  def numArchivedGames: Int = archivedGames.size

  def isReady(gameId: String): Boolean =
    activeGames.contains(gameId) && Players.isReady(gameId)
    
  private def archive(gameId: String): Unit =
    // logger declared inline to avoid conflict with slf4j.Logger.ROOT_LOGGER_NAME in TestServer
    Logger(Games.getClass).info(s"Archiving $gameId")
    archivedGames += (gameId -> activeGames(gameId))
    activeGames -= gameId
    fileIO.foreach(_.archiveGame(gameId))
    Players.unregister(gameId)

  private[server] def readGame(saveFile: java.io.File): SaveGame =
    val source = Source.fromFile(saveFile)
    val fileContents = source.getLines.mkString
    source.close()
    val result = decode[SaveGame](fileContents)
    if result.isLeft then throw ReadSaveGameError(result.left.getOrElse(null).getMessage)
    result.getOrElse(null)

  private def restoreGame(saveGame: SaveGame): Unit =
    val gameId = saveGame.players.last._2.gameId
    Players(gameId) = saveGame.players
    add(gameId, saveGame.game)
