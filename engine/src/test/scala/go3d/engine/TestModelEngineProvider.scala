package go3d.engine

import go3d.{Game, Move, Pass}
import go3d.client.{MoveEngine, MoveEngineError, MoveEngineSettings}
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import java.nio.file.Files

class TestModelEngineProvider:

  private val settings = MoveEngineSettings(fixtureStore.toString, 4)

  private def errorMessage(strategy: String, game: Game): String =
    val error = MoveEngine.create(strategy, settings, game).failure.exception
    Assertions.assertInstanceOf(classOf[MoveEngineError], error)
    error.getMessage

  @Test def testModelStrategyIsFoundAndPlaysALegalMove(): Unit =
    val game = Game.start(5).success.value
    val move: Move | Pass = MoveEngine.create("model:0", settings, game).success.value
      .move(game).success.value
    Assertions.assertTrue(game.makeMove(move).isSuccess, move.toString)

  @Test def testGameAtTheModelKomiIsPlayed(): Unit =
    val game = Game.start(5, 4.5).success.value
    Assertions.assertTrue(MoveEngine.create("model:0", settings, game).isSuccess)

  @Test def testModelForAnotherSizeIsRefused(): Unit =
    // the fixture, stored where generation 0 of size 7 belongs
    val store = Files.createTempDirectory("models")
    val misplaced = OnnxModel.path(store, 7, 0)
    Files.createDirectories(misplaced.getParent)
    Files.copy(fixtureModelPath, misplaced)
    val game = Game.start(7).success.value
    val error = MoveEngine.create("model:0", MoveEngineSettings(store.toString, 4), game)
      .failure.exception
    Seq(misplaced, misplaced.getParent, misplaced.getParent.getParent, store).foreach(Files.delete)
    Assertions.assertInstanceOf(classOf[MoveEngineError], error)
    Assertions.assertTrue(
      error.getMessage.contains("board_size is 5, expected 7"),
      error.getMessage
    )

  @Test def testMissingGenerationNamesThePath(): Unit =
    val message = errorMessage("model:1", Game.start(5).success.value)
    Assertions.assertTrue(message.contains(OnnxModel.path(fixtureStore, 5, 1).toString), message)

  @Test def testGenerationMustBeANumber(): Unit =
    Assertions.assertTrue(errorMessage("model:x", Game.start(5).success.value).contains("model:x"))
    Assertions.assertTrue(errorMessage("model:-1", Game.start(5).success.value).contains("-1"))
