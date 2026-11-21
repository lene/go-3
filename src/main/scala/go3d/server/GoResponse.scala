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

/** `over` comes from the game, and `winner` is present if and only if the game is over, so a
  * response cannot say that a game is over differently from the game it carries, nor name the
  * winner of a game that is not over. Only the two factories of the companion build one.
  */
case class StatusResponse private (
    game: Game,
    moves: List[Position],
    ready: Boolean,
    playerColor: Option[Color],
    debug: RequestInfo,
    winner: Option[Color]
) extends GoResponse:
  def over: Boolean = game.isOver

object StatusResponse:
  /** The status a server answers, with the winner that its rules give, `game.winner`. */
  def apply(
      game: Game,
      moves: List[Position],
      ready: Boolean,
      playerColor: Option[Color],
      debug: RequestInfo
  ): StatusResponse =
    new StatusResponse(game, moves, ready, playerColor, debug, game.winner)

  /** The status a client reads from a document with its `over` and `winner`. `over` must be that
    * of the game, and a `winner` is accepted only when the game is over. The winner is the
    * server's and is not compared with the client's own scoring, so a client keeps working when
    * the server scores differently. A document of a finished game without a winner comes from a
    * server from before the winner existed and gets the winner of the game.
    */
  def fromDocument(
      game: Game,
      moves: List[Position],
      ready: Boolean,
      playerColor: Option[Color],
      debug: RequestInfo,
      over: Boolean,
      winner: Option[Color]
  ): Either[StatusConflict, StatusResponse] =
    if over != game.isOver then Left(StatusConflict.OverDiffersFromGame)
    else if !over && winner.isDefined then Left(StatusConflict.WinnerWhileNotOver)
    else
      Right(new StatusResponse(game, moves, ready, playerColor, debug, winner.orElse(game.winner)))

/** How a status document contradicts its game, and the `field` of the document at fault. */
enum StatusConflict(val field: String, val message: String):
  case OverDiffersFromGame extends StatusConflict("over", "over differs from the game")
  case WinnerWhileNotOver extends StatusConflict("winner", "winner while the game is not over")

case class GameOverResponse(game: Game, debug: RequestInfo) extends GoResponse

/** A game waiting for a white player, with its komi so that a player sees it before joining. */
case class OpenGame(id: String, komi: Double)

/** The games waiting for a white player. `ids` comes from `games`, so the two cannot differ. */
case class OpenGamesResponse(games: List[OpenGame]) extends GoResponse:
  def ids: List[String] = games.map(_.id)
