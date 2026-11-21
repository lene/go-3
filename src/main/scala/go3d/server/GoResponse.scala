package go3d.server

import go3d.Color
import go3d.Game
import go3d.Position

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

case class OpenGamesResponse(ids: Array[String]) extends GoResponse
