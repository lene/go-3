package go3d.engine

import com.typesafe.scalalogging.LazyLogging
import go3d.Game
import go3d.client.{MoveEngine, MoveEngineError, MoveEngineProvider, MoveEngineSettings}

import java.nio.file.Paths
import scala.util.{Failure, Success, Try}

/** The strategy `model:<generation>`: a [[Search]] with generation `generation` of the game's
  * size from the model store. Registered in `META-INF/services` for `BotClient` to find.
  */
final class ModelEngineProvider extends MoveEngineProvider with LazyLogging:
  def prefix: String = ModelEngineProvider.Prefix

  def create(argument: String, settings: MoveEngineSettings, game: Game): Try[MoveEngine] =
    for
      generation <- argument.toIntOption.filter(_ >= 0).fold[Try[Int]](
        Failure(MoveEngineError(s"$prefix:$argument: a generation is a number from 0"))
      )(Success(_))
      store <- Try(Paths.get(settings.modelStore))
      model <- OnnxModel.load(OnnxModel.path(store, game.size, generation), game.size)
    yield
      if model.komi != game.komi then
        logger.warn(
          s"model ${model.path} was made at komi ${model.komi}, the game has komi ${game.komi}"
        )
      Search(model, settings.simulations)

object ModelEngineProvider:
  val Prefix: String = "model"
