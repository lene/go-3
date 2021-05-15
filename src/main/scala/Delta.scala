package go3d

class Delta(val dx: Int, val dy: Int, val dz: Int):
  def abs: Int = Math.abs(dx) + Math.abs(dy) + Math.abs(dz)
  override def equals(that: Any): Boolean =
    that match
      case that: Delta => dx == that.dx && dy == that.dy && dz == that.dz
      case _ => false
