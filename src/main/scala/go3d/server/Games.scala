package go3d.server

import com.typesafe.scalalogging.Logger
import io.circe.parser._
import scala.io.Source
import scala.collection.concurrent

import go3d.{Game, Color}

object Games:

  // Thread-safe map: multiple games can be updated concurrently
  // Each game uses ConcurrentState for lock-free atomic updates
  private val activeGames = concurrent.TrieMap[String, ConcurrentState[Game]]()
  private val archivedGames = concurrent.TrieMap[String, Game]()
  var fileIO: Option[FileIO] = None

  def init(baseDir: String): Unit = fileIO = Some(FileIO(baseDir))

  def checkInitialized(): Unit =
    if fileIO.isEmpty then throw new IllegalStateException("Games not initialized")

  /**
   * Get current game state (thread-safe read).
   */
  def apply(gameId: String): Game =
    if activeGames.contains(gameId) then
      activeGames(gameId).get()
    else
      archivedGames(gameId)

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

  /**
   * Register a new game with lock-free thread safety.
   */
  def register(boardSize: Int): String =
    val gameId = IdGenerator.getId
    val game = Game.start(boardSize)
    activeGames.put(gameId, new ConcurrentState(game))
    gameId

  def registerPlayer(gameId: String, color: Color): Unit =
    Players.register(gameId, color)

  /**
   * Add or update a game (thread-safe).
   * Use this for restoring games from disk or updating after moves.
   */
  def add(gameId: String, game: Game): Unit =
    activeGames.get(gameId) match
      case Some(state) => state.set(game)  // Update existing
      case None => activeGames.put(gameId, new ConcurrentState(game))  // Create new
    fileIO.foreach(_.saveGame(gameId))
    if game.isOver then archive(gameId)

  /**
   * Thread-safe atomic game update using a function.
   *
   * Automatically retries if concurrent update occurs.
   * This is the preferred way to update game state from handlers.
   *
   * @param gameId The game to update
   * @param f Function to transform old game state to new state
   * @return The new game state after successful update
   */
  def update(gameId: String)(f: Game => Game): Game =
    val newGame = activeGames(gameId).update(f)
    fileIO.foreach(_.saveGame(gameId))
    if newGame.isOver then archive(gameId)
    newGame

  def contains(gameId: String): Boolean =
    activeGames.contains(gameId) || archivedGames.contains(gameId)
  def numActiveGames: Int = activeGames.size
  def activeGameIds: Iterable[String] = activeGames.keys
  def numArchivedGames: Int = fileIO.fold(0)(_.getArchivedGames.size)
  def archivedGameIds: Iterable[String] = fileIO.get.getArchivedGames
  def isReady(gameId: String): Boolean = activeGames.contains(gameId) && Players.isReady(gameId)
    
  private def archive(gameId: String): Unit =
    // logger declared inline to avoid conflict with slf4j.Logger.ROOT_LOGGER_NAME in TestServer
    Logger(Games.getClass).info(s"Archiving $gameId")
    activeGames.get(gameId).foreach { state =>
      archivedGames.put(gameId, state.get())
      activeGames.remove(gameId)
      fileIO.foreach(_.archiveGame(gameId))
      Players.unregister(gameId)
      Tokens.unregisterGame(gameId)
    }

  private[server] def readGame(saveFile: java.io.File): SaveGame =
    val source = Source.fromFile(saveFile)
    val fileContents = source.getLines.mkString
    source.close()
    decode[SaveGame](fileContents) match
      case Right(saveGame) => saveGame
      case Left(error) => throw ReadSaveGameError(error.getMessage)

  private def restoreGame(saveGame: SaveGame): Unit =
    val gameId = saveGame.players.last._2.gameId
    Players(gameId) = saveGame.players
    add(gameId, saveGame.game)
