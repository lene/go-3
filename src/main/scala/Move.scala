package go3d

class Move(val position: Position, val color: Color):
  def this(x: Int, y: Int, z:Int, col: Color) = this(Position(x, y, z), col)
  def isPass: Boolean = position == null

  override def toString: String = position.toString+" "+color.toString

  override def equals(that: Any): Boolean =
    that match
      case that: Pass => false
      case that: Move => position == that.position && color == that.color
      case _ => false

class Pass(override val color: Color) extends Move(null, color)
