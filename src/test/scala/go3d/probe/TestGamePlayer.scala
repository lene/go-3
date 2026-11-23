package go3d.probe

import go3d.*
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

class TestGamePlayer:

  private def play(black: String, white: String, seed: Long): Game =
    playOn(3, black, white, seed)

  private def playOn(size: Int, black: String, white: String, seed: Long): Game =
    GamePlayer.play(size, black, white, seed).success.value

  @Test def testAGamePlaysUntilItIsOver(): Unit =
    val game = play("random", "random", 1L)
    Assertions.assertTrue(game.isOver)
    Assertions.assertEquals(Komi.Default, game.komi)

  @Test def testBlackMovesFirstAndTheColoursAlternate(): Unit =
    val game = play("random", "closestToCenter", 1L)
    Assertions.assertEquals(Black, game.moves.head.color)
    Assertions.assertTrue(game.moves.sliding(2).forall(pair => pair(0).color != pair(1).color))

  @Test def testAGameReplaysThroughGame(): Unit =
    val game = play("prioritiseCapture", "maximizeOwnLiberties", 3L)
    val replayed = game.moves.foldLeft(Game.start(3).success.value) { (acc, move) =>
      acc.makeMove(move).success.value
    }
    Assertions.assertEquals(game, replayed)
    Assertions.assertEquals(game.score, replayed.score)

  @Test def testTheSameSeedGivesTheSameGame(): Unit =
    for strategy <- ProbeOptions.StrategyNames do
      val first = play(strategy, "random", 42L)
      val second = play(strategy, "random", 42L)
      Assertions.assertEquals(
        first.moves.toList.map(_.toString),
        second.moves.toList.map(_.toString)
      )

  @Test def testAnotherSeedGivesAnotherGame(): Unit =
    val games = (1L to 5L).map(seed => play("random", "random", seed).moves.toList.map(_.toString))
    Assertions.assertTrue(games.distinct.length > 1)

  @Test def testAGameOnALargerBoardIsReproducible(): Unit =
    val first = playOn(5, "minimizeOpponentLiberties", "closestToStarPoints", 7L)
    val second = playOn(5, "minimizeOpponentLiberties", "closestToStarPoints", 7L)
    Assertions.assertEquals(first.moves.toList.map(_.toString), second.moves.toList.map(_.toString))
    Assertions.assertEquals(first.score, second.score)

  @Test def testEveryStrategyOfTheProbeIsOneOfSetStrategy(): Unit =
    for name <- ProbeOptions.StrategyNames do
      Assertions.assertTrue(GamePlayer.play(3, name, name, 1L).isSuccess, name)

  @Test def testAnUnknownStrategyFailsTheGame(): Unit =
    Assertions.assertTrue(GamePlayer.play(3, "random", "cleverest", 1L).isFailure)

  @Test def testAnInvalidSizeFailsTheGame(): Unit =
    Assertions.assertTrue(GamePlayer.play(4, "random", "random", 1L).isFailure)

  @Test def testGameSeedsDependOnEverythingThatIdentifiesAGame(): Unit =
    val base = GamePlayer.gameSeed(1L, 5, "random", "closestToCenter", 0)
    val others = List(
      GamePlayer.gameSeed(2L, 5, "random", "closestToCenter", 0),
      GamePlayer.gameSeed(1L, 7, "random", "closestToCenter", 0),
      GamePlayer.gameSeed(1L, 5, "closestToCenter", "closestToCenter", 0),
      GamePlayer.gameSeed(1L, 5, "random", "random", 0),
      GamePlayer.gameSeed(1L, 5, "random", "closestToCenter", 1)
    )
    Assertions.assertEquals(base, GamePlayer.gameSeed(1L, 5, "random", "closestToCenter", 0))
    Assertions.assertEquals(others.length, (base :: others).distinct.length - 1)

  @Test def testGameSeedsAreStableAcrossVersions(): Unit =
    Assertions.assertEquals(
      -4822689908822626334L,
      GamePlayer.gameSeed(1L, 5, "random", "closestToCenter", 0)
    )
