package go3d.client.gdx

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.graphics.g3d.{ModelInstance, RenderableProvider}
import com.badlogic.gdx.utils.Timer
import com.typesafe.scalalogging.LazyLogging
import go3d.client.BaseClient
import go3d.server.StatusResponse
import go3d.{Black, Game, Position, White}

class GobanDisplay(client: BaseClient) extends ApplicationListener with LazyLogging:
  private final val BOARD_SIZE: Int = client.status.game.size
  final val UPDATE_DELAY_SECONDS = 2f
  final val UPDATE_INTERVAL_SECONDS = 1f

  private lazy val builder = GeometryBuilder(BOARD_SIZE)
  private lazy val gdxResources = GDXResources(BOARD_SIZE)

  private var stonesModel: List[RenderableProvider] = List()
  private var game: Option[Game] = None

  @Override def create(): Unit =
    logger.info(s"GobanDisplay.create() - client.playerColor = ${client.playerColor}")
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

  private def ownLastMove: Option[Position] =
    game.flatMap(_.playerLastMove(client.playerColor))

  private def opponentLastMove: Option[Position] =
    game.flatMap(_.playerLastMove(client.playerColor.map(!_)))

  @Override def render(): Unit =
    gdxResources.render(ownLastMove, opponentLastMove, builder.gridModel, stonesModel)

  @Override def dispose(): Unit =
    gdxResources.dispose()
    builder.dispose()

  @Override def resume(): Unit = logger.debug("resume")

  @Override def resize(width: Int, height: Int): Unit = gdxResources.resize()

  @Override def pause(): Unit = logger.debug("pause")
