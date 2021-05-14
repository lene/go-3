package go3d

class Delta(val dx: Int, val dy: Int, val dz: Int):
  def abs: Int = Math.abs(dx) + Math.abs(dy) + Math.abs(dz)
  override def equals(that: Any): Boolean =
    that match
      case that: Delta => dx == that.dx && dy == that.dy && dz == that.dz
      case _ => false

class Position(val x: Int, val y: Int, val z: Int):
  if x < 1 || y < 1 || z < 1 then
    throw IllegalArgumentException("coordinate < 1: "+x+", "+y+", "+z)

  override def toString: String = ""+x+" "+y+" "+z

  override def equals(that: Any): Boolean =
    that match
        case that: Position => x == that.x && y == that.y && z == that.z
        case _ => false

  def -(other: Position): Delta = Delta(x-other.x, y-other.y, z-other.z)
