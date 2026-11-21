package go3d.server

import go3d.Color
import go3d.Game
import go3d.Position

import scala.util.hashing.MurmurHash3

class GoResponse
case class ErrorResponse(err: String) extends GoResponse
case class GameCreatedResponse(id: String, size: Int) extends GoResponse
case class PlayerRegisteredResponse(
    game: Game,
    color: Color,
    authToken: String,
    ready: Boolean,
    debug: RequestInfo
) extends GoResponse

/** `over` and `winner` come from the game, so a response cannot say that a game is over, or who
  * won it, differently from the game it carries.
  */
case class StatusResponse(
    game: Game,
    moves: List[Position],
    ready: Boolean,
    playerColor: Option[Color],
    debug: RequestInfo
) extends GoResponse:
  def over: Boolean = game.isOver
  def winner: Option[Color] = game.winner

case class GameOverResponse(game: Game, debug: RequestInfo) extends GoResponse

/** A game waiting for a white player, with its komi so that a player sees it before joining. */
case class OpenGame(id: String, komi: Double)

/** The games waiting for a white player. `ids` comes from `games`, so the two cannot differ.
  *
  * Equality compares the games element by element, which a case class does not do for an array.
  */
case class OpenGamesResponse(games: Array[OpenGame]) extends GoResponse:
  def ids: Array[String] = games.map(_.id)

  override def equals(obj: Any): Boolean =
    obj match
      case other: OpenGamesResponse => games.sameElements(other.games)
      case _ => false

  override def hashCode: Int = MurmurHash3.arrayHash(games)
