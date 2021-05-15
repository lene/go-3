package go3d

trait GoGame{
  def at(position: Position): Color
  def isValid(move: Move): Boolean
  def setStone(move: Move): Unit
}
