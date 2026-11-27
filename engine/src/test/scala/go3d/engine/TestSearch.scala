package go3d.engine

import go3d.{Black, Game, Move, Pass, Position, White}
import go3d.client.MoveEngineError
import go3d.Empty
import go3d.Goban
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import scala.util.{Random, Success}

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

  private val tolerance = 1e-9

  @Test def testSelfPlayPolicyIsTheRootVisitShareOfEveryAction(): Unit =
    val game = played(3, 0.5, Move(1, 1, 1, Black), Move(2, 2, 2, White))
    val choice =
      Search(uniform, 30).selfPlayMove(game, Random(1), sample = true, noise = true).success.value
    Assertions.assertEquals(28, choice.policy.length)
    Assertions.assertEquals(1.0, choice.policy.sum, tolerance)
    // 30 simulations: every share is a whole number of visits
    choice.policy.foreach(share => Assertions.assertEquals(math.rint(share * 30), share * 30, 1e-6))
    val legal = legalActions(game)
    choice.policy.indices.filterNot(legal).foreach(action =>
      Assertions.assertEquals(0.0, choice.policy(action), action.toString)
    )
    Assertions.assertTrue(legal.contains(Encoding.action(choice.move, 3)))

  @Test def testSelfPlayMoveWithoutSamplingIsTheMostVisited(): Unit =
    val start = Game.start(5).success.value
    (1 to 5).foreach { seed =>
      val choice =
        Search(uniform, 40).selfPlayMove(start, Random(seed), sample = false, noise = true)
          .success.value
      val visits = choice.policy(Encoding.action(choice.move, 5))
      Assertions.assertEquals(choice.policy.max, visits, tolerance)
    }

  @Test def testSelfPlayMoveIsReproducible(): Unit =
    val start = Game.start(5).success.value
    def choice(seed: Long) =
      Search(
        uniform,
        20
      ).selfPlayMove(start, Random(seed), sample = true, noise = true).success.value
    Assertions.assertEquals(choice(4), choice(4))

  @Test def testNoiseMakesSelfPlayExplore(): Unit =
    // uniform priors and values: without noise and sampling every seed would play the same
    val start = Game.start(5).success.value
    val moves = (1 to 10).map { seed =>
      Search(
        uniform,
        20
      ).selfPlayMove(start, Random(seed), sample = false, noise = true).success.value.move
    }
    Assertions.assertTrue(moves.distinct.length > 1, moves.toString)

  @Test def testWithoutNoiseTheMostVisitedMoveDoesNotDependOnTheSeed(): Unit =
    val start = Game.start(5).success.value
    val moves = (1 to 10).map { seed =>
      Search(uniform, 20).selfPlayMove(start, Random(seed), sample = false, noise = false)
        .success.value.move
    }
    Assertions.assertEquals(List(Search(uniform, 20).move(start).success.value), moves.distinct)

  @Test def testWithoutNoiseASampledMoveStillDependsOnTheSeed(): Unit =
    val start = Game.start(5).success.value
    val moves = (1 to 10).map { seed =>
      Search(uniform, 20).selfPlayMove(start, Random(seed), sample = true, noise = false)
        .success.value.move
    }
    Assertions.assertTrue(moves.distinct.length > 1, moves.toString)

  @Test def testSelfPlayStillTakesTheWinningPass(): Unit =
    val choice = Search(
      uniform,
      50
    ).selfPlayMove(afterWhitePassed(0.5), Random(1), sample = false, noise = true)
    Assertions.assertEquals(Pass(Black), choice.success.value.move)

  @Test def testGameThatIsOverHasNoSelfPlayMove(): Unit =
    val over = played(3, 0.5, Pass(Black), Pass(White))
    val error = Search(
      uniform,
      10
    ).selfPlayMove(over, Random(1), sample = true, noise = true).failure.exception
    Assertions.assertInstanceOf(classOf[MoveEngineError], error)

  @Test def testSampledSelfPlayMovesFollowTheVisits(): Unit =
    // two favoured actions: the most visited move is one of them, a sampled one need not be
    val favoured = FakeEvaluator(Map(0 -> 3f, 62 -> 3f), 0.0)
    val start = Game.start(5).success.value
    val choices = (1 to 200).map { seed =>
      Search(
        favoured,
        10
      ).selfPlayMove(start, Random(seed), sample = true, noise = true).success.value
    }
    val shares = choices.map(choice => choice.policy(Encoding.action(choice.move, 5)))
    Assertions.assertTrue(choices.map(_.move).distinct.length > 1)
    Assertions.assertTrue(shares.forall(_ > 0), shares.toString)
    Assertions.assertTrue(
      choices.zip(shares).exists((choice, share) => share < choice.policy.max),
      "every sampled move was the most visited one"
    )
