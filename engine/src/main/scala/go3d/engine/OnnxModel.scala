package go3d.engine

import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession}
import go3d.Game
import go3d.client.MoveEngineError

import java.nio.FloatBuffer
import java.nio.file.{Files, Path}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.{Failure, Success, Try, Using}

/** A model generation loaded with ONNX Runtime, whose contract fits games of `size`. */
final class OnnxModel private (
    val path: Path,
    val size: Int,
    val komi: Double,
    environment: OrtEnvironment,
    session: OrtSession
) extends Evaluator
    with AutoCloseable:

  private val shape = Array[Long](1, Encoding.InputPlanes, size, size, size)

  def evaluate(game: Game): Try[Evaluation] =
    Using.Manager { use =>
      val board = FloatBuffer.wrap(Encoding.encode(game))
      val input = use(OnnxTensor.createTensor(environment, board, shape))
      val result = use(session.run(java.util.Map.of(OnnxModel.InputName, input)))
      for
        policy <- output(result, OnnxModel.PolicyOutput)
        value <- output(result, OnnxModel.ValueOutput)
      yield Evaluation(ArraySeq.unsafeWrapArray(policy), value(0).toDouble)
    }.flatten

  def close(): Unit = session.close()

  private def output(result: OrtSession.Result, name: String): Try[Array[Float]] =
    result.get(name).toScala match
      case Some(tensor: OnnxTensor) =>
        val buffer = tensor.getFloatBuffer
        Success(Array.tabulate(buffer.remaining)(buffer.get))
      case _ => Failure(MoveEngineError(s"model $path has no tensor output $name"))

object OnnxModel:
  val InputName: String = "board"
  val PolicyOutput: String = "policy"
  val ValueOutput: String = "value"
  val FileName: String = "model.onnx"

  /** Where generation `generation` of size `size` is in the model store `store`. */
  def path(store: Path, size: Int, generation: Int): Path =
    store.resolve(size.toString).resolve(generation.toString).resolve(FileName)

  /** The model at `path` for a game of `size`, if its contract is what the engine and the game
    * expect.
    */
  def load(path: Path, size: Int): Try[OnnxModel] =
    if !Files.isRegularFile(path) then Failure(MoveEngineError(s"no model at $path"))
    else
      val environment = OrtEnvironment.getEnvironment()
      Try(environment.createSession(path.toString))
        .recoverWith(error => Failure(MoveEngineError(s"model $path: ${error.getMessage}")))
        .flatMap { session =>
          Try(session.getMetadata.getCustomMetadata.asScala.toMap)
            .flatMap(ModelContract.check(_, size))
            .map(komi => new OnnxModel(path, size, komi, environment, session))
            .recoverWith { error =>
              session.close()
              Failure(MoveEngineError(s"model $path for size $size: ${error.getMessage}"))
            }
        }
