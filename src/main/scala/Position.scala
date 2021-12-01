package go3d

class Position(val x: Int, val y: Int, val z: Int):
  if x < 1 || y < 1 || z < 1 then throw OutsideBoard(x, y, z)
  def this(xi: Array[Int]) = this(xi(0), xi(1), xi(2))
  def this(x: (Int, Int, Int)) = this(x(0), x(1), x(2))

  override def toString: String = s"$x $y $z"
  override def hashCode(): Int = toString.hashCode()
  
  override def equals(that: Any): Boolean =
    that match
        case that: Position => x == that.x && y == that.y && z == that.z
        case _ => false

  def -(other: Position): Delta = Delta(x-other.x, y-other.y, z-other.z)
