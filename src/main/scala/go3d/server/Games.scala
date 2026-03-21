package go3d.server

import com.typesafe.scalalogging.Logger
import go3d.Color
import go3d.Game
import io.circe.parser._

import java.util.NoSuchElementException
import scala.collection.concurrent
import scala.io.Source
import scala.util.{Failure, Success, Try}

object Games:

  // Thread-safe map: multiple games can be updated concurrently
  // Each game uses ConcurrentState for lock-free atomic updates
  private val activeGames = concurrent.TrieMap[String, ConcurrentState[Game]]()
  private val lastActivity = concurrent.TrieMap[String, Long]()
  var fileIO: Option[FileIO] = None

  def init(baseDir: String): Try[Unit] =
    FileIO(baseDir).map(io => fileIO = Some(io))

  def checkInitialized(): Try[Unit] =
    if fileIO.isEmpty then Failure(new IllegalStateException("Games not initialized"))
    else Success(())

  /**
   * Get current game state (thread-safe read).
   * Active games are served from memory; archived games are lazy-loaded from disk.
   */
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def apply(gameId: String): Game =
    activeGames.get(gameId).map(_.get()).orElse(
      fileIO.flatMap(io => io.loadArchivedGame(gameId).toOption).map(_.game)
    ).getOrElse(throw NoSuchElementException(gameId))

  def loadGames(baseDir: String): Unit =
    // logger declared locally to avoid conflict with slf4j.Logger.ROOT_LOGGER_NAME in TestServer
    val logger = Logger(Games.getClass)
    init(baseDir)
    for saveFile <- fileIO.fold(List())(_.getListOfFiles(".json").sorted) do
      readGame(saveFile) match
        case Success(saveGame) =>
          restoreGame(saveGame)
          logger.debug(s"Loaded ${saveFile.getName}")
        case Failure(e: ReadSaveGameError) => logger.warn(s"${saveFile.getName}: ${e.message}")
        case Failure(e: JsonDecodeError) => logger.warn(s"${saveFile.getName}: ${e.message}")
        case Failure(e) => logger.warn(s"${saveFile.getName}: ${e.getMessage}")
    logger.info(s"${Games.numActiveGames} active games loaded, ${Games.numArchivedGames} archived")

  /**
   * Register a new game with lock-free thread safety.
   */
  def register(boardSize: Int): Try[String] =
    go3d.Game.start(boardSize).map { game =>
      val gameId = IdGenerator.getId
      activeGames.put(gameId, new ConcurrentState(game))
      lastActivity(gameId) = System.currentTimeMillis()
      gameId
    }

  def registerPlayer(gameId: String, color: Color): Try[Unit] =
    Players.register(gameId, color)

  /**
   * Add or update a game (thread-safe).
   * Use this for restoring games from disk or updating after moves.
   */
  def add(gameId: String, game: Game): Unit =
    activeGames.get(gameId) match
      case Some(state) => state.set(game)  // Update existing
      case None => activeGames.put(gameId, new ConcurrentState(game))  // Create new
    lastActivity(gameId) = System.currentTimeMillis()
    fileIO.foreach(_.saveGame(gameId))
    if game.isOver then archive(gameId)

  /**
   * Thread-safe atomic game update using a Try-returning function.
   *
   * Automatically retries if concurrent update occurs.
   * If f returns Failure, propagates immediately without modifying state.
   *
   * @param gameId The game to update
   * @param f Function to transform old game state to Try of new state
   * @return The new game state after successful update, or Failure
   */
  def update(gameId: String)(f: Game => Try[Game]): Try[Game] =
    activeGames(gameId).update(f).map { newGame =>
      lastActivity(gameId) = System.currentTimeMillis()
      fileIO.foreach(_.saveGame(gameId))
      if newGame.isOver then archive(gameId)
      newGame
    }

  def contains(gameId: String): Boolean =
    activeGames.contains(gameId) || fileIO.exists(_.archivedExists(gameId))
  def numActiveGames: Int = activeGames.size
  def activeGameIds: Iterable[String] = activeGames.keys
  def numArchivedGames: Int = fileIO.fold(0)(_.getArchivedGames.size)
  def archivedGameIds: Iterable[String] = fileIO.fold(Iterable.empty[String])(_.getArchivedGames)
  def isReady(gameId: String): Boolean = activeGames.contains(gameId) && Players.isReady(gameId)

  /**
   * Remove an active game entirely (no archive). Used for games with too few moves to be worth
   * keeping. Cleans up all in-memory and on-disk state.
   */
  def deleteGame(gameId: String): Unit =
    activeGames.remove(gameId)
    lastActivity.remove(gameId)
    fileIO.foreach(_.deleteGame(gameId))
    Players.unregister(gameId)
    Tokens.unregisterGame(gameId)

  /**
   * Expire games that have had no activity for longer than the given thresholds.
   *
   * Games with 0 moves are deleted after shortInactiveMs (they were never played).
   * Games with moves are archived after inactiveMs.
   */
  def expireStaleGames(inactiveMs: Long, shortInactiveMs: Long = -1): Unit =
    val zeroMoveMs = if shortInactiveMs >= 0 then shortInactiveMs else inactiveMs / 12
    val now = System.currentTimeMillis()
    activeGames.keys.foreach { gameId =>
      val activity = lastActivity.getOrElse(gameId, now)
      val elapsed = now - activity
      val game = activeGames(gameId).get()
      if game.moves.isEmpty && elapsed >= zeroMoveMs then
        Logger(Games.getClass).info(s"Expiring zero-move game $gameId after ${elapsed}ms")
        deleteGame(gameId)
      else if game.moves.nonEmpty && elapsed >= inactiveMs then
        Logger(Games.getClass).info(s"Archiving stale game $gameId after ${elapsed}ms")
        archive(gameId)
    }

  private def archive(gameId: String): Unit =
    // logger declared inline to avoid conflict with slf4j.Logger.ROOT_LOGGER_NAME in TestServer
    Logger(Games.getClass).info(s"Archiving $gameId")
    activeGames.get(gameId).foreach { _ =>
      activeGames.remove(gameId)
      lastActivity.remove(gameId)
      fileIO.foreach(io => Try(io.archiveGame(gameId)).failed.foreach(e =>
        Logger(Games.getClass).warn(s"Failed to archive $gameId on disk: ${e.getMessage}")
      ))
      Players.unregister(gameId)
      Tokens.unregisterGame(gameId)
    }

  private[server] def readGame(saveFile: java.io.File): Try[SaveGame] =
    val source = Source.fromFile(saveFile)
    val fileContents = source.getLines.mkString
    source.close()
    decode[SaveGame](fileContents) match
      case Right(saveGame) => Success(saveGame)
      case Left(error) => Failure(ReadSaveGameError(error.getMessage))

  private def restoreGame(saveGame: SaveGame): Unit =
    val gameId = saveGame.players.last._2.gameId
    Players(gameId) = saveGame.players
    add(gameId, saveGame.game)
