package go3d.server.service

import go3d.Black
import go3d.Color
import go3d.Game
import go3d.server.DuplicateColor
import go3d.server.NonexistentGame
import go3d.server.SaveGame

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

/** A [[GameStore]] in memory, with the same conditional-write semantics as the DynamoDB one. */
class InMemoryGameStore extends GameStore:
  private case class Entry(game: Game, version: Long, completion: Option[(String, Long)])

  private val games = TrieMap[String, Entry]()
  private val players = TrieMap[(String, Color), String]()

  def createGame(gameId: String, game: Game): Try[Unit] =
    if games.putIfAbsent(gameId, Entry(game, 0L, None)).isEmpty then Success(())
    else Failure(GameExists(gameId))

  def getGame(gameId: String): Try[Option[StoredGame]] =
    Success(games.get(gameId).map(e => StoredGame(e.game, colors(gameId), e.version)))

  def updateGame(gameId: String, expectedVersion: Long, game: Game): Try[Unit] =
    games.get(gameId) match
      case None => Failure(NonexistentGame(gameId, List()))
      case Some(entry) =>
        val updated = entry.copy(game = game, version = expectedVersion + 1)
        if entry.version == expectedVersion && games.replace(gameId, entry, updated) then
          Success(())
        else Failure(ConcurrentModification(gameId, expectedVersion))

  def registerPlayer(gameId: String, color: Color, tokenHash: String): Try[Unit] =
    if players.putIfAbsent((gameId, color), tokenHash).isEmpty then Success(())
    else Failure(DuplicateColor(gameId, color))

  def playerColor(gameId: String, tokenHash: String): Try[Option[Color]] =
    Success(players.collectFirst { case ((g, color), hash) if g == gameId && hash == tokenHash =>
      color
    })

  def openGames(): Try[Array[String]] =
    Success(games.keys.filter(gameId => colors(gameId) == Set(Black)).toArray)

  def markCompleted(gameId: String, archiveKey: String, expiresAt: Long): Try[Unit] =
    games.get(gameId) match
      case None => Failure(NonexistentGame(gameId, List()))
      case Some(entry) =>
        games.update(gameId, entry.copy(completion = Some((archiveKey, expiresAt))))
        Success(())

  /** The archive key and expiry recorded by [[markCompleted]]. */
  def completion(gameId: String): Option[(String, Long)] = games.get(gameId).flatMap(_.completion)

  private def colors(gameId: String): Set[Color] =
    players.keys.collect { case (g, color) if g == gameId => color }.toSet

/** A [[GameArchive]] in memory, which can be told to fail. */
class InMemoryGameArchive(failing: Boolean) extends GameArchive:
  private val archives = TrieMap[String, SaveGame]()

  def archive(gameId: String, saveGame: SaveGame): Try[String] =
    if failing then Failure(IllegalStateException("archive unavailable"))
    else
      archives.update(gameId, saveGame)
      Success("archives/" + gameId + ".json")

  def url(gameId: String): Try[Option[String]] =
    if failing then Failure(IllegalStateException("archive unavailable"))
    else Success(archives.get(gameId).map(_ => "memory://archives/" + gameId + ".json"))

  def archived(gameId: String): Option[SaveGame] = archives.get(gameId)

/** A [[GameStore]] whose every operation fails, as when DynamoDB is unreachable. */
class FailingGameStore extends GameStore:
  private def failure[T]: Try[T] = Failure(IllegalStateException("store unavailable"))
  def createGame(gameId: String, game: Game): Try[Unit] = failure
  def getGame(gameId: String): Try[Option[StoredGame]] = failure
  def updateGame(gameId: String, expectedVersion: Long, game: Game): Try[Unit] = failure
  def registerPlayer(gameId: String, color: Color, tokenHash: String): Try[Unit] = failure
  def playerColor(gameId: String, tokenHash: String): Try[Option[Color]] = failure
  def openGames(): Try[Array[String]] = failure
  def markCompleted(gameId: String, archiveKey: String, expiresAt: Long): Try[Unit] = failure
