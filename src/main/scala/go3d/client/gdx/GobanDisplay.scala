package go3d.client.gdx

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.graphics.g3d.RenderableProvider
import com.badlogic.gdx.utils.Timer
import com.typesafe.scalalogging.LazyLogging

import go3d.client.BaseClient
import go3d.server.StatusResponse
import go3d.{Black, Game, White}

class GobanDisplay(client: BaseClient) extends ApplicationListener with LazyLogging:
  final val BOARD_SIZE: Int = client.status.game.size
  final val UPDATE_DELAY_SECONDS = 2f
  final val UPDATE_INTERVAL_SECONDS = 1f

  private lazy val gdxResources = GDXResources(BOARD_SIZE)
  private lazy val builder = GeometryBuilder(BOARD_SIZE)

  private[this] var stonesModel: List[RenderableProvider] = List()
  private[this] var game: Option[Game] = None

  @Override def create(): Unit =
    updateGame(client.status)
    Timer.schedule(new Timer.Task {
      @Override def run(): Unit = updateGame(client.status)
    }, UPDATE_DELAY_SECONDS, UPDATE_INTERVAL_SECONDS)

  private def updateGame(status: StatusResponse): Unit =
    def doUpdate(): Unit =
      game = Some(status.game)
      stonesModel = builder.createStones(status.game)
      logger.info(s"Move ${status.game.moves.length}: $lastMove $captures")
    game match
      case None => doUpdate()
      case Some(g) => if status.game.moves.length != g.moves.length then doUpdate()

  private def lastMove: String =
    game.fold("")(
      g => if g.moves.length == 0 then "waiting for game to start" else g.moves.last.toString
    )

  private def captures: String =
    game.fold("")(
      g => "Captures: " + Seq(Black, White).foldLeft("")(
        (caps, col) => caps + s"$col: ${g.captures(col)} "
      )
    )

  @Override def render(): Unit = gdxResources.render(builder.gridModel, stonesModel)

  @Override def dispose(): Unit =
    gdxResources.dispose()
    builder.dispose()

  @Override def resume(): Unit = logger.info("resume")

  @Override def resize(width: Int, height: Int): Unit = gdxResources.resize()

  @Override def pause(): Unit = logger.info("pause")
