package go3d

trait GoGame {
  def at(position: Position): Color
  def checkValid(move: Move): scala.util.Try[Unit]
  def setStone(move: Move): GoGame
}
