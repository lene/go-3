package go3d.client.gdx

import com.typesafe.scalalogging.LazyLogging
import go3d.client.BaseClient
import go3d.server.StatusResponse

import scala.util.{Failure, Success}

// Any: only from s"..." interpolation in log and HUD text (see STATIC_ANALYSIS.md)
@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.Any"))
class ReplayState(
  client: BaseClient,
  from: Int,
  private var to: Int,
  autoPlayDelay: Float
) extends LazyLogging:

  var currentIndex: Int = from
  var isPlaying: Boolean = true
  var elapsed: Float = 0f
  var currentStatus: Option[StatusResponse] = None
  var totalMoves: Int = to

  def init(): Unit =
    client.statusAtMove(Int.MaxValue) match
      case Success(sr) =>
        val actualTotal = sr.game.moves.length
        totalMoves = actualTotal
        to = to.min(actualTotal)
      case Failure(e) =>
        logger.error(s"ReplayState.init probe fetch failed: ${e.getMessage}")
        isPlaying = false
    currentIndex = from
    fetch()

  def fetch(): Unit =
    client.statusAtMove(currentIndex) match
      case Success(sr) => currentStatus = Some(sr)
      case Failure(e)  => logger.error(s"ReplayState.fetch($currentIndex) failed: ${e.getMessage}")

  def advance(): Unit =
    if currentIndex < to then
      currentIndex += 1
      fetch()
      if currentIndex == to then isPlaying = false

  def rewind(): Unit =
    if currentIndex > from then
      currentIndex -= 1
      fetch()

  /** Text lines of the replay HUD: move counter, side to move and captures. */
  def hudLines: Seq[String] =
    val moveLine = s"Move $currentIndex / $totalMoves"
    currentStatus.fold(Seq(moveLine, "", "")) { sr =>
      val colorLine = if sr.game.isOver then "Game over" else s"${sr.game.moveColor} to move"
      val capturesLine =
        s"Black captures: ${sr.game.captures(go3d.Black)}  " +
          s"White captures: ${sr.game.captures(go3d.White)}"
      Seq(moveLine, colorLine, capturesLine)
    }

  def tick(delta: Float): Unit =
    if isPlaying then
      elapsed += delta
      if elapsed >= autoPlayDelay then
        elapsed -= autoPlayDelay
        advance()
