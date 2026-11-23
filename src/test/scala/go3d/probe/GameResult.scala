package go3d.probe

import go3d.Black
import go3d.Color
import go3d.Game
import go3d.Pass
import go3d.White

/** How a game ended, as [[go3d.Game.isOver]] decides it. */
enum Ending(val text: String):
  /** Both players passed in a row. */
  case Passes extends Ending("passes")

  /** The game reached size³ moves. */
  case MoveLimit extends Ending("move-limit")

object Ending:
  def fromText(text: String): Option[Ending] = values.find(_.text == text)

  def of(game: Game): Ending =
    if game.moves.takeRight(2).collect { case p: Pass => p }.length == 2 then Passes
    else MoveLimit

/** What one player has at the end of a game: the stones on the board, the area score (stones plus
  * territory) and the stones this player captured during the game.
  */
final case class SideScore(stones: Int, area: Int, captures: Int):
  def areaWithCaptures: Int = area + captures
  def hasTerritory: Boolean = area > stones

object SideScore:
  def of(game: Game, scores: Map[Color, Int], color: Color): SideScore =
    SideScore(
      game.goban.allPositions.count(game.at(_) == color),
      scores(color),
      game.captures(color)
    )

/** The two scorings of a game: area scoring, and area scoring with the captures added (the rule
  * before 0.7.44). The winner of each follows from the scores and any komi.
  */
object Scoring:
  /** White wins if White's score plus the komi is higher, as in [[go3d.Game.winner]]. A
    * half-integer komi leaves no tie.
    */
  def winner(black: Int, white: Int, komi: Double): Color =
    if white + komi > black then White else Black

/** One played game: who played, how long it lasted and how it ended, and both scores. `record` is
  * the id of the saved game record, for the games that have one.
  */
final case class GameResult(
    size: Int,
    black: String,
    white: String,
    game: Int,
    moves: Int,
    ending: Ending,
    blackSide: SideScore,
    whiteSide: SideScore,
    record: Option[String]
):
  def isSelfPlay: Boolean = black == white
  def captures: Int = blackSide.captures + whiteSide.captures
  def hasTerritory: Boolean = blackSide.hasTerritory || whiteSide.hasTerritory

  /** Black's area score minus White's. */
  def areaMargin: Int = blackSide.area - whiteSide.area

  def winnerByArea(komi: Double): Color = Scoring.winner(blackSide.area, whiteSide.area, komi)

  def winnerByAreaWithCaptures(komi: Double): Color =
    Scoring.winner(blackSide.areaWithCaptures, whiteSide.areaWithCaptures, komi)

  /** Whether adding the captures changes the winner at `komi`. */
  def flips(komi: Double): Boolean = winnerByArea(komi) != winnerByAreaWithCaptures(komi)

object GameResult:
  def of(
      size: Int,
      black: String,
      white: String,
      number: Int,
      game: Game,
      record: Option[String]
  ): GameResult =
    val scores = game.score
    GameResult(
      size,
      black,
      white,
      number,
      game.moves.length,
      Ending.of(game),
      SideScore.of(game, scores, Black),
      SideScore.of(game, scores, White),
      record
    )
