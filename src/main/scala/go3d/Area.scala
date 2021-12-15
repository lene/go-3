package go3d

case class Area(stones: Set[Move], liberties: Int):
  override def toString: String =
    def areaToString(): String =
      if stones.isEmpty then ""
      else stones.head.color.toString + stones.foldLeft("")((acc, stone) => acc + "/" + stone.position.toString)
    s"[${areaToString()}]"
