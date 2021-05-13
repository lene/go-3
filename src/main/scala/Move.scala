package go3d

class Move(val position: Position, val color: Color):
  def this(x: Int, y: Int, z:Int, col: Color) = this(Position(x, y, z), col)
  def isPass: Boolean = position == null
  def x: Int = position.x
  def y: Int = position.y
  def z: Int = position.x
  
  override def toString: String = position.toString+" "+color.toString

  override def equals(that: Any): Boolean =
    that match
      case that: Pass => false
      case that: Move => position == that.position && color == that.color
      case _ => false

class Pass(override val color: Color) extends Move(null, color)
