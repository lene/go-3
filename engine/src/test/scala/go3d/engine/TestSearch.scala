package go3d.engine

import go3d.{Black, Game, Move, Pass, Position, White}
import go3d.client.MoveEngineError
import go3d.Empty
import go3d.Goban
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import scala.util.Success

class TestSearch:

  private val uniform = FakeEvaluator(Map(), 0.0)

  /** Black to move on size 3 after White passed, with 2 Black stones and 1 White one. */
  private def afterWhitePassed(komi: Double): Game =
    played(3, komi, Move(1, 1, 1, Black), Move(2, 2, 2, White), Move(3, 3, 3, Black), Pass(White))

  @Test def testPlaysTheMoveThatWinsAtOnce(): Unit =
    // passing ends the game, and Black wins 2 to 1.5
    Assertions.assertEquals(
      Pass(Black),
      Search(uniform, 50).move(afterWhitePassed(0.5)).success.value
    )

  @Test def testAvoidsTheMoveThatLosesAtOnce(): Unit =
    // passing ends the game, and White wins 3.5 to 2
    val move = Search(uniform, 50).move(afterWhitePassed(2.5)).success.value
    Assertions.assertNotEquals(Pass(Black), move)

  @Test def testPassesWhenOnlyPassIsLegal(): Unit =
    // every empty point is an eye of the White group, so a Black stone there is suicide
    val eyes = Set((1, 1, 1), (3, 3, 3))
    val goban = Goban.tabulate(3)((x, y, z) => if eyes((x, y, z)) then Empty else White)
    val moves = Array[Move | Pass](Pass(Black), Move(2, 2, 2, White))
    val game = Game(3, goban.success.value, moves, Map(), 0.5)
    Assertions.assertEquals(List(), game.possibleMoves(Black))
    Assertions.assertEquals(Pass(Black), Search(uniform, 10).move(game).success.value)

  @Test def testFollowsThePriorsWhenTheValuesAreEqual(): Unit =
    val preferred = FakeEvaluator(Map(62 -> 5f), 0.0)
    val move = Search(preferred, 20).move(Game.start(5).success.value).success.value
    Assertions.assertEquals(Move(Position(3, 3, 3), Black), move)

  @Test def testNetworkValuesAreForThePlayerToMove(): Unit =
    // the player to move loses when the opponent has the centre, so Black takes it
    val centre = Position(3, 3, 3)
    val evaluator: Evaluator = game =>
      val value = if game.at(centre) == !game.moveColor then -1.0 else 0.0
      Success(Evaluation(IndexedSeq.fill(Encoding.actionCount(game.size))(0f), value))
    val move = Search(evaluator, 300).move(Game.start(5).success.value).success.value
    Assertions.assertEquals(Move(centre, Black), move)

  @Test def testGameThatIsOverHasNoMove(): Unit =
    val over = played(3, 0.5, Pass(Black), Pass(White))
    val error = Search(uniform, 10).move(over).failure.exception
    Assertions.assertInstanceOf(classOf[MoveEngineError], error)
