package go3d.client.gdx

import com.typesafe.scalalogging.LazyLogging
import go3d.{Black, Color, Move, Pass}
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

  /** Where the one-line progress indicator is printed; replaceable in tests. */
  private[gdx] var out: java.io.PrintStream = System.out
  private var lastProgressLength: Int = 0

  def fetch(): Unit =
    client.statusAtMove(currentIndex) match
      case Success(sr) =>
        currentStatus = Some(sr)
        printProgress()
      case Failure(e)  => logger.error(s"ReplayState.fetch($currentIndex) failed: ${e.getMessage}")

  /** Move counter and the move just played, e.g. "[10/245] black @ 3 2 3". */
  def progressLine: String =
    val lastMove = currentStatus.flatMap(_.game.moves.lastOption)
    val moveText = lastMove.fold("") {
      case move: Move => s" ${colorName(move.color)} ${move.color} ${move.x} ${move.y} ${move.z}"
      case pass: Pass => s" ${colorName(pass.color)} ${pass.color} pass"
    }
    s"[$currentIndex/$totalMoves]$moveText"

  /** Prints the progress line ending in a carriage return, so the output stays on one line. */
  private def printProgress(): Unit =
    val line = progressLine
    out.print(line.padTo(lastProgressLength, ' ') + "\r")
    out.flush()
    lastProgressLength = line.length

  private def colorName(color: Color): String = if color == Black then "black" else "white"

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
