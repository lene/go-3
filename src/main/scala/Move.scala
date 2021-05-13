package go3d

class Move(val position: Position, val color: Color):
  def this(x: Int, y: Int, z:Int, col: Color) = this(Position(x, y, z), col)
  def isPass: Boolean = position == null

class Pass(override val color: Color) extends Move(null, color)
