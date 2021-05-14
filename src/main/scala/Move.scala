package go3d

class Move(val position: Position, val color: Color):
  def this(x: Int, y: Int, z:Int, col: Color) = this(Position(x, y, z), col)
  def x: Int = position.x
  def y: Int = position.y
  def z: Int = position.z

  override def toString: String = position.toString+" "+color.toString

  override def equals(that: Any): Boolean =
    that match
      case that: Move => position == that.position && color == that.color
      case _ => false

class Pass(val color: Color):
  override def toString: String = "pass "+color.toString

