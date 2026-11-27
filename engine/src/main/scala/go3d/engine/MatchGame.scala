package go3d.engine

import go3d.{Black, Game}

import scala.annotation.tailrec
import scala.util.{Failure, Random, Success, Try}

/** A game of a promotion match ([[Match]]): two evaluators, each with a search of its own. */
object MatchGame:
  /** `black` and `white` play from `start` until the game is over, each the moves of its colour
    * with `simulations` per move. The first [[SelfPlayGame.openingMoves]] moves are drawn in
    * proportion to the root visits, without noise, so that the games of a match differ; the later
    * moves are the most visited ones. The same `seed` gives the same game when the evaluators give
    * the same evaluations.
    */
  def play(
      black: Evaluator,
      white: Evaluator,
      start: Game,
      simulations: Int,
      seed: Long
  ): Try[Game] =
    val blackSearch = Search(black, simulations)
    val whiteSearch = Search(white, simulations)
    val random = Random(seed)

    @tailrec
    def playOut(game: Game): Try[Game] =
      if game.isOver then Success(game)
      else
        val search = if game.moveColor == Black then blackSearch else whiteSearch
        val chosen =
          if game.moves.length < SelfPlayGame.openingMoves(game.size) then
            search.selfPlayMove(game, random, sample = true, noise = false).map(_.move)
          else search.move(game)
        chosen.flatMap(game.makeMove) match
          case Success(next) => playOut(next)
          case Failure(error) => Failure(error)

    playOut(start)
