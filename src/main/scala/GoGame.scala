package go3d

trait GoGame{
  def at(position: Position): Color
  def checkValid(move: Move): Unit
  def setStone(move: Move): Unit
}
