package go3d.client.gdx

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.RenderableProvider
import scala.compiletime.uninitialized
import com.badlogic.gdx.utils.Timer
import com.typesafe.scalalogging.LazyLogging
import go3d.Black
import go3d.Game
import go3d.Position
import go3d.White
import go3d.client.BaseClient
import go3d.server.StatusResponse

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
class GobanDisplay(
  client: BaseClient,
  val cursorFadeSeconds: Float = 10.0f,
  replayState: Option[ReplayState] = None
) extends ApplicationListener with LazyLogging:
  private final val BOARD_SIZE: Int = client.status.get.game.size
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
  private var hudBatch: SpriteBatch = uninitialized
  private var hudFont: BitmapFont = uninitialized

  @Override def create(): Unit =
    logger.info(s"GobanDisplay.create() - client.playerColor = ${client.playerColor}")
    client.status.foreach(updateGame)
    Timer.schedule(new Timer.Task {
      @Override def run(): Unit = client.status.foreach(updateGame)
    }, UPDATE_DELAY_SECONDS, UPDATE_INTERVAL_SECONDS)
    hudBatch = new SpriteBatch()
    hudFont  = new BitmapFont()
    if replayState.isDefined then
      Gdx.input.setInputProcessor(new com.badlogic.gdx.InputAdapter {
        override def keyDown(keycode: Int): Boolean =
          keycode match
            case com.badlogic.gdx.Input.Keys.SPACE     => replayState.foreach(_.advance()); true
            case com.badlogic.gdx.Input.Keys.BACKSPACE => replayState.foreach(_.rewind());  true
            case _              => false
      })

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
    replayState match
      case Some(rs) =>
        rs.tick(Gdx.graphics.getDeltaTime)
        rs.currentStatus match
          case Some(sr) =>
            if game.forall(_.moves.length != sr.game.moves.length) then
              game = Some(sr.game)
              stonesModel = builder.createStones(sr.game)
          case None =>
            // init() not yet complete: leave game/stonesModel as-is (empty on first frame)
      case None =>
        () // existing live-polling path continues below
    val currentTime = com.badlogic.gdx.utils.TimeUtils.millis() / 1000f
    val ownFadeAlpha = calculateFadeAlpha(currentTime - ownMoveTimestamp)
    val opponentFadeAlpha = calculateFadeAlpha(currentTime - opponentMoveTimestamp)
    gdxResources.render(
      ownLastMove, opponentLastMove, ownFadeAlpha, opponentFadeAlpha,
      builder.gridModel, stonesModel
    )
    if replayState.isDefined then drawHud()

  private def drawHud(): Unit =
    replayState.foreach { rs =>
      val moveStr  = s"Move ${rs.currentIndex} / ${rs.totalMoves}"
      val colorStr = rs.currentStatus.fold("") { sr =>
        if sr.game.isOver then "Game over"
        else s"${sr.game.moveColor} to move"
      }
      val capsStr  = rs.currentStatus.fold("") { sr =>
        val blackCaps = sr.game.captures(go3d.Black)
        val whiteCaps = sr.game.captures(go3d.White)
        s"Black captures: $blackCaps  White captures: $whiteCaps"
      }
      val lineHeight = hudFont.getLineHeight
      val y = Gdx.graphics.getHeight - 10f
      hudBatch.begin()
      hudFont.draw(hudBatch, moveStr,  10f, y)
      hudFont.draw(hudBatch, colorStr, 10f, y - lineHeight)
      hudFont.draw(hudBatch, capsStr,  10f, y - 2 * lineHeight)
      hudBatch.end()
    }

  private def calculateFadeAlpha(elapsedTime: Float): Float =
    if elapsedTime >= cursorFadeSeconds then 1.0f
    else (elapsedTime / cursorFadeSeconds).max(0f).min(1f)

  @Override def dispose(): Unit =
    gdxResources.dispose()
    builder.dispose()
    if hudBatch != null then hudBatch.dispose()
    if hudFont  != null then hudFont.dispose()

  @Override def resume(): Unit = logger.debug("resume")

  @Override def resize(width: Int, height: Int): Unit = gdxResources.resize()

  @Override def pause(): Unit = logger.debug("pause")
