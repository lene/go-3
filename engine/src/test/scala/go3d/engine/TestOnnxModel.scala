package go3d.engine

import go3d.Game
import go3d.client.MoveEngineError
import org.junit.jupiter.api.{Assertions, Test}
import org.scalatest.TryValues.*

import java.nio.file.Files
import scala.util.Using

class TestOnnxModel:

  // the outputs of the fixture in Python (onnxruntime, go3d_training.encoding)
  private val tolerance = 1e-5

  private def errorMessage(error: Throwable): String =
    Assertions.assertInstanceOf(classOf[MoveEngineError], error)
    error.getMessage

  @Test def testFixtureFitsTheContract(): Unit =
    Using.resource(OnnxModel.load(fixtureModelPath, 5).success.value) { model =>
      Assertions.assertEquals(4.5, model.komi)
      Assertions.assertEquals(5, model.size)
    }

  @Test def testInferenceOnAnEmptyBoard(): Unit =
    Using.resource(OnnxModel.load(fixtureModelPath, 5).success.value) { model =>
      val evaluation = model.evaluate(Game.start(5).success.value).success.value
      Assertions.assertEquals(126, evaluation.logits.length)
      Assertions.assertTrue(evaluation.value >= -1 && evaluation.value <= 1)
      Assertions.assertEquals(-0.44279834628105164, evaluation.value, tolerance)
      Assertions.assertEquals(0.08938410133123398, evaluation.logits(62).toDouble, tolerance)
      Assertions.assertEquals(0.20072856545448303, evaluation.logits(125).toDouble, tolerance)
    }

  @Test def testInferenceOnTheWorkedExampleMatchesPython(): Unit =
    Using.resource(OnnxModel.load(fixtureModelPath, 5).success.value) { model =>
      val evaluation = model.evaluate(workedExample).success.value
      Assertions.assertEquals(0.08341699093580246, evaluation.logits(62).toDouble, tolerance)
      Assertions.assertEquals(0.19913776218891144, evaluation.logits(125).toDouble, tolerance)
    }

  @Test def testModelForAnotherSizeIsRefused(): Unit =
    val message = errorMessage(OnnxModel.load(fixtureModelPath, 7).failure.exception)
    Assertions.assertTrue(message.contains("board_size is 5, expected 7"), message)
    Assertions.assertTrue(message.contains(fixtureModelPath.toString), message)

  @Test def testMissingModelNamesThePath(): Unit =
    val path = OnnxModel.path(fixtureStore, 5, 1)
    Assertions.assertEquals(
      s"no model at $path",
      errorMessage(OnnxModel.load(path, 5).failure.exception)
    )

  @Test def testFileThatIsNoModelIsRefused(): Unit =
    val path = Files.createTempFile("model", ".onnx")
    Files.writeString(path, "not a model")
    val message = errorMessage(OnnxModel.load(path, 5).failure.exception)
    Files.delete(path)
    Assertions.assertTrue(message.contains(path.toString), message)
