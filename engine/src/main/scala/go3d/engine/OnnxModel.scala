package go3d.engine

import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession}
import com.typesafe.scalalogging.LazyLogging
import go3d.Game
import go3d.client.MoveEngineError

import java.nio.FloatBuffer
import java.nio.file.{Files, Path}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.{Failure, Success, Try, Using}

/** Where ONNX Runtime evaluates a model: on the CPU, or on the GPU with the CUDA provider. */
enum Device:
  case Cpu, Cuda

object Device:
  /** The device named `name`, as on the command line: `cpu` or `cuda`. */
  def named(name: String): Option[Device] = values.find(_.toString.equalsIgnoreCase(name))

/** A model generation loaded with ONNX Runtime, whose contract fits games of `size`, evaluated
  * on `device`.
  */
final class OnnxModel private (
    val path: Path,
    val size: Int,
    val komi: Double,
    val device: Device,
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

object OnnxModel extends LazyLogging:
  val InputName: String = "board"
  val PolicyOutput: String = "policy"
  val ValueOutput: String = "value"
  val FileName: String = "model.onnx"

  /** Where generation `generation` of size `size` is in the model store `store`. */
  def path(store: Path, size: Int, generation: Int): Path =
    store.resolve(size.toString).resolve(generation.toString).resolve(FileName)

  /** The model at `path` for a game of `size` on the CPU, if its contract is what the engine and
    * the game expect.
    */
  def load(path: Path, size: Int): Try[OnnxModel] = load(path, size, Device.Cpu)

  /** The model at `path` for a game of `size`, if its contract is what the engine and the game
    * expect. On [[Device.Cuda]] it falls back to the CPU with a warning when the CUDA provider
    * cannot load, e.g. without a GPU, CUDA or cuDNN; the model's `device` tells which one it got.
    */
  def load(path: Path, size: Int, device: Device): Try[OnnxModel] =
    if !Files.isRegularFile(path) then Failure(MoveEngineError(s"no model at $path"))
    else
      val environment = OrtEnvironment.getEnvironment()
      session(environment, path, device)
        .recoverWith(error => Failure(MoveEngineError(s"model $path: ${error.getMessage}")))
        .flatMap { (session, used) =>
          Try(session.getMetadata.getCustomMetadata.asScala.toMap)
            .flatMap(ModelContract.check(_, size))
            .map(komi => new OnnxModel(path, size, komi, used, environment, session))
            .recoverWith { error =>
              session.close()
              Failure(MoveEngineError(s"model $path for size $size: ${error.getMessage}"))
            }
        }

  /** A session of the model at `path` on `device`, or on the CPU if the CUDA provider fails. */
  private def session(
      environment: OrtEnvironment,
      path: Path,
      device: Device
  ): Try[(OrtSession, Device)] =
    def cpu = Try(environment.createSession(path.toString)).map(_ -> Device.Cpu)
    device match
      case Device.Cpu => cpu
      case Device.Cuda =>
        Using(OrtSession.SessionOptions()) { options =>
          options.addCUDA()
          environment.createSession(path.toString, options) -> Device.Cuda
        }.recoverWith { error =>
          val reason = error.getMessage
          logger.warn(s"the CUDA provider does not load, the model runs on the CPU: $reason")
          cpu
        }
