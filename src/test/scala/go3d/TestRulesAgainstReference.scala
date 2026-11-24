package go3d

import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import scala.annotation.tailrec
import scala.util.Random

/** The differential test of the rules: seeded random games are played through [[Game]] and
  * [[ReferenceRules]] side by side, and after every move the two must agree on the board, the
  * captures, the legal moves and why each other point is illegal, the end of the game and the
  * score. A faster implementation of the rules has to pass it unchanged.
  */
class TestRulesAgainstReference:

  /** What the games exercised, to check that they reach the rules that are easy to get wrong. */
  private final case class Tally(moves: Int, captures: Int, suicides: Int, kos: Int):
    def +(other: Tally): Tally = Tally(
      moves + other.moves,
      captures + other.captures,
      suicides + other.suicides,
      kos + other.kos
    )

  private val GamesBySize = List(3 -> 300, 5 -> 20)
  // one pass in this many moves, so that some games end by two passes and some ko threats pass
  private val PassEvery = 25

  @Test def testRandomGamesFollowTheReferenceRules(): Unit =
    val tally = GamesBySize
      .flatMap((size, games) => (0 until games).map(playBoth(size, _)))
      .reduce(_ + _)
    Assertions.assertTrue(tally.captures > 0, s"no captures in $tally")
    Assertions.assertTrue(tally.suicides > 0, s"no suicide rejected in $tally")
    Assertions.assertTrue(tally.kos > 0, s"no ko rejected in $tally")

  private def playBoth(size: Int, game: Int): Tally =
    compare(
      Game.start(size).success.value,
      ReferenceRules.start(size),
      Random(size * 10_000L + game),
      Tally(0, 0, 0, 0)
    )

  @tailrec
  private def compare(game: Game, reference: ReferenceRules, random: Random, tally: Tally): Tally =
    val context = s"size ${game.size}, move ${game.moves.length}: ${game.moves.toList}"
    assertSameState(game, reference, context)
    if reference.isOver then tally
    else
      val color = reference.toMove
      val (legal, counted) = compareMoves(game, reference, context)
      if legal.isEmpty || random.nextInt(PassEvery) == 0 then
        compare(game.makeMove(Pass(color)).success.value, reference.pass, random, tally + counted)
      else
        val (position, next) = legal(random.nextInt(legal.length))
        compare(
          game.makeMove(Move(position, color)).success.value,
          next,
          random,
          tally + counted.copy(captures = next.lastCaptured.size)
        )

  private def assertSameState(game: Game, reference: ReferenceRules, context: String): Unit =
    for position <- reference.points do
      Assertions.assertEquals(reference.at(position), game.at(position), s"$position, $context")
    Assertions.assertEquals(
      reference.lastCaptured,
      game.lastCapture.map(_.position).toSet,
      s"captures, $context"
    )
    Assertions.assertEquals(reference.isOver, game.isOver, s"over, $context")
    Assertions.assertEquals(reference.score, game.score, s"score, $context")

  /** Checks every empty point for the player to move: [[Game.checkValid]] rejects it for the same
    * reason as the reference, and [[Game.possibleMoves]] are the points the reference allows.
    * Returns those points with the game after each, and the move counted in a [[Tally]].
    */
  private def compareMoves(
      game: Game,
      reference: ReferenceRules,
      context: String
  ): (Seq[(Position, ReferenceRules)], Tally) =
    val color = reference.toMove
    val outcomes =
      reference.points.filter(reference.at(_) == Empty).map(p => p -> reference.play(p))
    for (position, outcome) <- outcomes do
      Assertions.assertEquals(
        outcome.left.toOption.map(_.getClass),
        game.checkValid(Move(position, color)).failed.toOption.map(_.getClass),
        s"$position, $context"
      )
    val legal = outcomes.collect { case (position, Right(next)) => position -> next }
    Assertions.assertEquals(
      legal.map(_._1).toSet,
      game.possibleMoves(color).toSet,
      s"possible moves, $context"
    )
    val rejected = outcomes.flatMap(_._2.left.toOption)
    val suicides = rejected.count {
      case _: Suicide => true
      case _ => false
    }
    val kos = rejected.count {
      case _: Ko => true
      case _ => false
    }
    (legal, Tally(1, 0, suicides, kos))
