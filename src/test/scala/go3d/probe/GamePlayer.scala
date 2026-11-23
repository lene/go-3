package go3d.probe

import go3d.Black
import go3d.Color
import go3d.Game
import go3d.Komi
import go3d.Move
import go3d.Pass
import go3d.White
import go3d.client.SetStrategy

import scala.annotation.tailrec
import scala.util.hashing.MurmurHash3
import scala.util.{Failure, Random, Success, Try}

/** Plays one game in process, with the move rule of [[go3d.client.BotClient]]: the possible
  * moves, [[SetStrategy.narrowDown]], one random pick, and a pass when nothing is left. There is
  * no server and no [[go3d.server.Games]].
  */
object GamePlayer:

  /** Black and White play a game on a board of `size` at [[Komi.Default]] until [[Game.isOver]].
    * The same `seed` gives the same game.
    */
  def play(size: Int, black: String, white: String, seed: Long): Try[Game] =
    Game.start(size, Komi.Default).flatMap { start =>
      // a fresh strategy for each player and game: a strategy keeps its thinking time
      val strategies =
        Map(Black -> SetStrategy(size, Array(black)), White -> SetStrategy(size, Array(white)))
      playOut(start, strategies, Random(seed))
    }

  @tailrec
  private def playOut(game: Game, strategies: Map[Color, SetStrategy], random: Random): Try[Game] =
    if game.isOver then Success(game)
    else
      chooseMove(game, strategies(game.moveColor), random).flatMap(game.makeMove) match
        case Success(next) => playOut(next, strategies, random)
        case Failure(e) => Failure(e)

  /** The move of the player to move. [[SetStrategy.narrowDown]] shuffles with the global `Random`,
    * so the candidates are sorted before the seeded pick: that is what makes a game reproducible.
    */
  private def chooseMove(game: Game, strategy: SetStrategy, random: Random): Try[Move | Pass] =
    val color = game.moveColor
    strategy.narrowDown(game.possibleMoves(color), game).map { candidates =>
      if candidates.isEmpty then Pass(color)
      else
        val sorted = candidates.sortBy(_.asTuple)
        Move(sorted(random.nextInt(sorted.length)), color)
    }

  /** The seed of one game, from the seed of the run and what identifies the game, so that a game
    * does not depend on the order in which games are played. Two hashes make 64 bits.
    */
  def gameSeed(seed: Long, size: Int, black: String, white: String, game: Int): Long =
    val key = s"$seed/$size/$black/$white/$game"
    (MurmurHash3.stringHash(key, 1).toLong << 32) |
      (MurmurHash3.stringHash(key, 2).toLong & 0xffffffffL)
