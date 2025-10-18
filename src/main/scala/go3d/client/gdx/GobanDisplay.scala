package go3d.client.gdx

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.graphics.g3d.{ModelInstance, RenderableProvider}
import com.badlogic.gdx.utils.Timer
import com.typesafe.scalalogging.LazyLogging
import go3d.client.BaseClient
import go3d.server.StatusResponse
import go3d.{Black, Game, Position, White}

class GobanDisplay(client: BaseClient, val cursorFadeSeconds: Float = 10.0f)
    extends ApplicationListener with LazyLogging:
  private final val BOARD_SIZE: Int = client.status.game.size
  final val UPDATE_DELAY_SECONDS = 2f
  final val UPDATE_INTERVAL_SECONDS = 1f

  private lazy val builder = GeometryBuilder(BOARD_SIZE)
  private lazy val gdxResources = GDXResources(BOARD_SIZE)

  private var stonesModel: List[RenderableProvider] = List()
  private var game: Option[Game] = None
  private var ownMoveTimestamp: Float = 0f
  private var opponentMoveTimestamp: Float = 0f
  private var lastOwnMove: Option[Position] = None
  private var lastOpponentMove: Option[Position] = None

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
    val currentMove = game.flatMap(_.playerLastMove(client.playerColor))
    if currentMove != lastOwnMove then
      lastOwnMove = currentMove
      ownMoveTimestamp = com.badlogic.gdx.utils.TimeUtils.millis() / 1000f
    currentMove

  private def opponentLastMove: Option[Position] =
    val currentMove = game.flatMap(_.playerLastMove(client.playerColor.map(!_)))
    if currentMove != lastOpponentMove then
      lastOpponentMove = currentMove
      opponentMoveTimestamp = com.badlogic.gdx.utils.TimeUtils.millis() / 1000f
    currentMove

  @Override def render(): Unit =
    val currentTime = com.badlogic.gdx.utils.TimeUtils.millis() / 1000f
    val ownFadeAlpha = calculateFadeAlpha(currentTime - ownMoveTimestamp)
    val opponentFadeAlpha = calculateFadeAlpha(currentTime - opponentMoveTimestamp)
    gdxResources.render(
      ownLastMove, opponentLastMove, ownFadeAlpha, opponentFadeAlpha,
      builder.gridModel, stonesModel
    )

  private def calculateFadeAlpha(elapsedTime: Float): Float =
    if elapsedTime >= cursorFadeSeconds then 1.0f
    else (elapsedTime / cursorFadeSeconds).max(0f).min(1f)

  @Override def dispose(): Unit =
    gdxResources.dispose()
    builder.dispose()

  @Override def resume(): Unit = logger.debug("resume")

  @Override def resize(width: Int, height: Int): Unit = gdxResources.resize()

  @Override def pause(): Unit = logger.debug("pause")
