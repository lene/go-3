package go3d.server

import go3d.Black
import go3d.Color
import go3d.White

import scala.collection.concurrent
import scala.util.{Failure, Success, Try}

object Players:

  // Thread-safe map: each game's player map is independently updatable
  // Using ConcurrentState for lock-free atomic updates
  private val activePlayers = concurrent.TrieMap[String, ConcurrentState[Map[Color, Player]]]()

  def apply(gameId: String): Map[Color, Player] =
    activePlayers.get(gameId).map(_.get()).getOrElse(Map.empty)

  def get(gameId: String): Option[Map[Color, Player]] =
    activePlayers.get(gameId).map(_.get())

  def update(gameId: String, players: Map[Color, Player]): Unit =
    activePlayers.get(gameId) match
      case Some(state) => state.set(players)
      case None => activePlayers.put(gameId, new ConcurrentState(players))

  def openGames(): Array[String] =
    activePlayers.filter { case (_, state) =>
      val players = state.get()
      players.contains(Black) && !players.contains(White)
    }.keys.toArray

  def isDuplicate(gameId: String, color: Color): Boolean =
    activePlayers.get(gameId).exists(_.get().contains(color))

  def isReady(gameId: String): Boolean =
    activePlayers.get(gameId).exists(_.get().size == 2)

  /**
   * Register a player for a game (thread-safe).
   */
  def register(gameId: String, color: Color): Try[Unit] =
    val state = activePlayers.getOrElseUpdate(
      gameId,
      new ConcurrentState(Map.empty[Color, Player])
    )
    state.update { players =>
      if players.contains(color) then Failure(DuplicateColor(gameId, color))
      else Success(players + (color -> Player(color, gameId)))
    }.map(_ => ())

  def unregister(gameId: String): Unit = activePlayers.remove(gameId)
