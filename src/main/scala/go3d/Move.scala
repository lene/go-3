package go3d

class Move(val position: Position, val color: Color) extends HasColor:
  def this(x: Int, y: Int, z:Int, col: Color) = this(Position(x, y, z), col)
  def x: Int = position.x
  def y: Int = position.y
  def z: Int = position.z

  override def toString: String = s"$position $color"
  override def hashCode(): Int = toString.hashCode()
  
  override def equals(that: Any): Boolean =
    that match
      case that: Move => position == that.position && color == that.color
      case _ => false
