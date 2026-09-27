package go3d.server.service

import go3d.Color
import go3d.Game
import go3d.server.ServerException

import scala.util.Try

/** A stored game with its registered player colors and the version its next update must match. */
case class StoredGame(game: Game, players: Set[Color], version: Long)

class GameExists(val gameId: String) extends ServerException("game " + gameId + " already exists")

/** Another request changed the game since it was read; the client should refetch and retry. */
class ConcurrentModification(val gameId: String, expectedVersion: Long)
  extends ServerException(
    "game " + gameId + " changed concurrently, expected version " + expectedVersion.toString
  )

/**
 * Authoritative storage for games and player registrations.
 *
 * Every write reports failure through its `Try`; implementations must never log and swallow
 * errors, because the store is the only copy of the game state.
 */
trait GameStore:
  /** Stores a new game at version 0; fails with [[GameExists]] if the id is taken. */
  def createGame(gameId: String, game: Game): Try[Unit]

  def getGame(gameId: String): Try[Option[StoredGame]]

  /**
   * Replaces the game if its stored version is still `expectedVersion`, then increments the
   * version; fails with [[ConcurrentModification]] otherwise.
   */
  def updateGame(gameId: String, expectedVersion: Long, game: Game): Try[Unit]

  /** Fails with [[go3d.server.DuplicateColor]] if `color` is already registered. */
  def registerPlayer(gameId: String, color: Color, tokenHash: String): Try[Unit]

  /** The color registered in `gameId` with the token whose hash is `tokenHash`. */
  def playerColor(gameId: String, tokenHash: String): Try[Option[Color]]

  /** Games with a black player waiting for white. */
  def openGames(): Try[Array[String]]

  /**
   * Records that the finished game is archived under `archiveKey`; the game and its players
   * may be deleted after `expiresAt` (Unix seconds).
   */
  def markCompleted(gameId: String, archiveKey: String, expiresAt: Long): Try[Unit]

/** Long-term storage for finished games. */
trait GameArchive:
  /** Stores and verifies the archive of a finished game and returns its key. */
  def archive(gameId: String, saveGame: go3d.server.SaveGame): Try[String]
